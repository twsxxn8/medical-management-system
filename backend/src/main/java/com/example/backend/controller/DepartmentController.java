package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.CreateDepartmentRequest;
import com.example.backend.Entity.DepartmentEntity;
import com.example.backend.service.DepartmentService;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 科室接口（最小可用示例）。
 * 备注：仅管理员可访问。
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public Result<Page<DepartmentEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        // 列表只读：医生/患者等下拉框需要科室数据；写操作仍仅管理员
        if (!canReadDepartmentList(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(departmentService.listAll(page, size));
    }

    /** 允许读取科室分页列表的角色（用于各业务页下拉，不等同于管理科室的权限） */
    private boolean canReadDepartmentList(String role) {
        if (role == null) {
            return false;
        }
        return "ADMIN".equals(role)
                || "DOCTOR".equals(role)
                || "PATIENT".equals(role)
                || "RECEPTIONIST".equals(role);
    }

    @PostMapping
    public Result<DepartmentEntity> create(@Valid @RequestBody CreateDepartmentRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        DepartmentEntity saved = departmentService.create(
                req.getName(),
                req.getParentId(),
                req.getDescription(),
                req.getLocation()
        );
        return Result.ok(saved);
    }

    /**
     * 删除科室：根据 ID 删除指定科室记录（仅管理员）。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        departmentService.delete(id);
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

