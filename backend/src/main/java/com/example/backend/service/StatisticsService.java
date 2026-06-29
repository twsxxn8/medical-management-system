package com.example.backend.service;

import com.example.backend.repository.AppointmentRepository;
import com.example.backend.repository.DepartmentRepository;
import com.example.backend.repository.DoctorRepository;
import com.example.backend.repository.PatientRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * 首页统计业务层。
 * 备注：聚合查询各模块数据量，提供首页概览统计，使用 Redis 缓存减少数据库压力。
 */
@Service
public class StatisticsService {
    private static final String CACHE_KEY_OVERVIEW = "cache:statistics:overview";
    private static final long CACHE_TTL_SECONDS = 60;

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final DepartmentRepository departmentRepository;
    private final RedisService redisService;

    public StatisticsService(
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            DepartmentRepository departmentRepository,
            RedisService redisService
    ) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.departmentRepository = departmentRepository;
        this.redisService = redisService;
    }

    /**
     * 获取首页概览统计数据（缓存 60 秒）。
     * 包含：患者总数、医生总数、今日预约数、科室总数。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOverview() {
        // 1. 尝试从 Redis 缓存读取
        Object cached = redisService.get(CACHE_KEY_OVERVIEW);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }

        // 2. 缓存未命中，查询数据库
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPatients", patientRepository.count());
        stats.put("totalDoctors", doctorRepository.count());

        LocalDate today = LocalDate.now();
        stats.put("todayAppointments", appointmentRepository.countByAppointmentDate(today));
        stats.put("totalDepartments", departmentRepository.count());

        // 3. 写入 Redis 缓存，TTL = 60 秒
        redisService.set(CACHE_KEY_OVERVIEW, stats, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        return stats;
    }
}
