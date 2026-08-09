package com.campus.service.dto;

import lombok.Data;

@Data
public class ChatMessage {
    private String role;
    private String content;
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}