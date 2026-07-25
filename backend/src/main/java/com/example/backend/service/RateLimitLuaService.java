package com.example.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Redis Lua 令牌桶限流服务。
 * 使用 StringRedisTemplate 确保 ARGV 以纯字符串传递（非 JSON），Lua 端 tonumber() 正常解析。
 */
@Service
public class RateLimitLuaService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitLuaService.class);

    private final StringRedisTemplate redis;
    private DefaultRedisScript<List> script;

    private static final Map<String, int[]> PROFILES = Map.of(
            "ip_default",   new int[]{20, 10},
            "ip_ai",        new int[]{5,   2},
            "user_default", new int[]{30, 15},
            "user_ai",      new int[]{8,   3},
            "apikey",       new int[]{50, 20}
    );

    public RateLimitLuaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    public void loadScript() {
        try {
            InputStream is = new ClassPathResource("lua/token_bucket.lua").getInputStream();
            String lua = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            DefaultRedisScript<List> s = new DefaultRedisScript<>();
            s.setScriptText(lua);
            s.setResultType(List.class);
            this.script = s;
            log.info("令牌桶 Lua 脚本加载成功");
        } catch (IOException e) {
            log.error("加载令牌桶 Lua 脚本失败", e);
        }
    }

    public RateLimitResult check(String key, int capacity, int rate) {
        if (script == null) return RateLimitResult.ALLOWED;
        String redisKey = "token:bucket:" + key;
        long now = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) redis.execute(
                script,
                List.of(redisKey),
                String.valueOf(capacity),
                String.valueOf(rate),
                String.valueOf(now)
        );
        if (result == null || result.isEmpty()) return RateLimitResult.ALLOWED;
        int ok = result.get(0).intValue();
        if (ok == 1) {
            int rem = result.size() > 1 ? result.get(1).intValue() : 0;
            return RateLimitResult.allowed(rem);
        } else {
            int retry = result.size() > 1 ? result.get(1).intValue() : 1000;
            return RateLimitResult.rejected(retry);
        }
    }

    public RateLimitResult checkByProfile(String dimension, String value, boolean isAiApi) {
        String n = "apikey".equals(dimension) ? "apikey" : dimension + (isAiApi ? "_ai" : "_default");
        int[] p = PROFILES.getOrDefault(n, new int[]{20, 10});
        return check(dimension + ":" + value, p[0], p[1]);
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final int remaining, retryAfterMs;
        static final RateLimitResult ALLOWED = new RateLimitResult(true, Integer.MAX_VALUE, 0);
        private RateLimitResult(boolean a, int r, int ms) { allowed = a; remaining = r; retryAfterMs = ms; }
        public static RateLimitResult allowed(int r) { return new RateLimitResult(true, r, 0); }
        public static RateLimitResult rejected(int ms) { return new RateLimitResult(false, 0, ms); }
        public boolean isAllowed() { return allowed; }
        public int getRemaining() { return remaining; }
        public int getRetryAfterMs() { return retryAfterMs; }
    }
}
