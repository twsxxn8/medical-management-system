package com.example.backend.repository;

import com.example.backend.Entity.SystemLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统操作日志表数据访问层（对应 {@code system_log}）。
 */
public interface SystemLogRepository extends JpaRepository<SystemLogEntity, Long> {}
