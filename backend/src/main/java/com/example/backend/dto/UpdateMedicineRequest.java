package com.example.backend.dto;

import java.math.BigDecimal;
import javax.validation.constraints.Size;

// 修改药品的请求参数（不修改 ID）
public class UpdateMedicineRequest {
    
    @Size(max = 100)
    private String name;
    
    @Size(max = 50)
    private String category;
    
    @Size(max = 100)
    private String specification;
    
    @Size(max = 20)
    private String unit;
    
    private BigDecimal price;
    
    private Integer stock;
    
    @Size(max = 200)
    private String manufacturer;
    
    @Size(max = 100)
    private String approvalNumber;
    
    @Size(max = 200)
    private String storageCondition;
    
    // 状态：0=下架，1=上架
    private Integer status;
    
    // Getter 和 Setter（省略）
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
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
