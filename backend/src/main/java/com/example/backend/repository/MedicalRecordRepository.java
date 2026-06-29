package com.example.backend.repository;

import com.example.backend.Entity.MedicalRecordEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 病历表数据访问层（对应 {@code medical_record}）。
 */
public interface MedicalRecordRepository extends JpaRepository<MedicalRecordEntity, Long> {
    List<MedicalRecordEntity> findByPatientId(Long patientId, Sort sort);

    List<MedicalRecordEntity> findByDoctorId(Long doctorId, Sort sort);

    /**
     * 根据预约 ID 删除病历记录（用于级联删除预约）。
     */
    void deleteByAppointmentId(Long appointmentId);
}
