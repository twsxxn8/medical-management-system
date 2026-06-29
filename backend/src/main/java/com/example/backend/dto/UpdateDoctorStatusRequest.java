package com.example.backend.dto;

import javax.validation.constraints.NotNull;

/**
 * 修改医生状态请求体。
 * 备注：与 SQL 注释约定一致：0=停诊，1=应诊。
 */
public class UpdateDoctorStatusRequest {
    @NotNull
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

