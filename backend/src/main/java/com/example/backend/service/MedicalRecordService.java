package com.example.backend.service;


import com.example.backend.dto.CreateMedicalRecordRequest;
import com.example.backend.dto.UpdateMedicalRecordRequest;
import com.example.backend.Entity.MedicalRecordEntity;
import com.example.backend.Entity.DoctorEntity;
import com.example.backend.Entity.MedicineEntity;
import com.example.backend.repository.DoctorRepository;
import com.example.backend.repository.MedicalRecordRepository;
import com.example.backend.repository.MedicineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 病历业务（后端模块第 5 个落地页面对应的服务层）。
 * 备注：当前实现最小可用的 CRUD（列表/新增/更新），支持按患者或医生筛选。
 */
@Service
public class MedicalRecordService {
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicineRepository medicineRepository;
    private final DoctorRepository doctorRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            MedicineRepository medicineRepository,
            DoctorRepository doctorRepository
    ) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.medicineRepository = medicineRepository;
        this.doctorRepository = doctorRepository;
    }

    public List<MedicalRecordEntity> list(Optional<Long> patientId, Optional<Long> doctorId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "visitDate");
        if (patientId.isPresent()) {
            return medicalRecordRepository.findByPatientId(patientId.get(), sort);
        }
        if (doctorId.isPresent()) {
            return medicalRecordRepository.findByDoctorId(doctorId.get(), sort);
        }
        return medicalRecordRepository.findAll(sort);
    }

    @Transactional
    public MedicalRecordEntity create(CreateMedicalRecordRequest req) {
        // 如果有药品清单，扣减库存
        if (req.getPrescriptionMedicines() != null && !req.getPrescriptionMedicines().isEmpty()) {
            deductMedicineStock(req.getPrescriptionMedicines());
        }

        MedicalRecordEntity entity = new MedicalRecordEntity();
        entity.setPatientId(req.getPatientId());
        entity.setDoctorId(req.getDoctorId());
        entity.setAppointmentId(req.getAppointmentId());
        entity.setDiagnosis(req.getDiagnosis());
        entity.setPrescription(req.getPrescription());
        entity.setPrescriptionMedicines(req.getPrescriptionMedicines());
        entity.setSymptoms(req.getSymptoms());
        entity.setVisitDate(req.getVisitDate());

        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        return medicalRecordRepository.save(entity);
    }

    @Transactional
    public MedicalRecordEntity update(Long id, UpdateMedicalRecordRequest req) {
        MedicalRecordEntity entity = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("病历不存在"));

        entity.setDiagnosis(req.getDiagnosis());
        entity.setPrescription(req.getPrescription());
        entity.setPrescriptionMedicines(req.getPrescriptionMedicines());
        entity.setSymptoms(req.getSymptoms());
        entity.setVisitDate(req.getVisitDate());
        entity.setUpdateTime(LocalDateTime.now());

        return medicalRecordRepository.save(entity);
    }

    /**
     * 删除病历：根据 ID 删除病历记录。
     */
    @Transactional
    public void delete(Long id) {
        if (!medicalRecordRepository.existsById(id)) {
            throw new RuntimeException("病历不存在");
        }
        medicalRecordRepository.deleteById(id);
    }

    /**
     * 根据 userId 查找 doctor 表主键（doctor.id）。
     * 备注：medical_record.doctor_id 关联的是 doctor.id，而非 user.id。
     */
    public Optional<Long> resolveDoctorTableIdByUserId(Long userId) {
        List<DoctorEntity> doctors = doctorRepository.findByUserId(userId);
        if (doctors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(doctors.get(0).getId());
    }

    /**
     * 判断某病历是否属于该 userId 对应的医生。
     */
    public boolean isMedicalRecordOwnedByDoctorUser(Long medicalRecordId, Long userId) {
        Optional<Long> doctorId = resolveDoctorTableIdByUserId(userId);
        if (doctorId.isEmpty()) {
            return false;
        }
        return medicalRecordRepository.findById(medicalRecordId)
                .map(record -> record.getDoctorId().equals(doctorId.get()))
                .orElse(false);
    }

    /**
     * 扣减药品库存：解析 JSON 格式的药品清单并扣减库存。
     * 格式：[{"medicineId": 1, "quantity": 2}, {"medicineId": 2, "quantity": 1}]
     */
    @SuppressWarnings("unchecked")
    private void deductMedicineStock(String prescriptionMedicines) {
        try {
            List<Map<String, Object>> medicines = objectMapper.readValue(prescriptionMedicines, List.class);
            for (Map<String, Object> item : medicines) {
                Long medicineId = Long.valueOf(item.get("medicineId").toString());
                Integer quantity = Integer.valueOf(item.get("quantity").toString());
                
                MedicineEntity medicine = medicineRepository.findById(medicineId)
                        .orElseThrow(() -> new RuntimeException("药品不存在，ID: " + medicineId));
                
                if (medicine.getStock() < quantity) {
                    throw new RuntimeException("药品库存不足：" + medicine.getName() + "，当前库存：" + medicine.getStock());
                }
                
                medicine.setStock(medicine.getStock() - quantity);
                medicine.setUpdateTime(LocalDateTime.now());
                medicineRepository.save(medicine);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析药品清单失败");
        }
    }
}
