package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.CreateAppointmentRequest;
import com.example.backend.dto.UpdateAppointmentRequest;
import com.example.backend.dto.UpdateAppointmentStatusRequest;
import com.example.backend.Entity.AppointmentEntity;
import com.example.backend.service.AppointmentService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 预约挂号接口（Appointment 管理模块）。
 * 备注：管理员可管理所有预约，医生只能查看/操作自己的预约。
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 列表查询：可选按 patientId / doctorId / date 筛选。
     * 权限：管理员看全部，医生只看自己的预约。
     */
    @GetMapping
    public Result<List<AppointmentEntity>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request
    ) {
        String token = getToken(request);
        String role = JwtUtil.getRoleFromToken(token);
        Long userId = JwtUtil.getUserIdFromToken(token);
        
        // 医生只能看自己的预约
        if ("DOCTOR".equals(role)) {
            return Result.ok(appointmentService.listByDoctorUserId(userId, Optional.ofNullable(date)));
        }
        
        return Result.ok(appointmentService.list(
                Optional.ofNullable(patientId),
                Optional.ofNullable(doctorId),
                Optional.ofNullable(date)
        ));
    }

    /**
     * 新增预约：写入 appointment 表（管理员和医生均可）。
     */
    @PostMapping
    public Result<AppointmentEntity> create(@Valid @RequestBody CreateAppointmentRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(appointmentService.create(req));
    }

    /**
     * 更新预约：编辑预约信息（仅管理员）。
     */
    @PutMapping("/{id}")
    public Result<AppointmentEntity> update(@PathVariable Long id, @Valid @RequestBody UpdateAppointmentRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(appointmentService.update(id, req));
    }

    /**
     * 修改预约状态：只改 status（管理员和医生均可操作自己的预约）。
     */
    @PutMapping("/{id}/status")
    public Result<AppointmentEntity> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusRequest req,
            HttpServletRequest request
    ) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(appointmentService.updateStatus(id, req.getStatus()));
    }

    /**
     * 删除预约：根据 ID 删除指定预约记录（仅管理员）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        appointmentService.delete(id);
        return Result.ok();
    }

    /**
     * 从请求中获取 Token。
     */
    private String getToken(HttpServletRequest request) {
        // 1. 从 HTTP 请求头中获取 Authorization 字段的值
        String token = request.getHeader("Authorization");

        // 2. 检查 token 是否存在，且以 "Bearer " 开头（JWT 标准格式）
        if (token != null && token.startsWith("Bearer ")) {
            // 3. 去掉 "Bearer " 前缀（7个字符），返回纯 Token 字符串
            return token.substring(7);
        }

        // 4. 如果没有 Token 或格式不对，返回 null
        return null;
    }
}

