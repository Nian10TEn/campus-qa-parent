package com.campus.service.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/qa/{sessionId}")
public class QaWebSocketServer {
    private static final Map<String, Session> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId) {
        ONLINE_SESSIONS.put(sessionId, session);
        log.info("WebSocket 连接建立，sessionId：{}", sessionId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("sessionId") String sessionId) {
        ONLINE_SESSIONS.remove(sessionId);
        log.info("WebSocket 连接关闭，sessionId：{}", sessionId);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 发生错误：{}", error.getMessage());
    }
    // 指定推送
    public static void sendMessage(String sessionId, String message) {
        Session session = ONLINE_SESSIONS.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("WebSocket 推送失败，sessionId：{}", sessionId, e);
            }
        } else {
            log.warn("WebSocket 连接不存在或已关闭，sessionId：{}", sessionId);
        }
    }
}