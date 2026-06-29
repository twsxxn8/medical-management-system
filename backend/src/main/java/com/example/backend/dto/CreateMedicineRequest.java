package com.example.backend.dto;

import java.math.BigDecimal;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

// 新增药品的请求参数
public class CreateMedicineRequest {
    
    // 药品名称，必填，最多 100 字符
    @NotBlank
    @Size(max = 100)
    private String name;
    
    // 药品类别
    @Size(max = 50)
    private String category;
    
    // 规格（如 10mg/片）
    @Size(max = 100)
    private String specification;
    
    // 单位（盒/瓶/支）
    @Size(max = 20)
    private String unit;
    
    // 价格，必填
    @NotNull
    private BigDecimal price;
    
    // 初始库存，必填
    @NotNull
    private Integer stock;
    
    // 生产厂家
    @Size(max = 200)
    private String manufacturer;
    
    // 批准文号
    @Size(max = 100)
    private String approvalNumber;
    
    // 储存条件
    @Size(max = 200)
    private String storageCondition;
    
    // Getter 和 Setter（省略，按常规写法）
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getApprovalNumber() { return approvalNumber; }
    public void setApprovalNumber(String approvalNumber) { this.approvalNumber = approvalNumber; }
    public String getStorageCondition() { return storageCondition; }
    public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }
}
