// 1-24行：导入依赖
package com.example.backend.controller;

import com.example.backend.common.Result;          // 统一响应格式
import com.example.backend.dto.CreateMedicalRecordRequest;  // 新增病历请求体
import com.example.backend.dto.UpdateMedicalRecordRequest;  // 更新病历请求体
import com.example.backend.Entity.MedicalRecordEntity;      // 病历实体类
import com.example.backend.service.MedicalRecordService;    // 病历业务层
import java.time.LocalDateTime;                  // 时间类型（虽然这个文件没用到）
import java.util.Collections;
import java.util.List;                           // 列表集合
import java.util.Optional;                       // 可选类型（用于处理 null）
import javax.servlet.http.HttpServletRequest;    // HTTP 请求对象（用于获取 Token）
import javax.validation.Valid;                   // 参数校验注解
import org.springframework.format.annotation.DateTimeFormat;  // 日期格式化注解（没用到）
import org.springframework.web.bind.annotation.*; // Spring MVC 路由注解
import com.example.backend.util.JwtUtil;         // JWT 工具类（解析 Token）

// 26-29行：类注释
/**
 * 病历接口（MedicalRecord 管理模块）。
 * 备注：管理员可查看所有病历，医生只能查看/操作自己书写的病历。
 */

// 30-37行：类定义和构造函数
@RestController                              // 声明这是一个 REST 控制器
@RequestMapping("/api/medical-records")      // 所有接口路径前缀
public class MedicalRecordController {
    private final MedicalRecordService medicalRecordService;  // 注入业务层

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;     // 构造函数注入
    }

// 39-65行：列表查询接口
    /**
     * 列表查询：可选按 patientId / doctorId 筛选。
     * 权限：管理员看全部，医生只看自己的病历。
     */
    @GetMapping                              // GET 请求映射
    public Result<List<MedicalRecordEntity>> list(
            @RequestParam(required = false) Long patientId,   // 可选参数：患者ID
            @RequestParam(required = false) Long doctorId,    // 可选参数：医生ID
            HttpServletRequest request                        // HTTP 请求对象
    ) {
        String token = getToken(request);                     // 提取 Token
        String role = JwtUtil.getRoleFromToken(token);        // 解析角色（ADMIN/DOCTOR）
        Long userId = JwtUtil.getUserIdFromToken(token);      // 解析用户ID

        // 医生只能看自己的病历：必须用 doctor 表主键筛选（medical_record.doctor_id -> doctor.id），不能用 user.id
        if ("DOCTOR".equals(role)) {
            return medicalRecordService.resolveDoctorTableIdByUserId(userId)
                    .map(did -> Result.ok(medicalRecordService.list(Optional.empty(), Optional.of(did))))
                    .orElseGet(() -> Result.ok(Collections.emptyList()));
        }

        // 管理员可以按条件查询全部病历
        return Result.ok(medicalRecordService.list(
                Optional.ofNullable(patientId),               // 可能为 null
                Optional.ofNullable(doctorId)
        ));
    }

// 67-77行：新增病历接口
    /**
     * 新增病历：写入 medical_record 表（管理员和医生均可）。
     */
    @PostMapping                             // POST 请求映射
    public Result<MedicalRecordEntity> create(
            @Valid @RequestBody CreateMedicalRecordRequest req,  // @Valid 校验参数，@RequestBody 解析 JSON
            HttpServletRequest request
    ) {
        String token = getToken(request);
        String role = JwtUtil.getRoleFromToken(token);
        Long userId = JwtUtil.getUserIdFromToken(token);
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return Result.error("权限不足");
        }
        // 医生只能为本人 doctor 身份建档，防止越权代写他人病历
        if ("DOCTOR".equals(role)) {
            Optional<Long> ownDoctorId = medicalRecordService.resolveDoctorTableIdByUserId(userId);
            if (ownDoctorId.isEmpty() || !ownDoctorId.get().equals(req.getDoctorId())) {
                return Result.error("权限不足：只能选择本人作为书写医生");
            }
        }
        return Result.ok(medicalRecordService.create(req));
    }

// 79-89行：更新病历接口
    /**
     * 更新病历：管理员可改任意；医生仅能改本人书写的病历。
     */
    @PutMapping("/{id}")                     // PUT 请求，{id} 是路径变量
    public Result<MedicalRecordEntity> update(
            @PathVariable Long id,           // 从 URL 获取病历ID
            @Valid @RequestBody UpdateMedicalRecordRequest req,
            HttpServletRequest request
    ) {
        String token = getToken(request);
        String role = JwtUtil.getRoleFromToken(token);
        Long userId = JwtUtil.getUserIdFromToken(token);
        if ("ADMIN".equals(role)) {
            return Result.ok(medicalRecordService.update(id, req));
        }
        if ("DOCTOR".equals(role) && medicalRecordService.isMedicalRecordOwnedByDoctorUser(id, userId)) {
            return Result.ok(medicalRecordService.update(id, req));
        }
        return Result.error("权限不足");
    }

// 91-102行：删除病历接口
    /**
     * 删除病历：管理员可删任意；医生仅能删除本人书写的病历。
     */
    @DeleteMapping("/{id}")                  // DELETE 请求
    public Result<Void> delete(
            @PathVariable Long id,           // 病历ID
            HttpServletRequest request
    ) {
        String token = getToken(request);
        String role = JwtUtil.getRoleFromToken(token);
        Long userId = JwtUtil.getUserIdFromToken(token);
        if ("ADMIN".equals(role)) {
            medicalRecordService.delete(id);
            return Result.ok();
        }
        if ("DOCTOR".equals(role) && medicalRecordService.isMedicalRecordOwnedByDoctorUser(id, userId)) {
            medicalRecordService.delete(id);
            return Result.ok();
        }
        return Result.error("权限不足");
    }

// 104-113行：工具方法
    /**
     * 从请求中获取 Token。
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");  // 从请求头获取
        if (token != null && token.startsWith("Bearer ")) { // 检查格式
            return token.substring(7);                      // 去掉 "Bearer " 前缀
        }
        return null;                                        // 格式不对返回 null
    }
}
