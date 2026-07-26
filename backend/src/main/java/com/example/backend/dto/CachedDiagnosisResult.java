package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 缓存包装 DTO，在 DiagnosisResult 基础上附加缓存元数据。
 * 存储于 Redis sc:result:{hash}，反序列化时 fromCache 始终为 true。
 */
public class CachedDiagnosisResult {

    /** 核心诊断结果 */
    @JsonProperty("result")
    private DiagnosisResult result;

    /** 是否为缓存命中（反序列化时始终为 true） */
    @JsonProperty("from_cache")
    private boolean fromCache;

    /** 原始症状文本 */
    @JsonProperty("original_symptoms")
    private String originalSymptoms;

    /** 缓存创建时间（epoch 毫秒） */
    @JsonProperty("cached_at")
    private long cachedAt;

    /** 相似度分数（仅在查询时填充，不持久化） */
    private transient double similarityScore;

    public CachedDiagnosisResult() {}

    public CachedDiagnosisResult(DiagnosisResult result, String originalSymptoms, long cachedAt) {
        this.result = result;
        this.fromCache = true;
        this.originalSymptoms = originalSymptoms;
        this.cachedAt = cachedAt;
    }

    // ===== Getters & Setters =====

    public DiagnosisResult getResult() {
        return result;
    }

    public void setResult(DiagnosisResult result) {
        this.result = result;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public void setFromCache(boolean fromCache) {
        this.fromCache = fromCache;
    }

    public String getOriginalSymptoms() {
        return originalSymptoms;
    }

    public void setOriginalSymptoms(String originalSymptoms) {
        this.originalSymptoms = originalSymptoms;
    }

    public long getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(long cachedAt) {
        this.cachedAt = cachedAt;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }
}
