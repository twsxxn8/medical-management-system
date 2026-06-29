package com.example.backend.service;

import com.example.backend.dto.CreatePatientRequest;
import com.example.backend.dto.UpdatePatientRequest;
import com.example.backend.entity.PatientEntity;
import com.example.backend.repository.PatientRepository;
import com.example.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 患者档案业务（patient 模块服务层）。
 * 备注：当前先实现最小可用 CRUD（列表/新增/更新），用于给预约/病历模块提供依赖数据。
 */
@Service
public class PatientService {
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PatientService(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    public List<PatientEntity> listAll() {
        // 按 id 升序，便于前端稳定展示
        return patientRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional
    public PatientEntity create(CreatePatientRequest req) {
        // 如果提供了 user_id，校验是否存在
        if (req.getUserId() != null && !userRepository.existsById(req.getUserId())) {
            throw new RuntimeException("关联用户不存在");
        }

        // UNIQUE 约束：身份证号唯一
        if (patientRepository.findByIdCard(req.getIdCard()).isPresent()) {
            throw new RuntimeException("身份证号已存在");
        }

        PatientEntity entity = new PatientEntity();
        entity.setUserId(req.getUserId()); // 可为 null
        entity.setIdCard(req.getIdCard());
        entity.setGender(req.getGender());
        entity.setBirthDate(req.getBirthDate());
        entity.setAddress(req.getAddress());
        entity.setEmergencyContact(req.getEmergencyContact());
        entity.setEmergencyPhone(req.getEmergencyPhone());
        entity.setMedicalHistory(req.getMedicalHistory());
        entity.setCreateTime(LocalDateTime.now());
        return patientRepository.save(entity);
    }

    @Transactional
    public PatientEntity update(Long id, UpdatePatientRequest req) {
        PatientEntity entity = patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("患者档案不存在"));

        entity.setGender(req.getGender());
        entity.setBirthDate(req.getBirthDate());
        entity.setAddress(req.getAddress());
        entity.setEmergencyContact(req.getEmergencyContact());
        entity.setEmergencyPhone(req.getEmergencyPhone());
        entity.setMedicalHistory(req.getMedicalHistory());

        return patientRepository.save(entity);
    }

    /**
     * 删除患者：根据 ID 删除患者记录。
     * 备注：若该患者有关联预约或病历，删除会因外键约束失败，需先处理关联数据。
     */
    @Transactional
    public void delete(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new RuntimeException("患者档案不存在");
        }
        patientRepository.deleteById(id);
    }
}

