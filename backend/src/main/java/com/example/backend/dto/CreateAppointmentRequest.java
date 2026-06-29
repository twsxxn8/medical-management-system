package com.example.backend.dto;

import java.time.LocalDate;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 新增预约请求体（对应 {@code appointment}）。
 * 备注：
 * - 必填外键：patientId / doctorId / departmentId。
 * - {@code appointmentDate} + {@code timeSlot} 用于标识预约时间段（示例：上午/下午/晚上）。
 * - {@code status} 由后端默认置为 0（未确认），避免前端随意写入状态。
 */
public class CreateAppointmentRequest {
    @NotNull
    private Long patientId;

    @NotNull
    private Long doctorId;

    @NotNull
    private Long departmentId;

    @NotNull
    private LocalDate appointmentDate;

    @NotBlank
    @Size(max = 50)
    private String timeSlot;

    @Size(max = 500)
    private String reason;

    @Size(max = 500)
    private String remark;

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

