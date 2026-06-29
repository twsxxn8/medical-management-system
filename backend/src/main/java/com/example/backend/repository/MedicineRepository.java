package com.example.backend.repository;

import com.example.backend.Entity.MedicineEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 药品表数据访问层（对应 {@code medicine}）。
 */
public interface MedicineRepository extends JpaRepository<MedicineEntity, Long> {
    List<MedicineEntity> findByStatus(Integer status, Sort sort);
}
