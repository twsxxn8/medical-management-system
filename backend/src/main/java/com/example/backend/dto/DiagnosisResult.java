package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI 诊断结果结构化 DTO。
 * 通过 DeepSeek API 的 response_format: json_schema 严格模式保证 LLM 输出合法 JSON，
 * Jackson 直接反序列化，前端不再需要正则解析。
 */
public class DiagnosisResult {

    /** 可能疾病方向（最多3个） */
    @JsonProperty("possible_diseases")
    private List<DiseaseItem> possibleDiseases;

    /** 建议检查项目 */
    @JsonProperty("recommended_checks")
    private List<String> recommendedChecks;

    /** 建议就诊科室 */
    @JsonProperty("recommended_department")
    private String recommendedDepartment;

    /** 就诊紧急程度 */
    private String urgency;

    /** 日常注意事项 */
    private List<String> precautions;

    /** 补充说明（可选） */
    private String note;

    // ===== Getters & Setters =====

    public List<DiseaseItem> getPossibleDiseases() {
        return possibleDiseases;
    }

    public void setPossibleDiseases(List<DiseaseItem> possibleDiseases) {
        this.possibleDiseases = possibleDiseases;
    }

    public List<String> getRecommendedChecks() {
        return recommendedChecks;
    }

    public void setRecommendedChecks(List<String> recommendedChecks) {
        this.recommendedChecks = recommendedChecks;
    }

    public String getRecommendedDepartment() {
        return recommendedDepartment;
    }

    public void setRecommendedDepartment(String recommendedDepartment) {
        this.recommendedDepartment = recommendedDepartment;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public List<String> getPrecautions() {
        return precautions;
    }

    public void setPrecautions(List<String> precautions) {
        this.precautions = precautions;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /**
     * 疾病方向子项：名称、概率、描述。
     */
    public static class DiseaseItem {

        private String name;

        private String probability;

        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProbability() {
            return probability;
        }

        public void setProbability(String probability) {
            this.probability = probability;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
