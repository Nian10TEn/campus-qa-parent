package com.campus.service.consumer;

import com.campus.service.dto.ChatMessage;
import com.campus.service.feign.AiServiceClient;
import com.campus.service.util.RedisQueueUtil;
import com.campus.service.util.SessionHistoryUtil;
import com.campus.service.websocket.QaWebSocketServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionConsumer implements CommandLineRunner {

    private final RedisQueueUtil redisQueueUtil;
    private final SessionHistoryUtil sessionHistoryUtil;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        new Thread(() -> {
            while (true) {
                try {
                    String questionJson = redisQueueUtil.pop(60);
                    if (questionJson == null) {
                        continue;
                    }
                    log.info("收到待处理问题：{}", questionJson);

                    Map<String, Object> map = objectMapper.readValue(questionJson, Map.class);
                    String sessionId = (String) map.get("sessionId");
                    String question = (String) map.get("question");

                    List<ChatMessage> history = sessionHistoryUtil.getRecentHistory(sessionId, 5);
                    String prompt = buildPrompt(history, question);
                    log.info("拼接后的 Prompt：{}", prompt);

                    String answer = callAiService(question, history);

                    // 保存对话历史
                    sessionHistoryUtil.appendHistory(sessionId, question, answer);

                    Map<String, String> result = new HashMap<>();
                    result.put("type", "answer");
                    result.put("question", question);
                    result.put("answer", answer);
                    QaWebSocketServer.sendMessage(sessionId, objectMapper.writeValueAsString(result));

                } catch (Exception e) {
                    log.error("处理队列消息出错", e);
                }
            }
        }, "question-consumer-thread").start();
    }

    private String callAiService(String question, List<ChatMessage> history) {
        Map<String, Object> request = new HashMap<>();
        request.put("question", question);
        request.put("history", history);
        try {
            log.info("准备调用 Python AI 服务，请求参数：{}", request);
            Map<String, Object> response = aiServiceClient.ask(request);
            log.info("Python AI 返回原始结果：{}", response);
            if (response != null && response.containsKey("answer")) {
                return (String) response.get("answer");
            }
        } catch (Exception e) {
            log.error("调用 AI 服务失败", e);
        }
        return "AI 服务暂时不可用，请稍后重试。";
    }

    private String buildPrompt(List<ChatMessage> history, String currentQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个校园客服助手，请根据以下对话历史回答用户问题。\n\n");
        for (ChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                sb.append("用户：").append(msg.getContent()).append("\n");
            } else {
                sb.append("客服：").append(msg.getContent()).append("\n");
            }
        }
        sb.append("用户：").append(currentQuestion).append("\n");
        sb.append("客服：");
        return sb.toString();
    }
}