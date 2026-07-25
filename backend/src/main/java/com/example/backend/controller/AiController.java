package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.service.AiService;
import com.example.backend.service.AiStreamService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能问诊接口。
 * 提供两种调用模式：
 * <ul>
 *   <li>同步模式：POST /api/ai/diagnosis → 等待完整结果后返回</li>
 *   <li>流式模式：POST /api/ai/diagnosis/stream → SSE 逐 token 推送（打字机效果）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    private final AiStreamService aiStreamService;

    public AiController(AiService aiService, AiStreamService aiStreamService) {
        this.aiService = aiService;
        this.aiStreamService = aiStreamService;
    }

    /**
     * 同步问诊（兼容旧版）：接收症状描述和历史对话，返回完整 AI 分析结果。
     */
    @PostMapping("/diagnosis")
    public Result<Map<String, String>> askDiagnosis(@RequestBody Map<String, Object> request) {
        String symptoms = (String) request.get("symptoms");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) request.get("history");

        if (symptoms == null || symptoms.trim().isEmpty()) {
            return Result.error("请输入症状描述");
        }

        String aiResponse = aiService.askDiagnosis(symptoms.trim(), history);
        Map<String, String> data = new HashMap<>();
        data.put("reply", aiResponse);
        return Result.ok(data);
    }

    /**
     * 流式问诊（SSE）：接收症状描述和历史对话，通过 Server-Sent Events 逐 token 推送 AI 回复。
     *
     * <p>事件格式：
     * <pre>{@code
     *   event:token
     *   data:头痛
     *
     *   event:token
     *   data:可能
     *
     *   ...
     *
     *   event:done
     *   data:完整回复内容
     * }</pre>
     *
     * <p>前端通过 EventSource 或 fetch + ReadableStream 消费。
     * SseEmitter 超时设为 60 秒，与 LLM API 超时对齐。
     */
    @PostMapping(value = "/diagnosis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDiagnosis(@RequestBody Map<String, Object> request) {
        String symptoms = (String) request.get("symptoms");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) request.get("history");

        if (symptoms == null || symptoms.trim().isEmpty()) {
            // 参数错误时仍需通过 SseEmitter 返回，保持 SSE 协议一致
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("请输入症状描述"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // 超时 60 秒，与 AI API 的 readTimeout 对齐
        SseEmitter emitter = new SseEmitter(60_000L);

        // 注册回调：超时或异常时释放资源
        emitter.onTimeout(() -> {
            // SseEmitter 内部自动 complete，无需额外操作
        });
        emitter.onError(throwable -> {
            // SseEmitter 内部自动 complete，无需额外操作
        });

        // 新开线程处理流式调用，避免阻塞 Tomcat 线程
        aiStreamService.streamDiagnosis(symptoms.trim(), history, emitter);
        return emitter;
    }
}
