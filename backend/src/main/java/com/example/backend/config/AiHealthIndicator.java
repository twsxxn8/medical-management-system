package com.example.backend.config;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 复合健康检查指示器。
 *
 * <p>检查以下依赖项：
 * <ul>
 *   <li>SQL Server — 执行 SELECT 1 验证连接</li>
 *   <li>Redis — 执行 PING 验证连接</li>
 * </ul>
 */
@Component
public class AiHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AiHealthIndicator.class);

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public AiHealthIndicator(DataSource dataSource, StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // 检查数据库连接
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            builder.withDetail("database", "UP");
        } catch (Exception e) {
            log.warn("数据库健康检查失败: {}", e.getMessage());
            builder.withDetail("database", "DOWN — " + e.getMessage());
            builder.down();
        }

        // 检查 Redis 连接
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            builder.withDetail("redis", "PONG".equals(pong) ? "UP" : "UNEXPECTED: " + pong);
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            builder.withDetail("redis", "DOWN — " + e.getMessage());
            builder.down();
        }

        return builder.build();
    }
}
