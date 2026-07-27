package com.example.backend.service;

import com.example.backend.config.AiProviderConfig;
import com.example.backend.config.MultiProviderConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI Provider 路由器：统一调度入口。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>同步调用</b> — priority 顺序 intra-call failover：失败自动换下一个 provider</li>
 *   <li><b>流式调用</b> — pre-call 断路器检查：选择第一个未 OPEN 的 provider</li>
 *   <li><b>Embedding</b> — 选择最佳可用 provider</li>
 * </ul>
 *
 * <p>每个 provider 有独立的 CircuitBreaker（{@code {name}-{type}}），
 * 状态由 Resilience4j 自动管理。
 */
@Service
public class AiProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);

    private final List<AiProviderConfig> chatProviders;
    private final List<AiProviderConfig> embeddingProviders;
    private final AiCircuitBreakerService breakerService;

    /** 所有 provider 均失败时的通用降级文本 */
    private static final String ALL_DOWN_MSG =
            "所有 AI 服务暂时不可用，请 30 秒后重试。\n" +
            "您可以尝试以下操作：\n" +
            "1. 稍等片刻后重新发送\n" +
            "2. 前往医院就诊获取专业诊断\n" +
            "3. 紧急情况请拨打 120";

    public AiProviderRouter(MultiProviderConfig config,
                            AiCircuitBreakerService breakerService) {
        this.chatProviders = config.buildChatProviders();
        this.embeddingProviders = config.buildEmbeddingProviders();
        this.breakerService = breakerService;
        log.info("AiProviderRouter 初始化完成: chat={}, embedding={}",
                chatProviders.size(), embeddingProviders.size());
        for (AiProviderConfig p : chatProviders) {
            log.info("  - {} : priority={}, model={}", p.getName(), p.getPriority(), p.getModel());
        }
    }

    // ==================== 同步调用：intra-call failover ====================

    /**
     * 执行带自动 failover 的同步 Chat 调用。
     *
     * <p>按 priority 顺序遍历 provider，失败后自动尝试下一个。
     * 全部失败时返回 {@code fallback.get()}。
     *
     * @param call     实际 LLM 调用（接受选中的 provider）
     * @param fallback 全部 provider 失败时的降级逻辑
     * @param <T>      返回值类型
     * @return 调用结果或降级值
     */
    public <T> T executeChatSync(Function<AiProviderConfig, T> call,
                                  Supplier<T> fallback) {
        Exception lastException = null;
        for (AiProviderConfig provider : chatProviders) {
            String breakerName = provider.getName() + "-chat";
            try {
                return breakerService.executeSyncCall(breakerName,
                        () -> call.apply(provider), () -> {
                            throw new RuntimeException("breaker OPEN for " + provider.getName());
                        });
            } catch (Exception e) {
                lastException = e;
                log.warn("Provider {} (chat) 调用失败，尝试下一个: {}",
                        provider.getName(), e.getMessage());
            }
        }
        log.error("所有 chat provider 均已失败，执行降级", lastException);
        return fallback.get();
    }

    /** 同步调用 failover（使用预置降级文本）。 */
    @SuppressWarnings("unchecked")
    public <T> T executeChatSync(Function<AiProviderConfig, T> call) {
        return executeChatSync(call, () -> (T) ALL_DOWN_MSG);
    }

    // ==================== 流式调用：pre-call 断路器检查 ====================

    /**
     * 获取最佳可用流式 provider。
     *
     * <p>按 priority 顺序遍历，返回第一个 stream 断路器非 OPEN 的 provider。
     * 全部 OPEN 时通过 emitter 发送 SSE error 并返回 null。
     *
     * @param emitter SSE 发射器（全部不可用时用于发送错误）
     * @return 最佳可用 provider，或 null（全部不可用）
     */
    public AiProviderConfig acquireStreamProvider(SseEmitter emitter) {
        for (AiProviderConfig provider : chatProviders) {
            String breakerName = provider.getName() + "-chat-stream";
            if (breakerService.getState(breakerName) != CircuitBreaker.State.OPEN) {
                log.debug("流式 provider 选择: {}", provider.getName());
                return provider;
            }
            log.info("流式 provider {} 断路器 OPEN，跳过", provider.getName());
        }
        log.error("所有流式 provider 断路器均 OPEN，快速失败");
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(ALL_DOWN_MSG));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return null;
    }

    /** 记录流式调用成功到对应 provider 的断路器。 */
    public void recordStreamResult(String providerName, boolean success) {
        breakerService.recordStreamResult(providerName + "-chat-stream", success);
    }

    /** 记录流式调用异常到对应 provider 的断路器。 */
    public void recordStreamException(String providerName, Throwable t) {
        breakerService.recordStreamException(providerName + "-chat-stream", t);
    }

    // ==================== Embedding ====================

    /**
     * 选择最佳可用 embedding provider。
     *
     * <p>跳过断路器 OPEN 的 provider，全部 OPEN 时返回第一个（让断路器抛出异常）。
     */
    public AiProviderConfig selectBestEmbeddingProvider() {
        for (AiProviderConfig provider : embeddingProviders) {
            String breakerName = provider.getName() + "-embedding";
            if (breakerService.getState(breakerName) != CircuitBreaker.State.OPEN) {
                return provider;
            }
        }
        log.warn("所有 embedding provider 断路器均 OPEN，使用第一个");
        return embeddingProviders.get(0);
    }

    /**
     * 带熔断保护的 Embedding 调用（命名断路器）。
     */
    public float[] executeEmbedding(String providerName, Supplier<float[]> call) {
        return breakerService.executeEmbedding(providerName + "-embedding", call);
    }

    // ==================== Accessors ====================

    public AiCircuitBreakerService getCircuitBreakerService() {
        return breakerService;
    }

    public List<AiProviderConfig> getChatProviders() {
        return chatProviders;
    }

    public List<AiProviderConfig> getEmbeddingProviders() {
        return embeddingProviders;
    }
}
