package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * AI 问诊请求 DTO：替代 @RequestBody Map，避免 Spring MVC Jackson 解析 Map 时的 UTF-8 问题。
 */
public class AiDiagnosisRequest {

    @NotBlank(message = "症状描述不能为空")
    private String symptoms;

    private List<Map<String, String>> history;

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }

    public List<Map<String, String>> getHistory() {
        return history;
    }

    public void setHistory(List<Map<String, String>> history) {
        this.history = history;
    }
}
