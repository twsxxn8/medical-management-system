package com.example.backend.filter;

import com.example.backend.service.RateLimitLuaService;
import com.example.backend.service.RateLimitLuaService.RateLimitResult;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 接口限流过滤器：基于 Redis Lua 令牌桶算法实现 IP 维度的兜底限流。
 *
 * <p><strong>技术升级要点（vs v1 计数器模式）：</strong>
 * <ul>
 *   <li>令牌桶算法：支持突发流量（burst），桶容量 capacity = 20</li>
 *   <li>Redis Lua 脚本：HMGET + 令牌计算 + HMSET 原子执行，无竞态条件</li>
 *   <li>AI 接口独立限流：/api/ai/** 路径使用更严格配置（capacity=5, rate=2/s）</li>
 *   <li>标准响应头：X-RateLimit-Remaining、Retry-After</li>
 *   <li>精细控制由 {@code @RateLimit} 注解 + AOP 切面提供</li>
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitLuaService rateLimitLuaService;

    public RateLimitFilter(RateLimitLuaService rateLimitLuaService) {
        this.rateLimitLuaService = rateLimitLuaService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") || uri.equals("/api/health") || uri.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        boolean isAiApi = uri.startsWith("/api/ai/");

        RateLimitResult result;
        try {
            result = rateLimitLuaService.checkByProfile("ip", clientIp, isAiApi);
        } catch (Exception e) {
            log.warn("Redis 限流检查失败，放行请求: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (!result.isAllowed()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("X-RateLimit-Retry-After", String.valueOf(result.getRetryAfterMs()));
            response.getWriter().write(
                    "{\"success\":false,\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}"
            );
            if (log.isDebugEnabled()) {
                log.debug("限流拒绝: ip={}, uri={}, retry_after={}ms",
                        clientIp, uri, result.getRetryAfterMs());
            }
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
