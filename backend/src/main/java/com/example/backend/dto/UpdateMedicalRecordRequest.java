package com.example.backend.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.Size;

/**
 * 更新病历请求体（对应 {@code medical_record}）。
 * 备注：不允许修改外键关联（patientId/doctorId/appointmentId）。
 */
public class UpdateMedicalRecordRequest {
    @Size(max = 2000)
    private String diagnosis;

    @Size(max = 2000)
    private String prescription;

    @Size(max = 2000)
    private String prescriptionMedicines;

    @Size(max = 2000)
    private String symptoms;

    private LocalDateTime visitDate;

    // Getter 和 Setter
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
