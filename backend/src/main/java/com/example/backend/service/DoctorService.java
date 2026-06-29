package com.example.backend.service;

import com.example.backend.dto.DoctorResponse;
import com.example.backend.dto.UpdateDoctorRequest;
import com.example.backend.Entity.DoctorEntity;
import com.example.backend.repository.DoctorRepository;
import com.example.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医生业务（后端模块第 2 个落地页面对应的服务层）。
 * 备注：当前先实现最小可用的 CRUD（列表/新增/更新/启用停用），用于联调与演示。
 */
@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    public DoctorService(DoctorRepository doctorRepository, UserRepository userRepository) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
    }

    /**
     * 列表查询：根据角色过滤数据。
     * - ADMIN：返回所有医生
     * - DOCTOR：只返回当前登录医生的记录
     */
    public List<DoctorResponse> list(Optional<Long> departmentId, Optional<Long> currentUserId, String role) {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        List<DoctorEntity> doctors;

        // 权限控制：医生只能看自己，管理员看全部
        if ("DOCTOR".equals(role) && currentUserId.isPresent()) {
            doctors = doctorRepository.findByUserId(currentUserId.get(), sort);
        } else if (departmentId.isPresent()) {
            doctors = doctorRepository.findByDepartmentId(departmentId.get(), sort);
        } else {
            doctors = doctorRepository.findAll(sort);
        }

        // 关联查询用户姓名
        return doctors.stream().map(doctor -> {
            DoctorResponse response = new DoctorResponse();
            response.setId(doctor.getId());
            response.setUserId(doctor.getUserId());
            response.setDepartmentId(doctor.getDepartmentId());
            response.setTitle(doctor.getTitle());
            response.setSpecialty(doctor.getSpecialty());
            response.setConsultationFee(doctor.getConsultationFee());
            response.setIntroduction(doctor.getIntroduction());
            response.setSchedule(doctor.getSchedule());
            response.setStatus(doctor.getStatus());
            response.setCreateTime(doctor.getCreateTime());
            response.setUpdateTime(doctor.getUpdateTime());

            // 查询关联的用户姓名
            userRepository.findById(doctor.getUserId()).ifPresent(user -> {
                response.setRealName(user.getRealName());
            });

            return response;
        }).collect(Collectors.toList());
    }

    /**
     * 判断 doctor 主键对应的档案是否属于指定用户（doctor.user_id == userId）。
     * 用于医生端仅能改自己资料，防止越权修改他人。
     */
    public boolean isProfileOwnedByUser(Long doctorId, Long userId) {
        if (userId == null) {
            return false;
        }
        return doctorRepository.findById(doctorId)
                .map(d -> userId.equals(d.getUserId()))
                .orElse(false);
    }

    @Transactional
    public DoctorEntity update(Long id, UpdateDoctorRequest req) {
        DoctorEntity entity = doctorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("医生不存在"));

        entity.setDepartmentId(req.getDepartmentId());
        entity.setTitle(req.getTitle());
        entity.setSpecialty(req.getSpecialty());
        entity.setConsultationFee(req.getConsultationFee());
        entity.setIntroduction(req.getIntroduction());
        entity.setSchedule(req.getSchedule());
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        entity.setUpdateTime(LocalDateTime.now());
        return doctorRepository.save(entity);
    }

    @Transactional
    public DoctorEntity updateStatus(Long id, Integer status) {
        DoctorEntity entity = doctorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("医生不存在"));
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        return doctorRepository.save(entity);
    }

}

