package com.example.backend.dto;

import java.time.LocalDate;
import javax.validation.constraints.Size;

/**
 * 更新患者档案请求体（对应 {@code patient}）。
 * 备注：不允许修改 {@code userId} 与 {@code idCard}（两者在 SQL 中都带 UNIQUE，等同“绑定关系/唯一标识”）。
 */
public class UpdatePatientRequest {
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

