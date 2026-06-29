package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.CreateUserRequest;
import com.example.backend.Entity.UserEntity;
import com.example.backend.service.UserService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 用户管理接口（User 管理模块）。
 * 备注：仅供管理员使用，用于维护系统登录账号。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 列表查询：获取所有用户信息（仅管理员）。
     */
    @GetMapping
    public Result<List<UserEntity>> list(HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(userService.listAll());
    }

    /**
     * 新增用户：自动对密码进行 BCrypt 加密（仅管理员）。
     * 备注：如果角色为 DOCTOR，自动创建医生记录。
     */
    @PostMapping
    public Result<UserEntity> create(@RequestBody CreateUserRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        try {
            return Result.ok(userService.create(req));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除用户：根据 ID 物理删除（仅管理员）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        userService.delete(id);
        return Result.ok();
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
