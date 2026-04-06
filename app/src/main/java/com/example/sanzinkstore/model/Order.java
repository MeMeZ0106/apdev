package com.example.sanzinkstore.model;

import java.util.List;

public class Order {
    private String userId;
    private List<CartItem> items;
    private double totalAmount;
    private long timestamp;
    private String status; // e.g., PENDING, COMPLETED, CANCELLED
    private String paymentMethod; // e.g., PAY_ON_PICKUP, ONLINE_PAYMENT
    private boolean paid;

    public Order() {}

    public Order(String userId, List<CartItem> items, double totalAmount, long timestamp, String status, String paymentMethod, boolean paid) {
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.timestamp = timestamp;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.paid = paid;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
}
