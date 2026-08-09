package com.campus.service.util;

import com.campus.common.Constants;
import com.campus.service.dto.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionHistoryUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    // 最大保存轮数
    private static final int MAX_HISTORY = 10;
    // 过期时间
    private static final int TTL_MINUTES = 30;

    public void appendHistory(String sessionId, String question, String answer) {
        String key = Constants.SESSION_KEY_PREFIX + sessionId;
        // 用户提问
        ChatMessage userMsg = new ChatMessage("user", question);
        // AI 回答
        ChatMessage assistantMsg = new ChatMessage("assistant", answer);
        try {
            String userJson = objectMapper.writeValueAsString(userMsg);
            String assistantJson = objectMapper.writeValueAsString(assistantMsg);
            // 追加到列表尾部
            stringRedisTemplate.opsForList().rightPush(key, userJson);
            stringRedisTemplate.opsForList().rightPush(key, assistantJson);
            // 限制列表长度，保留最近 MAX_HISTORY 轮
            Long size = stringRedisTemplate.opsForList().size(key);
            if (size != null && size > 2 * MAX_HISTORY) {
                stringRedisTemplate.opsForList().trim(key, size - 2 * MAX_HISTORY, -1);
            }
            // 重置过期时间
            stringRedisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("序列化对话记录失败", e);
        }
    }

    public List<ChatMessage> getRecentHistory(String sessionId, int limit) {
        String key = Constants.SESSION_KEY_PREFIX + sessionId;
        // 从 Redis 列表取最后 limit 条
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, -limit, -1);
        List<ChatMessage> messages = new ArrayList<>();
        if (jsonList != null) {
            for (String json : jsonList) {
                try {
                    ChatMessage msg = objectMapper.readValue(json, ChatMessage.class);
                    messages.add(msg);
                } catch (JsonProcessingException e) {
                    log.error("反序列化对话记录失败", e);
                }
            }
        }
        return messages;
    }
}