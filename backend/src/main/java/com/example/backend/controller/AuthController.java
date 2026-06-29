package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.LoginRequest;
import com.example.backend.Entity.SystemLogEntity;
import com.example.backend.Entity.UserEntity;
import com.example.backend.repository.SystemLogRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.RedisService;
import com.example.backend.util.JwtUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（Auth 管理模块）。
 * 备注：提供用户登录/登出功能，登出时将 Token 加入 Redis 黑名单。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final SystemLogRepository systemLogRepository;
    private final RedisService redisService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository, SystemLogRepository systemLogRepository, RedisService redisService) {
        this.userRepository = userRepository;
        this.systemLogRepository = systemLogRepository;
        this.redisService = redisService;
    }

    /**
     * 用户登录：校验账号密码并签发 Token，记录登录日志。
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        // 1. 根据用户名查询用户
        Optional<UserEntity> userOpt = userRepository.findByUsername(req.getUsername());
        if (userOpt.isEmpty()) {
            return Result.error("用户名或密码错误");
        }

        UserEntity user = userOpt.get();

        // 2. 校验密码（BCrypt）
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        // 3. 生成 Token
        String role = user.getRole() != null ? user.getRole() : "DOCTOR";
        String token = JwtUtil.generateToken(user.getId(), role);

        // 4. 记录登录日志
        SystemLogEntity log = new SystemLogEntity();
        log.setUserId(user.getId());
        log.setOperation("用户登录");
        log.setMethod("POST /api/auth/login");
        log.setParams("{\"username\":\"" + req.getUsername() + "\"}");
        log.setIpAddress(getClientIp(request));
        log.setCreateTime(LocalDateTime.now());
        systemLogRepository.save(log);

        // 5. 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", role);
        
        return Result.ok(data);
    }

    /**
     * 获取客户端 IP 地址。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 用户登出：将当前 Token 加入 Redis 黑名单，TTL 设为 Token 剩余有效期。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            long remainingSeconds = JwtUtil.getRemainingSeconds(token);
            if (remainingSeconds > 0) {
                // 将 Token 加入黑名单，TTL = 剩余有效期
                String blacklistKey = "token:blacklist:" + token;
                redisService.set(blacklistKey, "1", remainingSeconds, TimeUnit.SECONDS);
            }
        }
        return Result.ok();
    }
}
