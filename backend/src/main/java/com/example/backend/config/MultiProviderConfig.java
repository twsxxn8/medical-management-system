package com.example.backend.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多 AI Provider 配置绑定。
 *
 * <p>支持两种配置源：
 * <ol>
 *   <li>新版：{@code app.ai.providers} 列表 —— 每个元素有 name、api-key、api-url 等字段</li>
 *   <li>旧版：{@code app.ai.api-key}、{@code app.ai.api-url} 等 flat key —— 自动构造单个 "default" provider</li>
 * </ol>
 *
 * <p>通过 {@code @ConfigurationProperties(prefix = "app.ai")} 绑定 application.yml，
 * 再由 {@link #buildChatProviders()} / {@link #buildEmbeddingProviders()} 生产排序后的不可变配置列表。
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class MultiProviderConfig {

    /** 新版：多 provider 列表 */
    private List<ProviderEntry> providers = new ArrayList<>();

    /** 语义缓存配置 */
    private SemanticCacheProps semanticCache = new SemanticCacheProps();

    // ===== 旧版 flat key =====

    private String apiKey;
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";
    private String model = "deepseek-v4-flash";
    private String embeddingUrl = "https://api.deepseek.com/v1/embeddings";
    private String embeddingModel = "deepseek-chat";

    // ===== Getters / Setters（供 Spring 绑定） =====

    public List<ProviderEntry> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderEntry> providers) {
        this.providers = providers;
    }

    public SemanticCacheProps getSemanticCache() {
        return semanticCache;
    }

    public void setSemanticCache(SemanticCacheProps semanticCache) {
        this.semanticCache = semanticCache;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEmbeddingUrl() {
        return embeddingUrl;
    }

    public void setEmbeddingUrl(String embeddingUrl) {
        this.embeddingUrl = embeddingUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // ===== Build 方法 =====

    /**
     * 构建已排序的 Chat provider 列表。
     * 如果 {@code providers} 列表为空，回退到旧版 flat 配置。
     */
    public List<AiProviderConfig> buildChatProviders() {
        if (providers != null && !providers.isEmpty()) {
            return buildFromList();
        }
        return buildLegacyFallback();
    }

    /**
     * 构建 Embedding provider 列表（仅包含配置了 embedding 能力的 provider）。
     */
    public List<AiProviderConfig> buildEmbeddingProviders() {
        List<AiProviderConfig> all = buildChatProviders();
        if (providers != null && !providers.isEmpty()) {
            // 多 provider 模式：过滤出有 embedding URL 的
            return all.stream()
                    .filter(p -> p.getEmbeddingUrl() != null && !p.getEmbeddingUrl().isEmpty())
                    .collect(Collectors.toList());
        }
        // 旧版 fallback：embedding 总是可用的（与 chat 共用 apiKey）
        return all;
    }

    // ===== 内部方法 =====

    private List<AiProviderConfig> buildFromList() {
        return providers.stream()
                .map(entry -> {
                    String name = entry.getName();
                    if (name == null || name.isEmpty()) {
                        throw new IllegalStateException("Each provider must have a 'name'");
                    }
                    int priority = entry.getPriority() != null
                            ? entry.getPriority()
                            : Integer.MAX_VALUE;
                    return new AiProviderConfig(
                            name,
                            entry.getApiKey(),
                            entry.getApiUrl(),
                            entry.getModel(),
                            entry.getEmbeddingUrl() != null
                                    ? entry.getEmbeddingUrl()
                                    : entry.getApiUrl(),
                            entry.getEmbeddingModel() != null
                                    ? entry.getEmbeddingModel()
                                    : entry.getModel(),
                            priority);
                })
                .sorted(Comparator.comparingInt(AiProviderConfig::getPriority))
                .collect(Collectors.toList());
    }

    private List<AiProviderConfig> buildLegacyFallback() {
        List<AiProviderConfig> list = new ArrayList<>();
        list.add(new AiProviderConfig(
                "deepseek", apiKey, apiUrl, model,
                embeddingUrl, embeddingModel, 1));
        return list;
    }

    // ===== 内部类 =====

    /**
     * application.yml 中 providers 列表的元素。
     */
    public static class ProviderEntry {
        private String name;
        private String apiKey;
        private String apiUrl;
        private String model;
        private String embeddingUrl;
        private String embeddingModel;
        private Integer priority;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEmbeddingUrl() {
            return embeddingUrl;
        }

        public void setEmbeddingUrl(String embeddingUrl) {
            this.embeddingUrl = embeddingUrl;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }
    }

    /**
     * 语义缓存配置内部属性。
     */
    public static class SemanticCacheProps {
        private boolean enabled = true;
        private double threshold = 0.88;
        private long ttlSeconds = 3600;
        private int maxEntries = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }
}
