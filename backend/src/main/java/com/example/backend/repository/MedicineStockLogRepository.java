package com.example.backend.repository;

import com.example.backend.Entity.MedicineStockLogEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 药品库存日志表数据访问层（对应 {@code medicine_stock_log}）。
 */
public interface MedicineStockLogRepository extends JpaRepository<MedicineStockLogEntity, Long> {
    List<MedicineStockLogEntity> findByMedicineId(Long medicineId, Sort sort);
}
