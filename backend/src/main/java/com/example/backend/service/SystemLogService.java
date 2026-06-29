package com.example.backend.service;

import com.example.backend.Entity.SystemLogEntity;
import com.example.backend.repository.SystemLogRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 系统日志业务：仅提供列表查询，用于审计与追溯。
 */
@Service
public class SystemLogService {
    private final SystemLogRepository systemLogRepository;

    public SystemLogService(SystemLogRepository systemLogRepository) {
        this.systemLogRepository = systemLogRepository;
    }

    /**
     * 查询日志列表：按创建时间倒序排列。
     */
    public List<SystemLogEntity> listAll() {
        return systemLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"));
    }
}
