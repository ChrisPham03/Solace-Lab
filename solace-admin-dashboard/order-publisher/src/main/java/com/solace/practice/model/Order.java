package com.solace.practice.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {
    
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String region;
    private String priority;
    private LocalDateTime createdAt;
    
    // Default constructor - needed for JSON deserialization
    public Order() {}
    
    // Full constructor
    public Order(String orderId, String customerId, String productId,
                 int quantity, BigDecimal totalAmount, OrderStatus status,
                 String region, String priority, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = status;
        this.region = region;
        this.priority = priority;
        this.createdAt = createdAt;
    }
    
    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    @Override
    public String toString() {
        return String.format("Order[id=%s, customer=%s, product=%s, amount=$%s, status=%s, region=%s]",
                orderId, customerId, productId, totalAmount, status, region);
    }
}
