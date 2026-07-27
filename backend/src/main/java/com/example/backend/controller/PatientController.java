package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.CreatePatientRequest;
import com.example.backend.dto.UpdatePatientRequest;
import com.example.backend.Entity.PatientEntity;
import com.example.backend.service.PatientService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 患者档案接口（Patient 管理模块）。
 * 备注：该模块完成后，即可进入预约（appointment）与病历（medical_record）模块的联表业务开发。
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * 列表查询：返回全部患者档案（管理员和医生均可访问）。
     */
    @GetMapping
    public Result<List<PatientEntity>> list(HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(patientService.listAll());
    }

    /**
     * 新增患者档案：写入 patient 表（仅管理员）。
     */
    @PostMapping
    public Result<PatientEntity> create(@Valid @RequestBody CreatePatientRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(patientService.create(req));
    }

    /**
     * 更新患者档案：只更新可变字段（仅管理员）。
     */
    @PutMapping("/{id}")
    public Result<PatientEntity> update(@PathVariable Long id, @Valid @RequestBody UpdatePatientRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(patientService.update(id, req));
    }

    /**
     * 删除患者：根据 ID 删除指定患者记录（仅管理员）。
     * 备注：若该患者有关联预约或病历，删除会因外键约束失败，需先处理关联数据。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        patientService.delete(id);
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

