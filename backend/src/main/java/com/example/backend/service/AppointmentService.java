package com.example.backend.service;

import com.example.backend.dto.CreateAppointmentRequest;
import com.example.backend.dto.UpdateAppointmentRequest;
import com.example.backend.Entity.AppointmentEntity;
import com.example.backend.repository.AppointmentRepository;
import com.example.backend.repository.DepartmentRepository;
import com.example.backend.repository.DoctorRepository;
import com.example.backend.repository.PatientRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约挂号业务（appointment 模块服务层）。
 * 备注：当前先实现最小可用 CRUD（列表/新增/更新/改状态），后续可在此加入：
 * - 同一医生同一时间段的冲突校验
 * - 状态流转规则（例如：未确认 -> 已确认 -> 已完成）
 */
@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final com.example.backend.repository.MedicalRecordRepository medicalRecordRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            com.example.backend.repository.MedicalRecordRepository medicalRecordRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    /**
     * 列表：支持按 patientId / doctorId / date 三选一筛选（优先级：patientId > doctorId > date）。
     * 备注：先做简单版，满足管理端页面；如需多条件组合筛选，可后续用 Specification/Querydsl 扩展。
     */
    public List<AppointmentEntity> list(Optional<Long> patientId, Optional<Long> doctorId, Optional<LocalDate> date) {
        Sort sort = Sort.by(Sort.Direction.DESC, "appointmentDate").and(Sort.by(Sort.Direction.DESC, "id"));
        if (patientId.isPresent()) {
            return appointmentRepository.findByPatientId(patientId.get(), sort);
        }
        if (doctorId.isPresent()) {
            return appointmentRepository.findByDoctorId(doctorId.get(), sort);
        }
        if (date.isPresent()) {
            return appointmentRepository.findByAppointmentDate(date.get(), sort);
        }
        return appointmentRepository.findAll(sort);
    }

    /**
     * 根据医生 userId 查询预约列表（用于医生角色数据隔离）。
     * 备注：先将 userId 转换为 doctorId，再查询预约记录。
     */
    public List<AppointmentEntity> listByDoctorUserId(Long userId, Optional<LocalDate> date) {
        // 先通过 userId 查找 doctor 表获取 doctorId
        List<com.example.backend.Entity.DoctorEntity> doctors = doctorRepository.findByUserId(userId);
        if (doctors.isEmpty()) {
            return List.of();
        }
        
        Long doctorId = doctors.get(0).getId();
        Sort sort = Sort.by(Sort.Direction.DESC, "appointmentDate").and(Sort.by(Sort.Direction.DESC, "id"));
        
        if (date.isPresent()) {
            return appointmentRepository.findByDoctorIdAndAppointmentDate(doctorId, date.get(), sort);
        }
        return appointmentRepository.findByDoctorId(doctorId, sort);
    }

    @Transactional
    public AppointmentEntity create(CreateAppointmentRequest req) {
        // 外键存在性校验：避免直接抛 SQL 外键异常，给前端更友好的提示
        if (!patientRepository.existsById(req.getPatientId())) {
            throw new RuntimeException("患者不存在");
        }
        if (!doctorRepository.existsById(req.getDoctorId())) {
            throw new RuntimeException("医生不存在");
        }
        if (!departmentRepository.existsById(req.getDepartmentId())) {
            throw new RuntimeException("科室不存在");
        }

        // 预约冲突检测 1：同一医生在同一日期和时段不能有多个未取消的预约
        boolean hasConflict = appointmentRepository.findByDoctorIdAndAppointmentDateAndTimeSlot(
                req.getDoctorId(), 
                req.getAppointmentDate(), 
                req.getTimeSlot()
        ).stream().anyMatch(a -> a.getStatus() != 3);
        
        if (hasConflict) {
            throw new RuntimeException("该医生在此时段已有预约，请选择其他时间");
        }

        // 预约冲突检测 2：同一患者不能在同一医生同一时段重复预约
        boolean patientConflict = appointmentRepository.findByDoctorIdAndAppointmentDateAndTimeSlot(
                req.getDoctorId(),
                req.getAppointmentDate(),
                req.getTimeSlot()
        ).stream().anyMatch(a -> a.getPatientId().equals(req.getPatientId()) && a.getStatus() != 3);
        
        if (patientConflict) {
            throw new RuntimeException("您在该医生此时段已有预约，请勿重复预约");
        }

        AppointmentEntity entity = new AppointmentEntity();
        entity.setPatientId(req.getPatientId());
        entity.setDoctorId(req.getDoctorId());
        entity.setDepartmentId(req.getDepartmentId());
        entity.setAppointmentDate(req.getAppointmentDate());
        entity.setTimeSlot(req.getTimeSlot());
        entity.setReason(req.getReason());
        entity.setRemark(req.getRemark());

        // 对齐建表脚本：默认状态 0=未确认
        entity.setStatus(0);

        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return appointmentRepository.save(entity);
    }

    @Transactional
    public AppointmentEntity update(Long id, UpdateAppointmentRequest req) {
        AppointmentEntity entity = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));

        // 外键存在性校验（同 create）
        if (!patientRepository.existsById(req.getPatientId())) {
            throw new RuntimeException("患者不存在");
        }
        if (!doctorRepository.existsById(req.getDoctorId())) {
            throw new RuntimeException("医生不存在");
        }
        if (!departmentRepository.existsById(req.getDepartmentId())) {
            throw new RuntimeException("科室不存在");
        }

        // 预约冲突检测：排除当前记录本身
        boolean hasConflict = appointmentRepository.findByDoctorIdAndAppointmentDateAndTimeSlot(
                req.getDoctorId(), 
                req.getAppointmentDate(), 
                req.getTimeSlot()
        ).stream().anyMatch(a -> a.getId() != id && a.getStatus() != 3);
        
        if (hasConflict) {
            throw new RuntimeException("该医生在此时段已有预约，请选择其他时间");
        }

        // 预约冲突检测 2：同一患者不能在同一医生同一时段重复预约（排除当前记录）
        boolean patientConflict = appointmentRepository.findByDoctorIdAndAppointmentDateAndTimeSlot(
                req.getDoctorId(),
                req.getAppointmentDate(),
                req.getTimeSlot()
        ).stream().anyMatch(a -> a.getId() != id && a.getPatientId().equals(req.getPatientId()) && a.getStatus() != 3);
        
        if (patientConflict) {
            throw new RuntimeException("该患者在该医生此时段已有预约，请勿重复预约");
        }

        entity.setPatientId(req.getPatientId());
        entity.setDoctorId(req.getDoctorId());
        entity.setDepartmentId(req.getDepartmentId());
        entity.setAppointmentDate(req.getAppointmentDate());
        entity.setTimeSlot(req.getTimeSlot());
        entity.setReason(req.getReason());
        entity.setRemark(req.getRemark());
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        entity.setUpdateTime(LocalDateTime.now());
        return appointmentRepository.save(entity);
    }

    @Transactional
    public AppointmentEntity updateStatus(Long id, Integer status) {
        AppointmentEntity entity = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("预约不存在"));
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        return appointmentRepository.save(entity);
    }

    /**
     * 删除预约：根据 ID 删除预约记录。
     * 备注：若该预约有关联病历，需先删除病历记录。
     */
    @Transactional
    public void delete(Long id) {
        if (!appointmentRepository.existsById(id)) {
            throw new RuntimeException("预约不存在");
        }
        
        // 先删除关联的病历（medical_record.appointment_id 外键）
        medicalRecordRepository.deleteByAppointmentId(id);
        
        appointmentRepository.deleteById(id);
    }
}

