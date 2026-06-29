package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.CreateMedicineRequest;
import com.example.backend.dto.UpdateMedicineRequest;
import com.example.backend.Entity.MedicineEntity;
import com.example.backend.service.MedicineService;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 药品接口（Medicine 管理模块）。
 * 备注：仅管理员可访问，用于维护药品信息。
 */
@RestController
@RequestMapping("/api/medicines")
public class MedicineController {
    private final MedicineService medicineService;

    public MedicineController(MedicineService medicineService) {
        this.medicineService = medicineService;
    }

    /**
     * 列表查询：管理员与医生均可查看（开处方/病历场景需要药品字典）；写操作仍仅管理员。
     */
    @GetMapping
    public Result<List<MedicineEntity>> list(HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(medicineService.listAll());
    }

    /**
     * 新增药品：写入 medicine 表（仅管理员）。
     */
    @PostMapping
    public Result<MedicineEntity> create(@Valid @RequestBody CreateMedicineRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(medicineService.create(req));
    }

    /**
     * 更新药品：编辑药品信息（仅管理员）。
     */
    @PutMapping("/{id}")
    public Result<MedicineEntity> update(@PathVariable Long id, @Valid @RequestBody UpdateMedicineRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(medicineService.update(id, req));
    }

    /**
     * 上下架：只改 status（仅管理员）。
     */
    @PutMapping("/{id}/status")
    public Result<MedicineEntity> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body,
            HttpServletRequest request
    ) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(medicineService.updateStatus(id, body.get("status")));
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
