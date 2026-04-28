package com.example.sanzinkstore.model;

public class Conversation {
    private String customerId;
    private String customerName;
    private String lastMessage;
    private long lastMessageTime;
    private long unreadBySeller;    // unread count that seller hasn't seen
    private long unreadByCustomer;  // unread count that customer hasn't seen

    public Conversation() {}

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public long getUnreadBySeller() { return unreadBySeller; }
    public void setUnreadBySeller(long unreadBySeller) { this.unreadBySeller = unreadBySeller; }
    public long getUnreadByCustomer() { return unreadByCustomer; }
    public void setUnreadByCustomer(long unreadByCustomer) { this.unreadByCustomer = unreadByCustomer; }
}

