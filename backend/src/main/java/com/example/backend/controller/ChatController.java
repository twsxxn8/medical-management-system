package com.example.backend.controller;

import com.example.backend.Entity.ChatMessageEntity;
import com.example.backend.common.Result;
import com.example.backend.dto.ChatConversationResponse;
import com.example.backend.dto.ChatRequest;
import com.example.backend.service.ChatService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 聊天（多轮对话）接口。
 * <ul>
 *   <li>同步聊天：POST /api/ai/chat</li>
 *   <li>流式聊天：POST /api/ai/chat/stream → SSE</li>
 *   <li>对话列表：GET /api/ai/conversations</li>
 *   <li>删除对话：DELETE /api/ai/conversations/{id}</li>
 *   <li>获取消息：GET /api/ai/conversations/{id}/messages</li>
 * </ul>
 * <p>
 * 与现有 {@link AiController} 独立运行，共享 /api/ai/** 路径的 JWT 认证和全局限流。
 */
@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ==================== 聊天 ====================

    /**
     * 同步聊天（后备方案）：阻塞等待完整 AI 回复。
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@Valid @RequestBody ChatRequest request) {
        Long userId = getUserId();
        Map<String, Object> result = chatService.chatSync(
                userId,
                request.getConversationId(),
                request.getMessage().trim()
        );
        return Result.ok(result);
    }

    /**
     * SSE 流式聊天：逐 token 推送 AI 回复（主要接口）。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        // 手动校验（与 AiController.streamStructuredDiagnosis 一致）
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("消息内容不能为空"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        Long userId = getUserId();
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.streamChat(userId, request.getConversationId(),
                request.getMessage().trim(), emitter);
        return emitter;
    }

    // ==================== 对话管理 ====================

    /** 获取当前用户的对话列表 */
    @GetMapping("/conversations")
    public Result<List<ChatConversationResponse>> listConversations() {
        Long userId = getUserId();
        return Result.ok(chatService.listConversations(userId));
    }

    /** 删除指定对话 */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        Long userId = getUserId();
        chatService.deleteConversation(userId, id);
        return Result.ok();
    }

    /** 获取指定对话的所有消息 */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<Map<String, Object>>> getMessages(@PathVariable Long id) {
        Long userId = getUserId();
        List<ChatMessageEntity> messages = chatService.getConversationMessages(userId, id);
        List<Map<String, Object>> result = messages.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("role", m.getRole());
            map.put("content", m.getContent());
            map.put("createTime", m.getCreateTime() != null ? m.getCreateTime().toString() : null);
            return map;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    // ==================== 辅助方法 ====================

    /** 从 SecurityContextHolder 获取当前用户 ID（镜像 AiController 中的模式） */
    private Long getUserId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
