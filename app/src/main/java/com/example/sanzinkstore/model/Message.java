package com.example.sanzinkstore.model;

public class Message {
    private String id;
    private String senderId;   // customerId or "seller"
    private String text;
    private long timestamp;
    private boolean readBySeller;

    public Message() {}

    public Message(String senderId, String text, long timestamp) {
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.readBySeller = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public boolean isReadBySeller() { return readBySeller; }
    public void setReadBySeller(boolean readBySeller) { this.readBySeller = readBySeller; }
}

