package com.example.backend.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 熔断器统一服务：对 LLM API 调用施加熔断保护。
 *
 * <p>CircuitBreaker 按 {@code {providerName}-{type}} 命名：
 * <ul>
 *   <li>{@code {name}-chat} — 同步调用</li>
 *   <li>{@code {name}-chat-stream} — 流式调用</li>
 *   <li>{@code {name}-embedding} — Embedding 调用</li>
 * </ul>
 *
 * <p>所有方法提供两个重载：
 * <ol>
 *   <li>带 {@code breakerName} 参数 —— {@link AiProviderRouter} 使用，支持多 provider</li>
 *   <li>无参版本 —— 委托到 {@code "deepseek-*"} breaker，向后兼容</li>
 * </ol>
 */
@Service
public class AiCircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreakerService.class);

    private final CircuitBreakerRegistry registry;

    /** 熔断开启时的通用降级提示 */
    private static final String FALLBACK_MSG =
            "AI 服务暂时不可用（熔断保护中），请 30 秒后重试。\n" +
            "您可以尝试以下操作：\n" +
            "1. 稍等片刻后重新发送\n" +
            "2. 前往医院就诊获取专业诊断\n" +
            "3. 紧急情况请拨打 120";

    public AiCircuitBreakerService(CircuitBreakerRegistry registry) {
        this.registry = registry;
        log.info("AI 熔断器注册表初始化完成，已注册实例: {}",
                registry.getAllCircuitBreakers().size());
    }

    // ==================== 同步调用保护 ====================

    /** 带熔断保护的同步调用（命名 breaker）。 */
    public <T> T executeSyncCall(String breakerName, Supplier<T> call, Supplier<T> fallback) {
        try {
            return registry.circuitBreaker(breakerName).executeSupplier(call);
        } catch (Exception e) {
            log.warn("同步 LLM 调用失败（熔断/异常）[{}]，执行降级: {}",
                    breakerName, e.getMessage());
            try {
                return fallback.get();
            } catch (Exception fe) {
                log.error("降级逻辑执行失败", fe);
                throw new RuntimeException("AI 服务不可用", fe);
            }
        }
    }

    /** 带熔断保护的同步调用（向后兼容：使用 "deepseek-chat"）。 */
    public <T> T executeSyncCall(Supplier<T> call, Supplier<T> fallback) {
        return executeSyncCall("deepseek-chat", call, fallback);
    }

    /** 同步调用降级（使用预设降级文本，向后兼容）。 */
    @SuppressWarnings("unchecked")
    public <T> T executeSyncCall(Supplier<T> call) {
        return executeSyncCall("deepseek-chat", call, () -> (T) FALLBACK_MSG);
    }

    // ==================== 流式调用保护 ====================

    /**
     * 流式调用熔断检查：电路 OPEN 时直接通过 SSE 发送错误并返回 false。
     *
     * @param breakerName 断路器名称（如 "deepseek-chat-stream"）
     * @param emitter     SSE 发射器
     * @return true = 可以继续调用，false = 已熔断
     */
    public boolean tryAcquireStreamPermission(String breakerName, SseEmitter emitter) {
        if (registry.circuitBreaker(breakerName).getState() == CircuitBreaker.State.OPEN) {
            log.warn("流式 LLM 熔断已开启 [{}]，快速拒绝请求", breakerName);
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

    /** 流式调用熔断检查（向后兼容：使用 "deepseek-chat-stream"）。 */
    public boolean tryAcquireStreamPermission(SseEmitter emitter) {
        return tryAcquireStreamPermission("deepseek-chat-stream", emitter);
    }

    /** 记录流式调用成功（命名 breaker）。 */
    public void recordStreamResult(String breakerName, boolean success) {
        if (success) {
            registry.circuitBreaker(breakerName)
                    .onSuccess(0, TimeUnit.NANOSECONDS);
        }
    }

    /** 记录流式调用成功（向后兼容）。 */
    public void recordStreamResult(boolean success) {
        recordStreamResult("deepseek-chat-stream", success);
    }

    /** 记录流式调用异常（命名 breaker）。 */
    public void recordStreamException(String breakerName, Throwable t) {
        log.debug("记录流式调用异常到断路器 [{}]: {}", breakerName, t.getClass().getSimpleName());
        registry.circuitBreaker(breakerName)
                .onError(0, TimeUnit.NANOSECONDS, t);
    }

    /** 记录流式调用异常（向后兼容）。 */
    public void recordStreamException(Throwable t) {
        recordStreamException("deepseek-chat-stream", t);
    }

    // ==================== Embedding 保护 ====================

    /**
     * 带熔断保护的 Embedding 调用。
     *
     * @param breakerName 断路器名称（如 "deepseek-embedding"）
     * @param call        Embedding API 调用
     * @return float[] embedding 向量
     * @throws EmbeddingService.EmbeddingException 电路 OPEN 时抛出
     */
    public float[] executeEmbedding(String breakerName, Supplier<float[]> call) {
        try {
            return registry.circuitBreaker(breakerName).executeSupplier(call);
        } catch (Exception e) {
            log.warn("Embedding 调用失败（熔断/异常）[{}]: {}", breakerName, e.getMessage());
            throw new EmbeddingService.EmbeddingException(
                    "Embedding 不可用（熔断保护）: " + e.getMessage(), e);
        }
    }

    /** 带熔断保护的 Embedding 调用（向后兼容：使用 "deepseek-embedding"）。 */
    public float[] executeEmbedding(Supplier<float[]> call) {
        return executeEmbedding("deepseek-embedding", call);
    }

    // ==================== 状态查询 ====================

    /** 获取指定断路器的当前状态。 */
    public CircuitBreaker.State getState(String breakerName) {
        return registry.circuitBreaker(breakerName).getState();
    }

    /** 获取同步断路器状态（向后兼容）。 */
    public CircuitBreaker.State getChatState() {
        return getState("deepseek-chat");
    }

    /** 获取流式断路器状态（向后兼容）。 */
    public CircuitBreaker.State getChatStreamState() {
        return getState("deepseek-chat-stream");
    }

    /** 获取 Embedding 断路器状态（向后兼容）。 */
    public CircuitBreaker.State getEmbeddingState() {
        return getState("deepseek-embedding");
    }

    /** 获取断路器注册表（供 Router 使用）。 */
    public CircuitBreakerRegistry getRegistry() {
        return registry;
    }
}
