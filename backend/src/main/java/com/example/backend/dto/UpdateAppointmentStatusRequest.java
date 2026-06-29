package com.example.backend.dto;

import javax.validation.constraints.NotNull;

/**
 * 修改预约状态请求体（对应 {@code appointment.status}）。
 * 备注：与建表脚本一致：0=未确认, 1=已确认, 2=已完成, 3=已取消, 4=已过期。
 */
public class UpdateAppointmentStatusRequest {
    @NotNull
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

