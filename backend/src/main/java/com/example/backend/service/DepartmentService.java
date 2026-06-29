package com.example.backend.service;

import com.example.backend.Entity.DepartmentEntity;
import com.example.backend.repository.DepartmentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 科室业务层：提供科室列表查询和新增功能。
 * 职责：处理业务逻辑，调用 Repository 层操作数据库，查询接口使用 Redis 缓存。
 */
@Service
public class DepartmentService {
    private static final String CACHE_KEY_ALL = "cache:departments:all";
    private static final long CACHE_TTL_SECONDS = 300;

    private final DepartmentRepository departmentRepository;
    private final RedisService redisService;

    public DepartmentService(DepartmentRepository departmentRepository, RedisService redisService) {
        this.departmentRepository = departmentRepository;
        this.redisService = redisService;
    }

    /**
     * 分页查询科室列表
     */
    public Page<DepartmentEntity> listAll(int page, int size) {
        // page 从 0 开始，按 id 降序排列
        return departmentRepository.findAll(PageRequest.of(page, size, Sort.by("id").descending()));
    }

    /**
     * 查询所有科室列表（缓存 300 秒）。
     * @return 按 ID 升序排列的科室列表
     */
    @SuppressWarnings("unchecked")
    public List<DepartmentEntity> listAll() {
        // 1. 尝试从缓存读取
        Object cached = redisService.get(CACHE_KEY_ALL);
        if (cached instanceof List) {
            return (List<DepartmentEntity>) cached;
        }

        // 2. 缓存未命中，查询数据库
        List<DepartmentEntity> list = departmentRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        // 3. 写入缓存，TTL = 300 秒
        redisService.set(CACHE_KEY_ALL, list, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        return list;
    }

    /**
     * 新增科室并保存到数据库
     * @param name 科室名称
     * @param parentId 父级科室 ID（支持层级结构）
     * @param description 科室描述
     * @param location 科室位置
     * @return 保存后的科室实体（包含自增 ID）
     */
    @Transactional
    public DepartmentEntity create(String name, Long parentId, String description, String location) {
        DepartmentEntity entity = new DepartmentEntity();
        entity.setName(name);
        entity.setParentId(parentId);
        entity.setDescription(description);
        entity.setLocation(location);
        
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        
        DepartmentEntity saved = departmentRepository.save(entity);

        // 写操作后清除缓存，保证数据一致性
        redisService.delete(CACHE_KEY_ALL);

        return saved;
    }

    /**
     * 删除科室：根据 ID 删除科室记录。
     * 备注：若该科室下有医生关联，删除会因外键约束失败，需先处理关联数据。
     */
    @Transactional
    public void delete(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("科室不存在");
        }
        departmentRepository.deleteById(id);

        // 写操作后清除缓存，保证数据一致性
        redisService.delete(CACHE_KEY_ALL);
    }

}

