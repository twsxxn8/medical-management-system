package com.example.backend.repository;

import com.example.backend.entity.PatientEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 患者档案数据访问层（对应 {@code patient}）。
 * 备注：SQL 中 {@code user_id} 与 {@code id_card} 均为 UNIQUE，可用于快速定位档案。
 */
public interface PatientRepository extends JpaRepository<PatientEntity, Long> {
    Optional<PatientEntity> findByUserId(Long userId);

    Optional<PatientEntity> findByIdCard(String idCard);
}

