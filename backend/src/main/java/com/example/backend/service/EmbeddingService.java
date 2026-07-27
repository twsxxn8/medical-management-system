package com.example.backend.service;

import com.example.backend.config.AiProviderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Embedding 向量化服务。
 *
 * <p>调用 OpenAI 兼容 Embedding API，将文本转为 float[] 向量。
 * 通过 {@link AiProviderRouter} 选择最优可用 provider。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AiProviderRouter providerRouter;

    public EmbeddingService(AiProviderRouter providerRouter) {
        this.providerRouter = providerRouter;
    }

    /**
     * 获取文本的 embedding 向量。
     *
     * @param text 待向量化的文本
     * @return float[] 向量
     * @throws EmbeddingException 调用失败时抛出，调用方应降级处理
     */
    public float[] getEmbedding(String text) throws EmbeddingException {
        if (text == null || text.isEmpty()) {
            throw new EmbeddingException("text 不能为空");
        }
        AiProviderConfig provider = providerRouter.selectBestEmbeddingProvider();
        String json = callEmbeddingApi(provider, text);
        return parseEmbeddingResponse(json);
    }

    /** 同步 HTTP 调用 Embedding API */
    private String callEmbeddingApi(AiProviderConfig provider, String text) throws EmbeddingException {
        HttpURLConnection c = null;
        try {
            URL url = new URL(provider.getEmbeddingUrl());
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            c.setDoOutput(true);
            c.setConnectTimeout(5_000);
            c.setReadTimeout(10_000);

            Map<String, Object> body = Map.of(
                    "model", provider.getEmbeddingModel(),
                    "input", text
            );
            String jsonBody = objectMapper.writeValueAsString(body);
            try (OutputStream os = c.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = c.getResponseCode();
            if (code != 200) {
                throw new EmbeddingException("Embedding API 返回非 200: " + code);
            }

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                return sb.toString();
            }
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Embedding API 调用失败: " + e.getMessage(), e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 解析 Embedding API 响应，提取向量 */
    @SuppressWarnings("unchecked")
    private float[] parseEmbeddingResponse(String json) throws EmbeddingException {
        try {
            Map<String, Object> resp = objectMapper.readValue(json, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
            if (data == null || data.isEmpty()) {
                throw new EmbeddingException("Embedding 响应中缺少 data 字段");
            }
            Object embeddingObj = data.get(0).get("embedding");
            if (embeddingObj == null) {
                throw new EmbeddingException("Embedding 响应中缺少 embedding 字段");
            }
            List<Number> embeddingList = (List<Number>) embeddingObj;
            float[] result = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                result[i] = embeddingList.get(i).floatValue();
            }
            return result;
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("解析 Embedding 响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * Embedding 调用异常。调用方应捕获此类异常并降级处理。
     */
    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) {
            super(message);
        }

        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
