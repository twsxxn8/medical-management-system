package com.example.backend.repository;

import com.example.backend.Entity.DoctorEntity;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 医生表数据访问层（对应 {@code doctor}）。
 */
public interface DoctorRepository extends JpaRepository<DoctorEntity, Long> {
    List<DoctorEntity> findByDepartmentId(Long departmentId, Sort sort);

    /**
     * 根据用户 ID 查询医生记录（用于医生只能查看自己的场景）。
     */
    List<DoctorEntity> findByUserId(Long userId, Sort sort);

    /**
     * 根据用户 ID 查询医生记录（用于删除用户时级联删除）。
     */
    List<DoctorEntity> findByUserId(Long userId);
}

