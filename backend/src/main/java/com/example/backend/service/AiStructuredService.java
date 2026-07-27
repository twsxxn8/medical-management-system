package com.example.backend.service;

import com.example.backend.config.AiProviderConfig;
import com.example.backend.dto.DiagnosisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 结构化问诊服务：通过 response_format: json_object 约束 LLM 输出合法 JSON。
 *
 * <p>通过 {@link AiProviderRouter} 实现多 provider 路由和 failover。
 */
@Service
public class AiStructuredService {

    private static final Logger log = LoggerFactory.getLogger(AiStructuredService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, false);

    private final AiProviderRouter providerRouter;

    private static final String SYSTEM_PROMPT =
            "你是一位专业的医疗咨询助手。请根据患者描述的症状，给出结构化的分析结果，以 JSON 格式返回。\n" +
            "你必须返回严格的 JSON 对象，字段说明如下：\n" +
            "- possible_diseases: 疾病方向数组，每个元素包含 name(疾病名称)、probability(可能性：高/中/低)、description(简要说明)\n" +
            "- recommended_checks: 建议检查项目，字符串数组\n" +
            "- recommended_department: 建议就诊科室，字符串\n" +
            "- urgency: 紧急程度，取值：立即就医/尽快就诊/可观察\n" +
            "- precautions: 日常注意事项，字符串数组\n" +
            "- note: 补充说明（可选），字符串\n" +
            "注意：你的回答仅供参考，不构成医疗诊断，建议患者及时就医。";

    public AiStructuredService(AiProviderRouter providerRouter) {
        this.providerRouter = providerRouter;
    }

    /** 同步结构化（带熔断保护 + multi-provider failover） */
    public DiagnosisResult askDiagnosisStructured(String symptoms, List<Map<String, String>> history) {
        String json = providerRouter.executeChatSync(
                provider -> callApi(provider, symptoms, history, false),
                () -> "{\"error\":\"所有 AI 服务暂时不可用（熔断保护中）\"}"
        );
        try {
            return objectMapper.readValue(json, DiagnosisResult.class);
        } catch (Exception e) {
            log.error("解析结构化 JSON 失败: {}", json, e);
            DiagnosisResult fallback = new DiagnosisResult();
            fallback.setRecommendedDepartment("请稍后重试");
            fallback.setUrgency("可观察");
            fallback.setNote("AI 返回格式异常：" + e.getMessage());
            fallback.setPossibleDiseases(List.of());
            fallback.setRecommendedChecks(List.of());
            fallback.setPrecautions(List.of());
            return fallback;
        }
    }

    /** SSE 流式结构化（带熔断保护 + multi-provider failover） */
    public void streamStructuredDiagnosis(String symptoms, List<Map<String, String>> history,
                                          SseEmitter emitter) {
        AiProviderConfig provider = providerRouter.acquireStreamProvider(emitter);
        if (provider == null) {
            return; // all OPEN, error already sent via emitter
        }

        final List<Map<String, String>> safeHistory = new ArrayList<>();
        if (history != null) safeHistory.addAll(history);

        HttpURLConnection connection = null;
        boolean success = false;
        try {
            URL url = new URL(provider.getApiUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            connection.setDoOutput(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);

            String jsonBody = objectMapper.writeValueAsString(
                    buildRequestBody(provider, symptoms, safeHistory, true));
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("AI 服务返回错误，状态码：" + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder full = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                        if (choices == null || choices.isEmpty()) continue;
                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta == null) continue;
                        String content = (String) delta.get("content");
                        if (content != null && !content.isEmpty()) {
                            emitter.send(SseEmitter.event().name("token").data(content));
                            full.append(content);
                        }
                        if (choices.get(0).get("finish_reason") != null) break;
                    } catch (Exception e) {
                        log.debug("解析 SSE chunk 失败: {}", data, e);
                    }
                }
                try {
                    DiagnosisResult r = objectMapper.readValue(full.toString(), DiagnosisResult.class);
                    emitter.send(SseEmitter.event().name("done")
                            .data(objectMapper.writeValueAsString(r)));
                } catch (Exception e) {
                    log.error("结构化 JSON 反序列化失败: {}", full, e);
                    emitter.send(SseEmitter.event().name("error")
                            .data("JSON 解析失败：" + e.getMessage()));
                }
            }
            emitter.complete();
            success = true;
        } catch (Exception e) {
            log.error("SSE 结构化调用失败", e);
            providerRouter.recordStreamException(provider.getName(), e);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("AI 服务调用失败：" + e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        } finally {
            if (success) {
                providerRouter.recordStreamResult(provider.getName(), true);
            }
            if (connection != null) connection.disconnect();
        }
    }

    /** 同步 HTTP 调用 */
    private String callApi(AiProviderConfig provider, String symptoms,
                           List<Map<String, String>> history, boolean stream) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(provider.getApiUrl());
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            c.setDoOutput(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(30_000);
            String body = objectMapper.writeValueAsString(
                    buildRequestBody(provider, symptoms,
                            history != null ? history : new ArrayList<>(), stream));
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            if (c.getResponseCode() != 200) {
                throw new RuntimeException("AI 服务返回错误，状态码：" + c.getResponseCode());
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = objectMapper.readValue(sb.toString(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    if (msg != null) return (String) msg.get("content");
                }
            }
            throw new RuntimeException("AI 服务返回为空");
        } catch (Exception e) {
            log.error("结构化 API 调用失败", e);
            throw new RuntimeException("API调用失败:" + e.getMessage(), e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 构造请求体 */
    private Map<String, Object> buildRequestBody(AiProviderConfig provider, String symptoms,
                                                  List<Map<String, String>> history,
                                                  boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", provider.getModel());
        body.put("temperature", 0.7);
        body.put("max_tokens", 1200);
        if (stream) body.put("stream", true);
        body.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        messages.add(sys);

        if (history != null) {
            for (Map<String, String> msg : history) {
                if (msg == null) continue;
                String role = msg.get("role");
                String content = msg.get("content");
                if (role != null && content != null
                        && ("user".equals(role) || "assistant".equals(role))) {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", role);
                    m.put("content", content);
                    messages.add(m);
                }
            }
        }

        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", symptoms);
        messages.add(user);
        body.put("messages", messages);
        return body;
    }
}
