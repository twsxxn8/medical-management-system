package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.AiDiagnosisRequest;
import com.example.backend.dto.DiagnosisResult;
import com.example.backend.service.AiService;
import com.example.backend.service.AiStructuredService;
import com.example.backend.service.AiStreamService;
import com.example.backend.service.SemanticCacheService;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能问诊接口，提供四种调用模式：
 * <ul>
 *   <li>同步文本：POST /api/ai/diagnosis → 等待完整文本结果</li>
 *   <li>流式文本：POST /api/ai/diagnosis/stream → SSE 逐 token 推送</li>
 *   <li>同步结构化：POST /api/ai/diagnosis/structured → 返回 JSON Schema 约束的结构化 JSON</li>
 *   <li>流式结构化：POST /api/ai/diagnosis/structured/stream → SSE 逐 token + 最终完整结构</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final AiStreamService aiStreamService;
    private final AiStructuredService aiStructuredService;
    private final SemanticCacheService semanticCacheService;

    public AiController(AiService aiService, AiStreamService aiStreamService,
                        AiStructuredService aiStructuredService,
                        SemanticCacheService semanticCacheService) {
        this.aiService = aiService;
        this.aiStreamService = aiStreamService;
        this.aiStructuredService = aiStructuredService;
        this.semanticCacheService = semanticCacheService;
    }

    // ==================== 文本模式（兼容旧版） ====================

    /**
     * 同步文本问诊：接收症状描述和历史对话，返回完整文本结果。
     */
    @PostMapping("/diagnosis")
    public Result<Map<String, String>> askDiagnosis(@Valid @RequestBody AiDiagnosisRequest request) {
        String aiResponse = aiService.askDiagnosis(
                request.getSymptoms().trim(),
                request.getHistory()
        );
        Map<String, String> data = new HashMap<>();
        data.put("reply", aiResponse);
        return Result.ok(data);
    }

    /**
     * SSE 流式文本问诊：逐 token 推送 AI 回复。
     */
    @PostMapping(value = "/diagnosis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDiagnosis(@Valid @RequestBody AiDiagnosisRequest request) {
        String symptoms = request.getSymptoms().trim();
        SseEmitter emitter = new SseEmitter(60_000L);
        aiStreamService.streamDiagnosis(symptoms, request.getHistory(), emitter);
        return emitter;
    }

    // ==================== 结构化模式（JSON Schema） ====================

    /**
     * 同步结构化问诊：使用 JSON Schema 约束 LLM 输出，返回结构化的 {@link DiagnosisResult}。
     * 支持语义缓存：相似症状命中缓存时直接返回，减少 LLM API 调用。
     */
    @PostMapping("/diagnosis/structured")
    public Result<DiagnosisResult> askDiagnosisStructured(
            @Valid @RequestBody AiDiagnosisRequest request,
            HttpServletResponse response) {
        String symptoms = request.getSymptoms().trim();

        // 1. 检查语义缓存
        SemanticCacheService.CacheResult cacheResult = semanticCacheService.check(symptoms);

        if (cacheResult.isHit()) {
            response.setHeader("X-Cache", "HIT");
            response.setHeader("X-Cache-Similarity",
                    String.format("%.4f", cacheResult.getSimilarityScore()));
            return Result.ok(cacheResult.getData().getResult());
        }

        // 2. 缓存未命中：调用 LLM
        response.setHeader("X-Cache", "MISS");
        DiagnosisResult result = aiStructuredService.askDiagnosisStructured(
                symptoms, request.getHistory());

        // 3. 存入缓存（最佳努力，失败不影响响应）
        semanticCacheService.store(symptoms, result);

        return Result.ok(result);
    }

    /**
     * SSE 流式结构化问诊：逐 token 推送 + onDone 返回完整结构化 JSON。
     * 支持语义缓存：命中时直接返回缓存的 DiagnosisResult。
     */
    @PostMapping(value = "/diagnosis/structured/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStructuredDiagnosis(@RequestBody AiDiagnosisRequest request) {
        if (request.getSymptoms() == null || request.getSymptoms().trim().isEmpty()) {
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("请输入症状描述"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        semanticCacheService.checkAndStreamOrDelegate(
                request.getSymptoms().trim(),
                request.getHistory(),
                emitter
        );
        return emitter;
    }
}
