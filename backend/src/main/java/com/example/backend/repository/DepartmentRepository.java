package com.example.backend.repository;

import com.example.backend.Entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 科室表数据访问层（对应 {@code department}）。
 */
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {}

