package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.CreateMedicineStockLogRequest;
import com.example.backend.Entity.MedicineStockLogEntity;
import com.example.backend.service.MedicineStockLogService;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.util.JwtUtil;

/**
 * 药品库存日志接口（MedicineStockLog 管理模块）。
 * 备注：仅管理员可访问，用于追踪药品出入库记录。
 */
@RestController
@RequestMapping("/api/medicine-stock-logs")
public class MedicineStockLogController {
    private final MedicineStockLogService medicineStockLogService;

    public MedicineStockLogController(MedicineStockLogService medicineStockLogService) {
        this.medicineStockLogService = medicineStockLogService;
    }

    /**
     * 列表查询：可选按 medicineId 筛选（仅管理员）。
     */
    @GetMapping
    public Result<List<MedicineStockLogEntity>> list(
            @RequestParam(required = false) Long medicineId,
            HttpServletRequest request
    ) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(medicineStockLogService.list(Optional.ofNullable(medicineId)));
    }

    /**
     * 新增库存变动：写入日志并同步更新库存（仅管理员）。
     */
    @PostMapping
    public Result<MedicineStockLogEntity> create(@Valid @RequestBody CreateMedicineStockLogRequest req, HttpServletRequest request) {
        String role = JwtUtil.getRoleFromToken(getToken(request));
        if (!"ADMIN".equals(role)) {
            return Result.error("权限不足");
        }
        return Result.ok(medicineStockLogService.create(req));
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
