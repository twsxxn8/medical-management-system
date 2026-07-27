package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增科室请求体。
 * 备注：字段长度约束与 SQL 建表脚本对齐，避免写入时触发 SQL 截断错误。
 */
public class CreateDepartmentRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    private Long parentId;

    @Size(max = 500)
    private String description;

    @Size(max = 200)
    private String location;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}

