package com.campus.service.util;

import com.campus.common.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisQueueUtil {
    private final StringRedisTemplate stringRedisTemplate;

    public void push(String value) {
        stringRedisTemplate.opsForList().leftPush(Constants.QUESTION_QUEUE_KEY, value);
    }

    public String pop(int timeoutSeconds) {
        return stringRedisTemplate.opsForList().rightPop(Constants.QUESTION_QUEUE_KEY, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }
}