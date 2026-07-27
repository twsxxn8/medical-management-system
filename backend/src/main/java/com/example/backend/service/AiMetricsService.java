package com.example.backend.service;

import com.example.backend.config.AiProviderConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 调用统计服务：汇总断路器状态和运行时指标。
 */
@Service
public class AiMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AiMetricsService.class);

    private final AiCircuitBreakerService breakerService;
    private final AiProviderRouter providerRouter;

    public AiMetricsService(AiCircuitBreakerService breakerService,
                            AiProviderRouter providerRouter) {
        this.breakerService = breakerService;
        this.providerRouter = providerRouter;
    }

    /**
     * 获取所有断路器的状态。
     */
    public List<Map<String, Object>> getCircuitBreakerStates() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CircuitBreaker breaker : breakerService.getRegistry().getAllCircuitBreakers()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", breaker.getName());
            info.put("state", breaker.getState().name());
            CircuitBreaker.Metrics m = breaker.getMetrics();
            info.put("failureRate", String.format("%.1f%%", m.getFailureRate()));
            info.put("slowCallRate", String.format("%.1f%%", m.getSlowCallRate()));
            info.put("numberOfSuccessfulCalls", m.getNumberOfSuccessfulCalls());
            info.put("numberOfFailedCalls", m.getNumberOfFailedCalls());
            info.put("numberOfSlowCalls", m.getNumberOfSlowCalls());
            info.put("numberOfNotPermittedCalls", m.getNumberOfNotPermittedCalls());
            result.add(info);
        }
        return result;
    }

    /**
     * 返回 AI 调用概览。
     */
    public Map<String, Object> getAiOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // Provider 列表
        List<Map<String, String>> providers = new ArrayList<>();
        for (AiProviderConfig p : providerRouter.getChatProviders()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("name", p.getName());
            info.put("model", p.getModel());
            info.put("priority", String.valueOf(p.getPriority()));
            providers.add(info);
        }
        overview.put("providers", providers);

        // 断路器状态
        List<Map<String, Object>> breakers = getCircuitBreakerStates();
        overview.put("circuitBreakers", breakers);

        // 整体状态
        boolean anyOpen = breakers.stream()
                .anyMatch(b -> "OPEN".equals(b.get("state")));
        overview.put("overallStatus", anyOpen ? "DEGRADED" : "HEALTHY");

        return overview;
    }
}
