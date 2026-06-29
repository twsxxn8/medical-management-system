package com.example.backend.repository;

import com.example.backend.Entity.AppointmentEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 预约挂号数据访问层（对应 {@code appointment}）。
 * 备注：为满足常见列表筛选，提供按患者/医生/日期的基础查询方法。
 */
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, Long> {
    @Query("SELECT a FROM AppointmentEntity a WHERE a.patientId = :patientId")
    List<AppointmentEntity> findByPatientId(@Param("patientId") Long patientId, Sort sort);

    List<AppointmentEntity> findByDoctorId(Long doctorId, Sort sort);

    List<AppointmentEntity> findByAppointmentDate(LocalDate appointmentDate, Sort sort);
    
    List<AppointmentEntity> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate appointmentDate, Sort sort);

    @Query("SELECT a FROM AppointmentEntity a WHERE a.doctorId = :doctorId AND a.appointmentDate = :appointmentDate AND a.timeSlot = :timeSlot")
    List<AppointmentEntity> findByDoctorIdAndAppointmentDateAndTimeSlot(
            Long doctorId,
            LocalDate appointmentDate,
            String timeSlot
    );

    /**
     * 统计指定日期的预约数量（用于首页统计）。
     */
    long countByAppointmentDate(LocalDate date);
}

