package com.example.backend.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 熔断器统一服务：对 DeepSeek API 调用施加熔断保护。
 *
 * <p>三个 CircuitBreaker 实例：
 * <ul>
 *   <li><b>deepseek-chat</b> — 同步调用（AiService、AiStructuredService 同步、ChatService 同步）</li>
 *   <li><b>deepseek-chat-stream</b> — 流式调用（AiStreamService、AiStructuredService 流式、ChatService 流式）</li>
 *   <li><b>deepseek-embedding</b> — Embedding 调用（EmbeddingService）</li>
 * </ul>
 *
 * <p>熔断器状态流转：CLOSED → OPEN（快速失败）→ HALF_OPEN（探测）→ CLOSED 或 OPEN
 */
@Service
public class AiCircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreakerService.class);

    private final CircuitBreaker chatBreaker;
    private final CircuitBreaker chatStreamBreaker;
    private final CircuitBreaker embeddingBreaker;

    /** 熔断开启时的通用降级提示 */
    private static final String FALLBACK_MSG =
            "AI 服务暂时不可用（熔断保护中），请 30 秒后重试。\n" +
            "您可以尝试以下操作：\n" +
            "1. 稍等片刻后重新发送\n" +
            "2. 前往医院就诊获取专业诊断\n" +
            "3. 紧急情况请拨打 120";

    public AiCircuitBreakerService(CircuitBreakerRegistry registry) {
        this.chatBreaker = registry.circuitBreaker("deepseek-chat");
        this.chatStreamBreaker = registry.circuitBreaker("deepseek-chat-stream");
        this.embeddingBreaker = registry.circuitBreaker("deepseek-embedding");
        log.info("AI 熔断器初始化完成: chat={}, chat-stream={}, embedding={}",
                chatBreaker.getState(), chatStreamBreaker.getState(), embeddingBreaker.getState());
    }

    // ==================== 同步调用保护 ====================

    /**
     * 带熔断保护的同步调用。
     *
     * @param call     实际 LLM 调用
     * @param fallback 熔断开启或调用失败时的降级逻辑
     * @param <T>      返回值类型
     * @return 调用结果或降级值
     */
    public <T> T executeSyncCall(Supplier<T> call, Supplier<T> fallback) {
        try {
            return chatBreaker.executeSupplier(call);
        } catch (Exception e) {
            log.warn("同步 LLM 调用失败（熔断/异常），执行降级: {}", e.getMessage());
            try {
                return fallback.get();
            } catch (Exception fe) {
                log.error("降级逻辑执行失败", fe);
                throw new RuntimeException("AI 服务不可用", fe);
            }
        }
    }

    /** 同步调用降级（使用预设降级文本） */
    @SuppressWarnings("unchecked")
    public <T> T executeSyncCall(Supplier<T> call) {
        return executeSyncCall(call, () -> (T) FALLBACK_MSG);
    }

    // ==================== 流式调用保护 ====================

    /**
     * 流式调用熔断检查：电路 OPEN 时直接通过 SSE 发送错误并返回 false。
     *
     * @param emitter SSE 发射器
     * @return true = 可以继续调用，false = 已熔断（emitter 已发送错误并关闭）
     */
    public boolean tryAcquireStreamPermission(SseEmitter emitter) {
        if (chatStreamBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("流式 LLM 熔断已开启，快速拒绝请求");
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("AI 服务暂时不可用（熔断保护中），请 30 秒后重试"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return false;
        }
        return true;
    }

    /**
     * 记录流式调用的结果到断路器。
     * <p>流式调用无法直接用 {@code executeSupplier} 包装（异步、原地操作），
     * 因此通过此方法手动告知断路器调用成功，使其正常统计失败率。
     *
     * @param success true = 成功，false = 失败
     */
    public void recordStreamResult(boolean success) {
        if (success) {
            chatStreamBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /** 手动记录流式调用异常，使断路器统计失败次数 */
    public void recordStreamException(Throwable t) {
        log.debug("记录流式调用异常到断路器: {}", t.getClass().getSimpleName());
        chatStreamBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, t);
    }

    // ==================== Embedding 保护 ====================

    /**
     * 带熔断保护的 Embedding 调用。
     *
     * @param call Embedding API 调用
     * @return float[] embedding 向量
     * @throws EmbeddingService.EmbeddingException 电路 OPEN 时抛出，由 SemanticCacheService 降级处理
     */
    public float[] executeEmbedding(Supplier<float[]> call) {
        try {
            return embeddingBreaker.executeSupplier(call);
        } catch (Exception e) {
            log.warn("Embedding 调用失败（熔断/异常），抛出 EmbeddingException 触发降级: {}", e.getMessage());
            throw new EmbeddingService.EmbeddingException(
                    "Embedding 不可用（熔断保护）: " + e.getMessage(), e);
        }
    }

    // ==================== 状态查询 ====================

    /** 获取同步断路器当前状态 */
    public CircuitBreaker.State getChatState() {
        return chatBreaker.getState();
    }

    /** 获取流式断路器当前状态 */
    public CircuitBreaker.State getChatStreamState() {
        return chatStreamBreaker.getState();
    }

    /** 获取 Embedding 断路器当前状态 */
    public CircuitBreaker.State getEmbeddingState() {
        return embeddingBreaker.getState();
    }
}
