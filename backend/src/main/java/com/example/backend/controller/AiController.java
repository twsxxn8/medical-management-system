package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.service.AiService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能问诊接口。
 * 备注：调用大模型 API 实现 AI 辅助问诊功能。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * AI 智能问诊：接收症状描述和历史对话，返回 AI 分析结果。
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
}
