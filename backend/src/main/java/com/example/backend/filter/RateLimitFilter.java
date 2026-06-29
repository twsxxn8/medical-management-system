package com.example.backend.filter;

import com.example.backend.service.RedisService;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 接口限流过滤器：基于 Redis 实现 IP 维度的请求频率限制。
 * 备注：每个 IP 每秒最多允许 20 次请求，超限返回 429 Too Many Requests。
 */
// @Component  会导致自动注册为 Servlet Filter
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_SECOND = 20;
    private static final String RATE_LIMIT_PREFIX = "rate:limit:";

    private final RedisService redisService;

    public RateLimitFilter(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 仅对 /api/ 接口限流，放行静态资源和健康检查
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") || uri.equals("/api/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String rateLimitKey = RATE_LIMIT_PREFIX + clientIp;

        // 自增计数（首次访问时 Redis 自动创建 key）
        Long currentCount = redisService.increment(rateLimitKey);

        // 第一次访问时设置 1 秒过期时间
        if (currentCount != null && currentCount == 1L) {
            redisService.expire(rateLimitKey, 1, TimeUnit.SECONDS);
        }

        // 超过限制返回 429
        if (currentCount != null && currentCount > MAX_REQUESTS_PER_SECOND) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 获取客户端真实 IP（兼容反向代理场景）。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
