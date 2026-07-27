package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.service.StatisticsService;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 首页统计接口。
 * 备注：返回系统核心业务数据的实时统计，管理员和医生均可访问。
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 获取首页统计数据（管理员和医生均可）。
     * 返回：患者总数、医生总数、今日预约数、科室总数等。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(statisticsService.getOverview());
    }

    /**
     * 从请求中获取 Token。
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return null;
    }
}
