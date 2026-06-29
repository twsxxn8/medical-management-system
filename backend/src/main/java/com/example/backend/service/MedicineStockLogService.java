package com.example.backend.service;

import com.example.backend.dto.CreateMedicineStockLogRequest;
import com.example.backend.Entity.MedicineEntity;
import com.example.backend.Entity.MedicineStockLogEntity;
import com.example.backend.repository.MedicineRepository;
import com.example.backend.repository.MedicineStockLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 药品库存日志业务（后端模块第 8 个落地页面对应的服务层）。
 * 备注：记录每次库存变动，并在变动时同步更新 {@code medicine} 表的 {@code stock} 字段。
 */
@Service
public class MedicineStockLogService {
    private final MedicineStockLogRepository stockLogRepository;
    private final MedicineRepository medicineRepository;

    public MedicineStockLogService(MedicineStockLogRepository stockLogRepository, MedicineRepository medicineRepository) {
        this.stockLogRepository = stockLogRepository;
        this.medicineRepository = medicineRepository;
    }

    /**
     * 查询日志列表：可选按药品筛选。
     */
    public List<MedicineStockLogEntity> list(Optional<Long> medicineId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createTime");
        if (medicineId.isPresent()) {
            return stockLogRepository.findByMedicineId(medicineId.get(), sort);
        }
        return stockLogRepository.findAll(sort);
    }

    /**
     * 新增库存变动记录：同时更新药品库存。
     */
    @Transactional
    public MedicineStockLogEntity create(CreateMedicineStockLogRequest req) {
        // 1. 校验药品是否存在
        MedicineEntity medicine = medicineRepository.findById(req.getMedicineId())
                .orElseThrow(() -> new RuntimeException("药品不存在"));

        // 2. 更新库存（type=1 入库增加，type=2 出库减少）
        int change = req.getType() == 1 ? req.getQuantity() : -req.getQuantity();
        
        // 出库时检查库存是否足够
        if (req.getType() == 2 && medicine.getStock() < req.getQuantity()) {
            throw new RuntimeException("库存不足，当前库存：" + medicine.getStock());
        }
        
        medicine.setStock(medicine.getStock() + change);
        medicine.setUpdateTime(LocalDateTime.now());
        medicineRepository.save(medicine);

        // 3. 记录日志
        MedicineStockLogEntity entity = new MedicineStockLogEntity();
        entity.setMedicineId(req.getMedicineId());
        entity.setType(req.getType());
        entity.setQuantity(req.getQuantity());
        entity.setOperatorId(req.getOperatorId());
        entity.setRemark(req.getRemark());
        entity.setCreateTime(LocalDateTime.now());

        return stockLogRepository.save(entity);
    }
}
