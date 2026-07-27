package com.example.backend.service;

import com.example.backend.dto.CachedDiagnosisResult;
import com.example.backend.dto.DiagnosisResult;
import com.example.backend.service.EmbeddingService.EmbeddingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 语义缓存服务。
 *
 * <p>在 LLM 调用前通过 Embedding API 向量化症状，与 Redis 中已缓存的向量
 * 做余弦相似度匹配。超过阈值直接返回缓存结果，减少 LLM API 调用。
 *
 * <p>架构：
 * <pre>
 *   check(symptoms) → embedding → 余弦扫描 ZSET → hit/miss
 *   store(symptoms, result) → embedding → SET + Lua 淘汰 → ZADD
 *   checkAndStreamOrDelegate(...) → hit 合成 SSE / miss 委托 AiStructuredService
 * </pre>
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_KEY_INDEX = "sc:index";
    private static final String REDIS_KEY_PREFIX_EMB = "sc:emb:";
    private static final String REDIS_KEY_PREFIX_RESULT = "sc:result:";
    private static final String REDIS_KEY_PREFIX_ORIG = "sc:orig:";

    private final StringRedisTemplate redis;
    private final EmbeddingService embeddingService;
    private final AiStructuredService aiStructuredService;
    private final AiCircuitBreakerService circuitBreakerService;
    private DefaultRedisScript<List> evictScript;

    @Value("${app.ai.semantic-cache.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.semantic-cache.threshold:0.88}")
    private double threshold;

    @Value("${app.ai.semantic-cache.ttl-seconds:3600}")
    private long ttlSeconds;

    @Value("${app.ai.semantic-cache.max-entries:1000}")
    private int maxEntries;

    /** 文本相似度降级阈值（Embedding API 不可用时启用） */
    private static final double TEXT_FALLBACK_THRESHOLD = 0.55;

    private int storeCount = 0;

    public SemanticCacheService(StringRedisTemplate redis,
                                EmbeddingService embeddingService,
                                AiStructuredService aiStructuredService,
                                AiCircuitBreakerService circuitBreakerService) {
        this.redis = redis;
        this.embeddingService = embeddingService;
        this.aiStructuredService = aiStructuredService;
        this.circuitBreakerService = circuitBreakerService;
    }

    @PostConstruct
    public void loadEvictScript() {
        try {
            InputStream is = new ClassPathResource("lua/semantic_cache_evict.lua").getInputStream();
            String lua = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            DefaultRedisScript<List> s = new DefaultRedisScript<>();
            s.setScriptText(lua);
            s.setResultType(List.class);
            this.evictScript = s;
            log.info("语义缓存淘汰 Lua 脚本加载成功");
        } catch (IOException e) {
            log.error("加载语义缓存淘汰 Lua 脚本失败", e);
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 检查缓存中是否有与 symptoms 语义相似的缓存结果。
     *
     * @param symptoms 用户症状描述
     * @return CacheResult，hit 时包含缓存的 DiagnosisResult 和相似度
     */
    public CacheResult check(String symptoms) {
        if (!enabled) return CacheResult.miss();

        String normalizedSymptoms = normalize(symptoms);
        if (normalizedSymptoms.isEmpty()) return CacheResult.miss();

        // 获取症状的 embedding 向量（带熔断保护，失败时降级为文本匹配）
        float[] queryEmb = null;
        boolean embeddingAvailable = true;
        try {
            queryEmb = circuitBreakerService.executeEmbedding(
                    () -> embeddingService.getEmbedding(normalizedSymptoms));
        } catch (RuntimeException e) {
            log.warn("获取 Embedding 失败（熔断/异常），降级为文本相似度匹配: {}", e.getMessage());
            embeddingAvailable = false;
        }

        // 拉取所有缓存条目索引
        Set<String> allHashes;
        try {
            allHashes = redis.opsForZSet().range(REDIS_KEY_INDEX, 0, -1);
        } catch (Exception e) {
            log.warn("Redis 读取缓存索引失败，跳过缓存检查: {}", e.getMessage());
            return CacheResult.miss();
        }

        if (allHashes == null || allHashes.isEmpty()) return CacheResult.miss();

        // 相似度扫描：优先用 embedding，降级为文本 Jaccard
        String bestHash = null;
        double bestScore = 0.0;
        double effectiveThreshold = embeddingAvailable ? threshold : TEXT_FALLBACK_THRESHOLD;
        List<String> staleHashes = new ArrayList<>();

        for (String hash : allHashes) {
            double score;
            if (embeddingAvailable) {
                // Embedding 模式：余弦相似度
                String embJson;
                try {
                    embJson = redis.opsForValue().get(REDIS_KEY_PREFIX_EMB + hash);
                } catch (Exception e) {
                    log.debug("Redis GET emb 失败: {}", hash);
                    continue;
                }
                if (embJson == null) {
                    staleHashes.add(hash);
                    continue;
                }
                float[] storedEmb = parseEmbeddingArray(embJson);
                if (storedEmb == null) continue;
                score = cosineSimilarity(queryEmb, storedEmb);
            } else {
                // 文本降级模式：字符 Jaccard 相似度
                String storedSymptoms;
                try {
                    storedSymptoms = redis.opsForValue().get(REDIS_KEY_PREFIX_ORIG + hash);
                } catch (Exception e) {
                    log.debug("Redis GET orig 失败: {}", hash);
                    continue;
                }
                if (storedSymptoms == null) {
                    // 无原始文本则尝试用 embedding（可能之前 embedding 成功过）
                    String embJson;
                    try {
                        embJson = redis.opsForValue().get(REDIS_KEY_PREFIX_EMB + hash);
                    } catch (Exception e) {
                        staleHashes.add(hash);
                        continue;
                    }
                    if (embJson == null) {
                        staleHashes.add(hash);
                        continue;
                    }
                    // embedding 存在但 query embedding 不可用，无法比较，跳过
                    continue;
                }
                score = textSimilarity(normalizedSymptoms, storedSymptoms);
            }

            if (score > bestScore) {
                bestScore = score;
                bestHash = hash;
            }
        }

        // 清理过期条目
        if (!staleHashes.isEmpty()) {
            try {
                redis.opsForZSet().remove(REDIS_KEY_INDEX, staleHashes.toArray());
            } catch (Exception e) {
                log.debug("清理 stale 条目失败: {}", e.getMessage());
            }
        }

        // 判断是否命中
        if (bestHash != null && bestScore >= effectiveThreshold) {
            try {
                String resultJson = redis.opsForValue().get(REDIS_KEY_PREFIX_RESULT + bestHash);
                if (resultJson != null) {
                    CachedDiagnosisResult cached = objectMapper.readValue(resultJson,
                            CachedDiagnosisResult.class);
                    cached.setSimilarityScore(bestScore);

                    // 更新 LRU 时间戳
                    try {
                        redis.opsForZSet().add(REDIS_KEY_INDEX, bestHash,
                                (double) System.currentTimeMillis());
                    } catch (Exception e) {
                        log.debug("更新 LRU 时间戳失败: {}", e.getMessage());
                    }

                    log.info("语义缓存命中: hash={}, similarity={:.4f}, mode={}, original={}",
                            bestHash, bestScore, embeddingAvailable ? "embedding" : "text",
                            cached.getOriginalSymptoms());
                    return CacheResult.hit(cached, bestScore);
                } else {
                    // result key 已过期，从索引中移除
                    try {
                        redis.opsForZSet().remove(REDIS_KEY_INDEX, bestHash);
                    } catch (Exception e) {
                        log.debug("移除过期索引失败: {}", e.getMessage());
                    }
                }
            } catch (JsonProcessingException e) {
                log.warn("反序列化缓存结果失败: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Redis 读取缓存结果失败: {}", e.getMessage());
            }
        }

        log.debug("语义缓存未命中: bestScore={:.4f} < threshold={:.2f}", bestScore, effectiveThreshold);
        return CacheResult.miss();
    }

    /**
     * 将诊断结果存入语义缓存。
     *
     * @param symptoms 用户症状描述
     * @param result   LLM 返回的诊断结果
     */
    public void store(String symptoms, DiagnosisResult result) {
        if (!enabled) return;
        if (result == null) return;

        String normalizedSymptoms = normalize(symptoms);
        if (normalizedSymptoms.isEmpty()) return;

        // 尝试获取 embedding（带熔断保护，失败不影响缓存存储）
        float[] embedding = null;
        try {
            embedding = circuitBreakerService.executeEmbedding(
                    () -> embeddingService.getEmbedding(normalizedSymptoms));
        } catch (RuntimeException e) {
            log.warn("获取 Embedding 失败（熔断/异常），仅存储文本用于降级匹配: {}", e.getMessage());
        }

        String hash = md5Hex(normalizedSymptoms).substring(0, 16);

        try {
            CachedDiagnosisResult cached = new CachedDiagnosisResult(
                    result, normalizedSymptoms, System.currentTimeMillis());
            String resultJson = objectMapper.writeValueAsString(cached);

            // 存储原始文本（始终存储，用于文本降级匹配）
            redis.opsForValue().set(REDIS_KEY_PREFIX_ORIG + hash, normalizedSymptoms);
            redis.expire(REDIS_KEY_PREFIX_ORIG + hash, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);

            // 存储诊断结果
            redis.opsForValue().set(REDIS_KEY_PREFIX_RESULT + hash, resultJson);
            redis.expire(REDIS_KEY_PREFIX_RESULT + hash, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);

            // 存储 embedding（仅当 API 调用成功时）
            if (embedding != null) {
                String embJson = embeddingToJson(embedding);
                redis.opsForValue().set(REDIS_KEY_PREFIX_EMB + hash, embJson);
                redis.expire(REDIS_KEY_PREFIX_EMB + hash, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            }

            // 执行 LRU 淘汰并添加索引
            long now = System.currentTimeMillis();
            try {
                if (evictScript != null) {
                    redis.execute(evictScript,
                            List.of(REDIS_KEY_INDEX),
                            String.valueOf(maxEntries),
                            hash,
                            String.valueOf(now));
                } else {
                    // Lua 脚本不可用时降级为 Java 操作
                    Long count = redis.opsForZSet().zCard(REDIS_KEY_INDEX);
                    if (count != null && count >= maxEntries) {
                        Set<String> oldest = redis.opsForZSet().range(REDIS_KEY_INDEX, 0, 0);
                        if (oldest != null && !oldest.isEmpty()) {
                            String evicted = oldest.iterator().next();
                            redis.delete(REDIS_KEY_PREFIX_EMB + evicted);
                            redis.delete(REDIS_KEY_PREFIX_RESULT + evicted);
                            redis.delete(REDIS_KEY_PREFIX_ORIG + evicted);
                            redis.opsForZSet().remove(REDIS_KEY_INDEX, evicted);
                        }
                    }
                    redis.opsForZSet().add(REDIS_KEY_INDEX, hash, (double) now);
                }
            } catch (Exception e) {
                log.warn("LRU 淘汰执行失败: {}", e.getMessage());
                // 尽力添加索引
                try {
                    redis.opsForZSet().add(REDIS_KEY_INDEX, hash, (double) now);
                } catch (Exception ex) {
                    log.warn("添加索引失败: {}", ex.getMessage());
                }
            }

            // 定期清理过期条目（每 50 次存储触发一次）
            storeCount++;
            if (storeCount % 50 == 0) {
                cleanupStaleEntries();
            }

            log.debug("语义缓存存储成功: hash={}, symptoms={}", hash, normalizedSymptoms);
        } catch (Exception e) {
            log.warn("缓存存储失败: {}", e.getMessage());
        }
    }

    /**
     * 流式端点的缓存检查与委托。
     *
     * @param symptoms 用户症状描述
     * @param history  对话历史
     * @param emitter  SSE 发射器
     */
    public void checkAndStreamOrDelegate(String symptoms,
                                         List<Map<String, String>> history,
                                         SseEmitter emitter) {
        CacheResult cacheResult = check(symptoms);

        if (cacheResult.isHit()) {
            // 缓存命中：合成 SSE 事件
            try {
                emitter.send(SseEmitter.event().name("fromCache").data("true"));
                CachedDiagnosisResult cached = cacheResult.getData();
                DiagnosisResult result = cached.getResult();

                // 生成摘要文本
                String summary = generateSummary(result, cached.getSimilarityScore(),
                        cached.getOriginalSymptoms());
                emitter.send(SseEmitter.event().name("token").data(summary));

                emitter.send(SseEmitter.event().name("done")
                        .data(objectMapper.writeValueAsString(result)));
                emitter.complete();
            } catch (Exception e) {
                log.error("缓存命中 SSE 发送失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("缓存响应失败：" + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        } else {
            // 缓存未命中：委托给 AiStructuredService
            aiStructuredService.streamStructuredDiagnosis(symptoms, history, emitter);
        }
    }

    // ==================== 工具方法 ====================

    /** 文本标准化：去除首尾空白、控制字符 */
    static String normalize(String symptoms) {
        if (symptoms == null) return "";
        String s = symptoms.trim();
        // 截断至 500 字符（与前端 maxlength 对齐）
        if (s.length() > 500) s = s.substring(0, 500);
        // 去除控制字符，保留 CJK、字母、数字、常用标点
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        return s;
    }

    /** MD5 十六进制 */
    static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }

    /** 余弦相似度 */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    /** 字符级 Jaccard 相似度（Embedding API 不可用时的降级方案） */
    static double textSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        // 提取字符集
        java.util.HashSet<Character> setA = new java.util.HashSet<>();
        java.util.HashSet<Character> setB = new java.util.HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);
        for (char c : b.toCharArray()) setB.add(c);
        // 计算 Jaccard: |A ∩ B| / |A ∪ B|
        java.util.HashSet<Character> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);
        java.util.HashSet<Character> union = new java.util.HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /** 从 JSON 字符串解析 float[] */
    static float[] parseEmbeddingArray(String jsonArray) {
        if (jsonArray == null) return null;
        try {
            @SuppressWarnings("unchecked")
            List<Number> list = objectMapper.readValue(jsonArray, List.class);
            float[] result = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = list.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.debug("解析 embedding 数组失败: {}", e.getMessage());
            return null;
        }
    }

    /** 将 float[] 序列化为 JSON 数组字符串 */
    static String embeddingToJson(float[] embedding) throws JsonProcessingException {
        double[] doubles = new double[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            doubles[i] = embedding[i];
        }
        return objectMapper.writeValueAsString(doubles);
    }

    /** 生成缓存命中的流式摘要文本 */
    private String generateSummary(DiagnosisResult r, double similarity, String originalSymptoms) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI 分析结果（缓存命中，相似度 ").append(String.format("%.0f%%", similarity * 100)).append("）\n\n");

        if (r.getPossibleDiseases() != null && !r.getPossibleDiseases().isEmpty()) {
            sb.append("可能疾病：");
            for (int i = 0; i < r.getPossibleDiseases().size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(r.getPossibleDiseases().get(i).getName());
            }
            sb.append("\n");
        }

        if (r.getRecommendedDepartment() != null && !r.getRecommendedDepartment().isEmpty()) {
            sb.append("建议科室：").append(r.getRecommendedDepartment()).append("\n");
        }

        if (r.getUrgency() != null && !r.getUrgency().isEmpty()) {
            sb.append("紧急程度：").append(r.getUrgency()).append("\n");
        }

        if (originalSymptoms != null && !originalSymptoms.isEmpty()) {
            sb.append("\n原缓存词：").append(originalSymptoms);
        }

        return sb.toString();
    }

    /** 定期清理 ZSET 中已无对应 emb key 的过期条目 */
    private void cleanupStaleEntries() {
        try {
            Set<String> allHashes = redis.opsForZSet().range(REDIS_KEY_INDEX, 0, -1);
            if (allHashes == null || allHashes.isEmpty()) return;

            List<String> stale = new ArrayList<>();
            for (String hash : allHashes) {
                Boolean exists = redis.hasKey(REDIS_KEY_PREFIX_EMB + hash);
                if (exists == null || !exists) {
                    stale.add(hash);
                }
            }
            if (!stale.isEmpty()) {
                redis.opsForZSet().remove(REDIS_KEY_INDEX, stale.toArray());
                log.debug("清理 {} 个过期索引条目", stale.size());
            }
        } catch (Exception e) {
            log.debug("定期清理过期条目失败: {}", e.getMessage());
        }
    }

    // ==================== 内部类 ====================

    /** 缓存查询结果 */
    public static class CacheResult {
        private final boolean hit;
        private final CachedDiagnosisResult data;
        private final double similarityScore;

        private CacheResult(boolean hit, CachedDiagnosisResult data, double similarityScore) {
            this.hit = hit;
            this.data = data;
            this.similarityScore = similarityScore;
        }

        public static CacheResult miss() {
            return new CacheResult(false, null, 0.0);
        }

        public static CacheResult hit(CachedDiagnosisResult data, double similarityScore) {
            return new CacheResult(true, data, similarityScore);
        }

        public boolean isHit() { return hit; }
        public CachedDiagnosisResult getData() { return data; }
        public double getSimilarityScore() { return similarityScore; }
    }
}
