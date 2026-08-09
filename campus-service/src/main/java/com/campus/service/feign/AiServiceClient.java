package com.campus.service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "python-ai-service", url = "${ai.service.url:http://127.0.0.1:8000}")
public interface AiServiceClient {

    @PostMapping("/ask")
    Map<String, Object> ask(@RequestBody Map<String, Object> request);
}