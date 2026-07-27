package com.example.backend.config;

/**
 * 不可变配置快照：单个 AI provider 的完整连接参数。
 *
 * <p>由 {@link MultiProviderConfig} 在应用启动时根据 application.yml 构建，
 * 保证运行时各 Service 拿到的配置是一致的、不可变的。
 */
public class AiProviderConfig {

    /** provider 唯一标识名，如 "deepseek"、"siliconflow" */
    private final String name;

    /** API 密钥（Bearer token） */
    private final String apiKey;

    /** Chat Completion API 完整 URL */
    private final String apiUrl;

    /** 对话模型名 */
    private final String model;

    /** Embedding API 完整 URL */
    private final String embeddingUrl;

    /** Embedding 模型名 */
    private final String embeddingModel;

    /** 优先级（越小越优先），用于降级排序 */
    private final int priority;

    public AiProviderConfig(String name, String apiKey, String apiUrl, String model,
                            String embeddingUrl, String embeddingModel, int priority) {
        this.name = name;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.embeddingUrl = embeddingUrl;
        this.embeddingModel = embeddingModel;
        this.priority = priority;
    }

    // ===== Getters =====

    public String getName() {
        return name;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getModel() {
        return model;
    }

    public String getEmbeddingUrl() {
        return embeddingUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "AiProviderConfig{name='" + name + "', model='" + model + "', priority=" + priority + '}';
    }
}
