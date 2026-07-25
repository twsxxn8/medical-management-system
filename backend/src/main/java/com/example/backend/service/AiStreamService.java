package com.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 流式响应服务：通过 SSE 将 LLM 生成的 token 实时推送至前端。
 *
 * <p>核心设计：
 * <ul>
 *   <li>使用原生 HttpURLConnection（避免 WebFlux 与 Spring MVC 冲突）直连 LLM API，
 *       开启 {@code stream: true}，逐行读取 SSE chunk</li>
 *   <li>每收到一个 delta token，立即通过 {@link SseEmitter#send(Object)} 推送给前端，
 *       实现打字机效果</li>
 *   <li>前端断开连接（如刷新页面）时，捕获 IOException 后关闭上游 HTTP 连接，
 *       避免资源泄露</li>
 *   <li>超时时间设为 60 秒，兼容长回复场景</li>
 * </ul>
 */
@Service
public class AiStreamService {

    private static final Logger log = LoggerFactory.getLogger(AiStreamService.class);

    @Value("${app.ai.api-key}")
    private String apiKey;

    @Value("${app.ai.api-url}")
    private String apiUrl;

    @Value("${app.ai.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * SSE 流式问诊：将 LLM token 级别输出逐条发送到 SseEmitter。
     *
     * @param symptoms 患者症状描述
     * @param history  多轮对话历史
     * @param emitter  SSE 发射器
     */
    public void streamDiagnosis(String symptoms, List<Map<String, String>> history, SseEmitter emitter) {
        String systemPrompt = "你是一位专业的医疗咨询助手。请根据患者描述的症状，提供以下内容：\n" +
                "1. 可能的疾病方向（不超过3个）\n" +
                "2. 建议的检查项目\n" +
                "3. 建议就诊的科室\n" +
                "4. 日常注意事项\n" +
                "注意：你的回答仅供参考，不构成医疗诊断，建议患者及时就医。回答要简洁专业。";

        streamChatApi(systemPrompt, symptoms, history != null ? history : new ArrayList<>(), emitter);
    }

    /**
     * 调用 LLM Chat Completion API（stream 模式），逐 token 转发至 SseEmitter。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  当前用户输入
     * @param history      历史对话
     * @param emitter      SSE 发射器
     */
    private void streamChatApi(String systemPrompt, String userMessage,
                               List<Map<String, String>> history, SseEmitter emitter) {
        HttpURLConnection connection = null;
        try {
            // 1. 建立 HTTP 连接（流式）
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);  // 流式长连接

            // 2. 构造请求体（stream: true）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);
            requestBody.put("stream", true);     // 关键：开启流式

            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (role != null && content != null && ("user".equals(role) || "assistant".equals(role))) {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", role);
                    m.put("content", content);
                    messages.add(m);
                }
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 3. 发送请求
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 4. 读取流式响应：逐行解析 SSE 格式 → 提取 delta content → send 给前端
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("AI 服务返回错误，状态码：" + responseCode));
                emitter.complete();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                StringBuilder fullContent = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    // SSE 格式: "data: {...}"
                    if (!line.startsWith("data: ")) {
                        continue;
                    }

                    String data = line.substring(6).trim();

                    // [DONE] 信号，流结束
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> chunk = objectMapper.readValue(data, Map.class);

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices =
                                (List<Map<String, Object>>) chunk.get("choices");

                        if (choices == null || choices.isEmpty()) {
                            continue;
                        }

                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta == null) {
                            continue;
                        }

                        String content = (String) delta.get("content");
                        if (content != null && !content.isEmpty()) {
                            // 逐 token 发送
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(content));
                            fullContent.append(content);
                        }

                        // 检查是否该条消息已结束
                        String finishReason = (String) choices.get(0).get("finish_reason");
                        if (finishReason != null) {
                            break;
                        }

                    } catch (Exception e) {
                        log.debug("解析 SSE chunk 失败: {}", data, e);
                    }
                }

                // 5. 发送完成事件（携带完整回复，供前端回显）
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(fullContent.toString()));
            }

            emitter.complete();

        } catch (Exception e) {
            log.error("SSE 流式调用失败", e);
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
}
