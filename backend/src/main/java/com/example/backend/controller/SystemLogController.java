package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.Entity.SystemLogEntity;
import com.example.backend.service.SystemLogService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 系统操作日志接口（SystemLog 管理模块）。
 * 备注：仅管理员可访问，日志模块通常仅支持查询。
 */
@RestController
@RequestMapping("/api/system-logs")
public class SystemLogController {
    private final SystemLogService systemLogService;

    public SystemLogController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    /**
     * 列表查询：返回全部操作记录（仅管理员）。
     */
    @GetMapping
    public Result<List<SystemLogEntity>> list(HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(systemLogService.listAll());
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

