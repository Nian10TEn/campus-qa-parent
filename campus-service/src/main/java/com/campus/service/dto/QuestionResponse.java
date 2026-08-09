package com.campus.service.dto;

import lombok.Data;

@Data
public class QuestionResponse {
    private String question;
    private String status;
    private String sessionId;
}