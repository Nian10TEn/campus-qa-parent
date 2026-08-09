package com.campus.service.dto;

import lombok.Data;

@Data
public class QuestionRequest {
    private String sessionId;
    private String question;
}