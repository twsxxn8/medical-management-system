package com.example.backend.service;

import com.example.backend.Entity.DoctorEntity;
import com.example.backend.Entity.UserEntity;
import com.example.backend.dto.CreateUserRequest;
import com.example.backend.repository.DoctorRepository;
import com.example.backend.repository.PatientRepository;
import com.example.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户业务层：处理账号创建、删除等逻辑。
 * 备注：创建医生账号时自动同步创建医生档案。
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final com.example.backend.repository.AppointmentRepository appointmentRepository;
    private final com.example.backend.repository.MedicalRecordRepository medicalRecordRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            com.example.backend.repository.AppointmentRepository appointmentRepository,
            com.example.backend.repository.MedicalRecordRepository medicalRecordRepository
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    public List<UserEntity> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public UserEntity create(CreateUserRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(req.getUsername());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setRole(req.getRole());
        user.setPhone(req.getPhone());
        user.setEmail("");
        user.setStatus(1);

        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);

        UserEntity savedUser = userRepository.save(user);

        // 如果角色是医生，自动创建医生记录
        if ("DOCTOR".equals(req.getRole())) {
            DoctorEntity doctor = new DoctorEntity();
            doctor.setUserId(savedUser.getId());
            doctor.setDepartmentId(1L); // 默认科室 ID
            doctor.setStatus(1);
            doctor.setCreateTime(now);
            doctor.setUpdateTime(now);
            doctorRepository.save(doctor);
        }

        return savedUser;
    }

    @Transactional
    public void delete(Long id) {
        // 1. 查找关联的医生记录
        doctorRepository.findByUserId(id).forEach(doctor -> {
            // 2. 删除该医生的所有预约（会级联删除关联的病历）
            appointmentRepository.findByDoctorId(doctor.getId(), org.springframework.data.domain.Sort.unsorted())
                    .forEach(appointment -> {
                        // 先删除关联的病历
                        medicalRecordRepository.deleteByAppointmentId(appointment.getId());
                        // 再删除预约
                        appointmentRepository.delete(appointment);
                    });
            
            // 3. 删除医生记录
            doctorRepository.delete(doctor);
        });

        // 4. 删除关联的患者档案
        patientRepository.findByUserId(id).ifPresent(patientRepository::delete);

        // 5. 最后删除用户
        userRepository.deleteById(id);
    }
}
