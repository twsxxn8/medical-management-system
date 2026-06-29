package com.example.backend.service;

import com.example.backend.dto.CreateMedicineRequest;
import com.example.backend.dto.UpdateMedicineRequest;
import com.example.backend.Entity.MedicineEntity;
import com.example.backend.repository.MedicineRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 药品业务（后端模块第 4 个落地页面对应的服务层）。
 * 备注：当前先实现最小可用的 CRUD（列表/新增/更新/上下架）。
 */
@Service
public class MedicineService {
    private final MedicineRepository medicineRepository;

    public MedicineService(MedicineRepository medicineRepository) {
        this.medicineRepository = medicineRepository;
    }

    public List<MedicineEntity> listAll() {
        return medicineRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional
    public MedicineEntity create(CreateMedicineRequest req) {
        MedicineEntity entity = new MedicineEntity();
        entity.setName(req.getName());
        entity.setCategory(req.getCategory());
        entity.setSpecification(req.getSpecification());
        entity.setUnit(req.getUnit());
        entity.setPrice(req.getPrice());
        entity.setStock(req.getStock());
        entity.setManufacturer(req.getManufacturer());
        entity.setApprovalNumber(req.getApprovalNumber());
        entity.setStorageCondition(req.getStorageCondition());

        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        
        // 对齐 SQL 默认值：未传则默认为 1（上架）
        entity.setStatus(1);
        return medicineRepository.save(entity);
    }

    @Transactional
    public MedicineEntity update(Long id, UpdateMedicineRequest req) {
        MedicineEntity entity = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("药品不存在"));

        entity.setName(req.getName());
        entity.setCategory(req.getCategory());
        entity.setSpecification(req.getSpecification());
        entity.setUnit(req.getUnit());
        entity.setPrice(req.getPrice());
        entity.setStock(req.getStock());
        entity.setManufacturer(req.getManufacturer());
        entity.setApprovalNumber(req.getApprovalNumber());
        entity.setStorageCondition(req.getStorageCondition());
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        entity.setUpdateTime(LocalDateTime.now());
        return medicineRepository.save(entity);
    }

    @Transactional
    public MedicineEntity updateStatus(Long id, Integer status) {
        MedicineEntity entity = medicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("药品不存在"));
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        return medicineRepository.save(entity);
    }
}
