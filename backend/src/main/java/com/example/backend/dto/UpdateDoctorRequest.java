package com.example.backend.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 更新医生请求体（对应 {@code doctor}）。
 * 备注：不允许修改 {@code userId}（与账号绑定且 SQL UNIQUE），如需换绑应单独设计流程。
 */
public class UpdateDoctorRequest {
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

    private Integer status;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

