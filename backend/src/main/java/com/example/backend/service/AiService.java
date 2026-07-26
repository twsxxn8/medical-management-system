package com.example.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * AI 大模型服务类：调用 DeepSeek API 实现智能问诊功能。
 */
@Service
public class AiService {

    @Value("${app.ai.api-key}")
    private String apiKey;

    @Value("${app.ai.api-url}")
    private String apiUrl;

    @Value("${app.ai.model}")
    private String model;

    /**
     * RestTemplate with explicit timeouts to prevent infinite blocking.
     */
    private final RestTemplate restTemplate;

    private final AiCircuitBreakerService circuitBreakerService;

    public AiService(AiCircuitBreakerService circuitBreakerService) {
        this.circuitBreakerService = circuitBreakerService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * AI 智能问诊：根据患者描述的症状及历史对话，返回初步分析和建议科室。
     */
    public String askDiagnosis(String symptoms, List<Map<String, String>> history) {
        final String systemPrompt = "你是一位专业的医疗咨询助手。请根据患者描述的症状，提供以下内容：\n" +
                "1. 可能的疾病方向（不超过3个）\n" +
                "2. 建议的检查项目\n" +
                "3. 建议就诊的科室\n" +
                "4. 日常注意事项\n" +
                "注意：你的回答仅供参考，不构成医疗诊断，建议患者及时就医。回答要简洁专业。";

        final String userMessage = symptoms;
        final List<Map<String, String>> finalHistory = history != null ? history : new ArrayList<>();

        return circuitBreakerService.executeSyncCall(
                () -> callChatApi(systemPrompt, userMessage, finalHistory),
                () -> "AI 服务暂时不可用（熔断保护中），请 30 秒后重试。\n"
                    + "您可以尝试以下操作：\n"
                    + "1. 稍等片刻后重新发送\n"
                    + "2. 前往医院就诊获取专业诊断\n"
                    + "3. 紧急情况请拨打 120"
        );
    }

    /**
     * 调用大模型 Chat API（未受熔断保护，由 askDiagnosis 包装调用）
     */
    @SuppressWarnings("unchecked")
    private String callChatApi(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");
            if (role != null && content != null && ("user".equals(role) || "assistant".equals(role))) {
                Map<String, String> historyMsg = new HashMap<>();
                historyMsg.put("role", role);
                historyMsg.put("content", content);
                messages.add(historyMsg);
            }
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        Map<String, Object> response = restTemplate.postForObject(apiUrl, entity, Map.class);

        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
        }
        throw new RuntimeException("AI 服务返回数据异常"); // 触发熔断计数
    }
}
