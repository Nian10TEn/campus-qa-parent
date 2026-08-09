package com.campus.service.controller;

import com.campus.common.R;
import com.campus.service.dto.QuestionRequest;
import com.campus.service.dto.QuestionResponse;
import com.campus.service.util.RedisQueueUtil;
import com.campus.service.util.SessionHistoryUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class QaController {

    private final RedisQueueUtil redisQueueUtil;
    private final SessionHistoryUtil sessionHistoryUtil;
    private final ObjectMapper objectMapper;

    @PostMapping("/ask")
    public R<QuestionResponse> ask(@RequestBody QuestionRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isEmpty()
                || request.getQuestion() == null || request.getQuestion().isEmpty()) {
            return R.fail(400, "sessionId 和 question 不能为空");
        }

        try {
            // 先把历史记录（最近 5 条）拼到请求里，带着上下文一起给队列消费者
            // 这里暂不改变入队结构，因为后面消费者需要历史拼接 Prompt
            // 我们直接在消费者里通过 sessionHistoryUtil 获取历史

            String json = objectMapper.writeValueAsString(request);
            redisQueueUtil.push(json);

            QuestionResponse resp = new QuestionResponse();
            resp.setQuestion(request.getQuestion());
            resp.setStatus("已受理，AI 正在处理中...");
            resp.setSessionId(request.getSessionId());
            return R.ok(resp);
        } catch (JsonProcessingException e) {
            return R.error("系统错误，入队失败");
        }
    }
}