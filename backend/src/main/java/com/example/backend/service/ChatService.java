package com.example.backend.service;

import com.example.backend.Entity.ChatConversationEntity;
import com.example.backend.Entity.ChatMessageEntity;
import com.example.backend.dto.ChatConversationResponse;
import com.example.backend.repository.ChatConversationRepository;
import com.example.backend.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 聊天服务：多轮对话的 CRUD + SSE 流式聊天。
 *
 * <p>核心设计：
 * <ul>
 *   <li>流式调用复用 {@link HttpURLConnection} 模式（与 {@link AiStreamService} 一致），
 *       避免 WebFlux 与 Spring MVC 冲突</li>
 *   <li>对话和消息持久化到 DB，刷新页面不丢失</li>
 *   <li>上下文限制为最近 40 条历史消息，防止 Token 超限</li>
 *   <li>DB 操作为最佳努力（fail-soft），不阻塞对话流</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.api-key}")
    private String apiKey;

    @Value("${app.ai.api-url}")
    private String apiUrl;

    @Value("${app.ai.model}")
    private String model;

    private final ChatConversationRepository conversationRepo;
    private final ChatMessageRepository messageRepo;
    private final AiCircuitBreakerService circuitBreakerService;

    /** 系统提示词 */
    private static final String CHAT_SYSTEM_PROMPT =
            "你是一位专业的智能医疗助手。你可以回答各类医疗健康相关问题，" +
            "包括但不限于：症状分析、疾病咨询、用药参考、健康生活方式建议、" +
            "检查报告解读、就医指导等。\n" +
            "请注意：\n" +
            "1. 你的回答仅供参考，不构成医疗诊断\n" +
            "2. 紧急情况应建议用户立即就医\n" +
            "3. 回答应专业、准确、易懂\n" +
            "4. 如有不确定的问题，诚实告知用户并建议咨询专业医生";

    /** 上下文最大消息条数 */
    private static final int MAX_CONTEXT_MESSAGES = 40;

    /** 超出上下文时的提示文本 */
    private static final String TRUNCATION_NOTICE =
            "（注意：对话历史较长，早期内容已被截断）";

    public ChatService(ChatConversationRepository conversationRepo,
                       ChatMessageRepository messageRepo,
                       AiCircuitBreakerService circuitBreakerService) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.circuitBreakerService = circuitBreakerService;
    }

    // ==================== 对话 CRUD ====================

    /** 获取用户的对话列表（按更新时间倒序） */
    public List<ChatConversationResponse> listConversations(Long userId) {
        List<ChatConversationEntity> conversations =
                conversationRepo.findByUserIdOrderByUpdateTimeDesc(userId);

        List<ChatConversationResponse> result = new ArrayList<>();
        for (ChatConversationEntity conv : conversations) {
            ChatConversationResponse resp = new ChatConversationResponse();
            resp.setId(conv.getId());
            resp.setTitle(conv.getTitle());
            resp.setCreateTime(conv.getCreateTime());
            resp.setUpdateTime(conv.getUpdateTime());

            // 获取最后一条消息作为摘要
            try {
                List<ChatMessageEntity> messages =
                        messageRepo.findByConversationIdOrderByCreateTimeAsc(conv.getId());
                if (messages != null && !messages.isEmpty()) {
                    ChatMessageEntity last = messages.get(messages.size() - 1);
                    String content = last.getContent();
                    if (content != null && content.length() > 80) {
                        content = content.substring(0, 80) + "...";
                    }
                    resp.setLastMessage(content);
                }
            } catch (Exception e) {
                log.debug("获取对话摘要失败: {}", e.getMessage());
            }

            result.add(resp);
        }
        return result;
    }

    /** 删除指定对话（需验证归属权） */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        try {
            conversationRepo.deleteByIdAndUserId(conversationId, userId);
        } catch (Exception e) {
            throw new RuntimeException("会话不存在");
        }
    }

    /** 获取对话的所有消息（需验证归属权） */
    public List<ChatMessageEntity> getConversationMessages(Long userId, Long conversationId) {
        // 验证归属权
        ChatConversationEntity conv = conversationRepo.findById(conversationId).orElse(null);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new RuntimeException("会话不存在");
        }
        return messageRepo.findByConversationIdOrderByCreateTimeAsc(conversationId);
    }

    // ==================== 流式聊天（核心） ====================

    /**
     * SSE 流式聊天：逐 token 推送 AI 回复，并持久化对话与消息。
     *
     * @param userId         当前用户 ID
     * @param conversationId 对话 ID（null 时自动创建新对话）
     * @param message        用户输入的消息文本
     * @param emitter        SSE 发射器
     */
    public void streamChat(Long userId, Long conversationId, String message,
                           SseEmitter emitter) {
        // 熔断检查：电路 OPEN 时快速失败
        if (!circuitBreakerService.tryAcquireStreamPermission(emitter)) {
            return;
        }

        ChatConversationEntity conversation;
        List<ChatMessageEntity> historyMessages;

        // 1. 加载或创建对话
        if (conversationId != null) {
            conversation = conversationRepo.findById(conversationId).orElse(null);
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("会话不存在"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
                return;
            }
            historyMessages = messageRepo.findByConversationIdOrderByCreateTimeAsc(conversationId);
        } else {
            conversation = new ChatConversationEntity();
            conversation.setUserId(userId);
            // 自动用消息前 50 字符作为标题
            String title = message.length() > 50 ? message.substring(0, 50) : message;
            conversation.setTitle(title);
            conversation.setCreateTime(LocalDateTime.now());
            conversation.setUpdateTime(LocalDateTime.now());
            conversation = conversationRepo.save(conversation);
            conversationId = conversation.getId();
            historyMessages = new ArrayList<>();
        }

        // 2. 存储用户消息
        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(message);
        userMessage.setCreateTime(LocalDateTime.now());
        messageRepo.save(userMessage);

        // 3. 更新会话时间
        try {
            conversation.setUpdateTime(LocalDateTime.now());
            conversationRepo.save(conversation);
        } catch (Exception e) {
            log.debug("更新会话时间失败: {}", e.getMessage());
        }

        // 4. 构建 LLM 消息上下文
        List<Map<String, String>> messages = buildMessages(historyMessages, message);

        // 5. 调用 DeepSeek API 流式
        HttpURLConnection connection = null;
        final Long finalConversationId = conversationId;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(120_000);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);
            requestBody.put("stream", true);
            requestBody.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("AI 服务返回错误，状态码：" + responseCode));
                emitter.complete();
                return;
            }

            // 6. 逐 token 解析 + 推送
            StringBuilder fullContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;

                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> chunk = objectMapper.readValue(data, Map.class);

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices =
                                (List<Map<String, Object>>) chunk.get("choices");

                        if (choices == null || choices.isEmpty()) continue;

                        Map<String, Object> delta =
                                (Map<String, Object>) choices.get(0).get("delta");
                        if (delta == null) continue;

                        String content = (String) delta.get("content");
                        if (content != null && !content.isEmpty()) {
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(content));
                            fullContent.append(content);
                        }

                        String finishReason = (String) choices.get(0).get("finish_reason");
                        if (finishReason != null) break;

                    } catch (Exception e) {
                        log.debug("解析 SSE chunk 失败: {}", data, e);
                    }
                }
            }

            // 7. 存储 assistant 消息
            String reply = fullContent.toString();
            try {
                ChatMessageEntity assistantMessage = new ChatMessageEntity();
                assistantMessage.setConversationId(finalConversationId);
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(reply);
                assistantMessage.setCreateTime(LocalDateTime.now());
                messageRepo.save(assistantMessage);
            } catch (Exception e) {
                log.warn("存储 assistant 消息失败: {}", e.getMessage());
            }

            // 8. 发送完成事件（携带 conversationId）
            Map<String, Object> doneData = new LinkedHashMap<>();
            doneData.put("conversationId", finalConversationId);
            doneData.put("reply", reply);
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(objectMapper.writeValueAsString(doneData)));

            emitter.complete();

        } catch (Exception e) {
            log.error("SSE 流式聊天失败", e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("AI 服务调用失败：" + e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ==================== 同步后备 ====================

    /**
     * 同步聊天（后备方案）：阻塞等待完整 AI 回复。
     * 使用与流式版本相同的 DB 持久化逻辑。
     *
     * @return 包含 conversationId 和 reply 的 Map
     */
    @Transactional
    public Map<String, Object> chatSync(Long userId, Long conversationId, String message) {
        ChatConversationEntity conversation;
        List<ChatMessageEntity> historyMessages;

        if (conversationId != null) {
            conversation = conversationRepo.findById(conversationId).orElse(null);
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new RuntimeException("会话不存在");
            }
            historyMessages = messageRepo.findByConversationIdOrderByCreateTimeAsc(conversationId);
        } else {
            conversation = new ChatConversationEntity();
            conversation.setUserId(userId);
            String title = message.length() > 50 ? message.substring(0, 50) : message;
            conversation.setTitle(title);
            conversation.setCreateTime(LocalDateTime.now());
            conversation.setUpdateTime(LocalDateTime.now());
            conversation = conversationRepo.save(conversation);
            conversationId = conversation.getId();
            historyMessages = new ArrayList<>();
        }

        // 存储用户消息
        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(message);
        userMessage.setCreateTime(LocalDateTime.now());
        messageRepo.save(userMessage);

        // 构建消息
        List<Map<String, String>> messages = buildMessages(historyMessages, message);

        // 同步调用 LLM（带熔断保护）
        String reply = circuitBreakerService.executeSyncCall(
                () -> callSyncApi(messages),
                () -> "AI 服务暂时不可用（熔断保护中），请 30 秒后重试。\n"
                    + "您可以尝试以下操作：\n"
                    + "1. 稍等片刻后重新发送\n"
                    + "2. 前往医院就诊获取专业诊断\n"
                    + "3. 紧急情况请拨打 120"
        );

        // 存储 assistant 消息
        ChatMessageEntity assistantMessage = new ChatMessageEntity();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(reply);
        assistantMessage.setCreateTime(LocalDateTime.now());
        messageRepo.save(assistantMessage);

        // 更新会话时间
        conversation.setUpdateTime(LocalDateTime.now());
        conversationRepo.save(conversation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", reply);
        return result;
    }

    /** 同步调用 DeepSeek API，返回完整回复文本 */
    private String callSyncApi(List<Map<String, String>> messages) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);
            requestBody.put("stream", false);
            requestBody.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return "AI 服务返回错误，状态码：" + responseCode;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return extractContent(sb.toString());
            }
        } catch (Exception e) {
            log.error("同步调用 AI 失败", e);
            return "AI 服务调用失败：" + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 从 OpenAI 兼容响应中提取 content */
    @SuppressWarnings("unchecked")
    private String extractContent(String json) {
        try {
            Map<String, Object> resp = objectMapper.readValue(json, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    String content = (String) message.get("content");
                    return content != null ? content : "";
                }
            }
        } catch (Exception e) {
            log.debug("解析 AI 响应失败: {}", e.getMessage());
        }
        return "";
    }

    // ==================== 工具方法 ====================

    /** 构建 LLM 消息数组：system + 历史（截断至最近 40 条）+ 新 user 消息 */
    private List<Map<String, String>> buildMessages(List<ChatMessageEntity> historyMessages,
                                                    String newUserMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", CHAT_SYSTEM_PROMPT);
        messages.add(systemMsg);

        // 历史消息（仅 user/assistant），截断至最近 MAX_CONTEXT_MESSAGES 条
        int start = 0;
        if (historyMessages.size() > MAX_CONTEXT_MESSAGES) {
            start = historyMessages.size() - MAX_CONTEXT_MESSAGES;
            log.debug("对话上下文截断: {} → {} 条", historyMessages.size(), MAX_CONTEXT_MESSAGES);
        }
        for (int i = start; i < historyMessages.size(); i++) {
            ChatMessageEntity msg = historyMessages.get(i);
            String role = msg.getRole();
            if ("user".equals(role) || "assistant".equals(role)) {
                Map<String, String> m = new HashMap<>();
                m.put("role", role);

                // 对截断后的首条 assistant 消息添加截断提示
                String content = msg.getContent();
                if (i == start && start > 0 && "assistant".equals(role)) {
                    content = TRUNCATION_NOTICE + "\n\n" + content;
                }
                m.put("content", content);
                messages.add(m);
            }
        }

        // 新 user 消息（已经在 DB 中，这里作为当前轮次输入）
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", newUserMessage);
        messages.add(userMsg);

        return messages;
    }
}
