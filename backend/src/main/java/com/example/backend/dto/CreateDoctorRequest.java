package com.example.backend.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 新增医生请求体（对应 {@code doctor}）。
 * 备注：此处以 {@code userId} 关联登录用户（SQL 中 doctor.user_id UNIQUE），不在这里做账号注册逻辑。
 */
public class CreateDoctorRequest {
    @NotNull
    private Long userId;

    @NotNull
    private Long departmentId;

    @Size(max = 50)
    private String title;

    @Size(max = 100)
    private String specialty;

    @DecimalMin("0.00")
    private BigDecimal consultationFee;

    @Size(max = 2000)
    private String introduction;

    @Size(max = 2000)
    private String schedule;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
}

