package com.example.backend.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增病历请求体（对应 {@code medical_record}）。
 * 备注：关联患者/医生/预约，诊断内容为必填。
 */
public class CreateMedicalRecordRequest {
    @NotNull
    private Long patientId;

    @NotNull
    private Long doctorId;

    private Long appointmentId;

    @NotNull
    @Size(max = 2000)
    private String diagnosis;

    @Size(max = 2000)
    private String prescription;

    @Size(max = 2000)
    private String prescriptionMedicines;

    @Size(max = 2000)
    private String symptoms;

    @NotNull
    private LocalDateTime visitDate;

    // Getter 和 Setter
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getPrescription() { return prescription; }
    public void setPrescription(String prescription) { this.prescription = prescription; }
    public String getPrescriptionMedicines() { return prescriptionMedicines; }
    public void setPrescriptionMedicines(String prescriptionMedicines) { this.prescriptionMedicines = prescriptionMedicines; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public LocalDateTime getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDateTime visitDate) { this.visitDate = visitDate; }
}
