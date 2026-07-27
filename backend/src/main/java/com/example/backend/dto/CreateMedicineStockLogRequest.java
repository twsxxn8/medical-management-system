package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增库存日志请求体（对应 {@code medicine_stock_log}）。
 * 备注：{@code type} 约定 1=入库，2=出库；{@code operatorId} 记录操作人。
 */
public class CreateMedicineStockLogRequest {
    @NotNull
    private Long medicineId;

    @NotNull
    private Integer type;

    @NotNull
    private Integer quantity;

    @NotNull
    private Long operatorId;

    @Size(max = 500)
    private String remark;

    // Getter 和 Setter
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
