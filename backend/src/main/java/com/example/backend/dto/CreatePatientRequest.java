package com.example.backend.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增患者档案请求体（对应 {@code patient}）。
 * 备注：
 * - {@code userId} 关联 {@code [user].id}（一一对应）。
 * - 字段长度约束与建表脚本保持一致，避免写入时被 SQL Server 截断。
 */
public class CreatePatientRequest {
    private Long userId;

    @NotBlank
    @Size(max = 18)
    private String idCard;

    @Size(max = 10)
    private String gender;

    private LocalDate birthDate;

    @Size(max = 200)
    private String address;

    @Size(max = 50)
    private String emergencyContact;

    @Size(max = 20)
    private String emergencyPhone;

    @Size(max = 2000)
    private String medicalHistory;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public String getEmergencyPhone() {
        return emergencyPhone;
    }

    public void setEmergencyPhone(String emergencyPhone) {
        this.emergencyPhone = emergencyPhone;
    }

    public String getMedicalHistory() {
        return medicalHistory;
    }

    public void setMedicalHistory(String medicalHistory) {
        this.medicalHistory = medicalHistory;
    }
}

