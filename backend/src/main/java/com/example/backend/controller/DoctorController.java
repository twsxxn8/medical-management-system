package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.DoctorResponse;
import com.example.backend.dto.UpdateDoctorRequest;
import com.example.backend.dto.UpdateDoctorStatusRequest;
import com.example.backend.Entity.DoctorEntity;
import com.example.backend.service.DoctorService;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.example.backend.util.JwtUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生管理接口（Doctor 管理模块）。
 * 备注：管理员可查看所有医生，医生只能查看自己的信息。
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /**
     * 列表查询：可选按科室筛选，根据角色自动过滤数据。
     * - ADMIN：返回所有医生
     * - DOCTOR：只返回当前登录医生的记录
     */
    @GetMapping
    public Result<List<DoctorResponse>> list(
            @RequestParam(required = false) Long departmentId,
            HttpServletRequest request
    ) {
        String token = getToken(request);
        Long currentUserId = JwtUtil.getUserIdFromToken(token);
        String role = JwtUtil.getRoleFromToken(token);
        
        return Result.ok(doctorService.list(
                Optional.ofNullable(departmentId),
                Optional.ofNullable(currentUserId),
                role
        ));
    }

    /**
     * 更新医生信息。
     */
    @PutMapping("/{id}")
    public Result<DoctorEntity> update(@PathVariable Long id, @RequestBody UpdateDoctorRequest req, HttpServletRequest request) {
        String token = getToken(request);
        String role = JwtUtil.getRoleFromToken(token);
        Long userId = JwtUtil.getUserIdFromToken(token);

        if ("ADMIN".equals(role)) {
            try {
                return Result.ok(doctorService.update(id, req));
            } catch (RuntimeException e) {
                return Result.error(e.getMessage());
            }
        }
        // 医生：仅允许维护本人 doctor 档案（与 doctor.user_id 对应）
        if ("DOCTOR".equals(role) && doctorService.isProfileOwnedByUser(id, userId)) {
            try {
                return Result.ok(doctorService.update(id, req));
            } catch (RuntimeException e) {
                return Result.error(e.getMessage());
            }
        }
        return Result.error("权限不足");
    }

    /**
     * 切换医生应诊/停诊状态。
     */
    @PutMapping("/{id}/status")
    public Result<DoctorEntity> updateStatus(@PathVariable Long id, @RequestBody UpdateDoctorStatusRequest req, HttpServletRequest request) {
        String token = getToken(request);
        String role = JwtUtil.getRoleFromToken(token);
        Long userId = JwtUtil.getUserIdFromToken(token);

        if ("ADMIN".equals(role)) {
            try {
                return Result.ok(doctorService.updateStatus(id, req.getStatus()));
            } catch (RuntimeException e) {
                return Result.error(e.getMessage());
            }
        }
        if ("DOCTOR".equals(role) && doctorService.isProfileOwnedByUser(id, userId)) {
            try {
                return Result.ok(doctorService.updateStatus(id, req.getStatus()));
            } catch (RuntimeException e) {
                return Result.error(e.getMessage());
            }
        }
        return Result.error("权限不足");
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

