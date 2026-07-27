package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.service.AiMetricsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康与监控接口。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthEndpoint healthEndpoint;
    private final AiMetricsService aiMetricsService;

    public HealthController(HealthEndpoint healthEndpoint,
                            AiMetricsService aiMetricsService) {
        this.healthEndpoint = healthEndpoint;
        this.aiMetricsService = aiMetricsService;
    }

    /**
     * 增强健康检查：返回 DB/Redis 状态。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", healthEndpoint.health().getStatus().getCode());
        return Result.ok(info);
    }

    /**
     * 断路器状态一览（需认证）。
     */
    @GetMapping("/health/circuit-breakers")
    public Result<List<Map<String, Object>>> circuitBreakers() {
        return Result.ok(aiMetricsService.getCircuitBreakerStates());
    }

    /**
     * AI 服务概览 — provider 列表 + 断路器状态（需认证）。
     */
    @GetMapping("/ai/stats")
    public Result<Map<String, Object>> aiStats() {
        return Result.ok(aiMetricsService.getAiOverview());
    }
}
