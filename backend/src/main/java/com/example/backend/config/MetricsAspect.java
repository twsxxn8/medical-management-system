package com.example.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求指标记录切面。
 *
 * <p>拦截所有 @RestController 方法，记录：
 * <ul>
 *   <li>调用次数和响应时间（Micrometer Timer）</li>
 *   <li>成功/失败计数（Micrometer Counter）</li>
 * </ul>
 */
@Aspect
@Component
public class MetricsAspect {

    private static final Logger log = LoggerFactory.getLogger(MetricsAspect.class);

    private final MeterRegistry meterRegistry;

    public MetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 环绕拦截所有 com.example.backend.controller 包下的 public 方法。
     */
    @Around("execution(public * com.example.backend.controller..*.*(..))")
    public Object recordMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        String uri = getRequestUri();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String metricName = "http.requests";

        Timer.Sample sample = Timer.start(meterRegistry);
        long startMs = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            // 成功计数
            Counter.builder(metricName)
                    .tag("uri", uri)
                    .tag("controller", className)
                    .tag("method", methodName)
                    .tag("outcome", "SUCCESS")
                    .register(meterRegistry)
                    .increment();
            return result;
        } catch (Throwable e) {
            // 失败计数
            Counter.builder(metricName)
                    .tag("uri", uri)
                    .tag("controller", className)
                    .tag("method", methodName)
                    .tag("outcome", "FAILURE")
                    .tag("error", e.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
            log.error("Controller 方法执行异常 [{}#{}]: {}",
                    className, methodName, e.getMessage(), e);
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - startMs;
            sample.stop(Timer.builder("http.request.duration")
                    .tag("uri", uri)
                    .tag("controller", className)
                    .tag("method", methodName)
                    .register(meterRegistry));
            log.debug("[{}] {}#{} → {}ms", uri, className, methodName, elapsed);
        }
    }

    private String getRequestUri() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            return request.getRequestURI();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
