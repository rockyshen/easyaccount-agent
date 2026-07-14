package com.rockyshen.easyaccountagent.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.auth.AuthenticatedUser;
import com.rockyshen.easyaccountagent.auth.WebSocketAuthHandshakeInterceptor;
import com.rockyshen.easyaccountagent.model.ws.ChatClientMsg;
import com.rockyshen.easyaccountagent.model.ws.ChatServerMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ReactAgent easyAccountAgent;
    private final Map<String, WSSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "easyaccounts-ws-worker");
        t.setDaemon(true);
        return t;
    });

    public WebSocketHandler(ObjectMapper objectMapper,
                            @Qualifier("easyAccountAgent") ReactAgent easyAccountAgent) {
        this.objectMapper = objectMapper;
        this.easyAccountAgent = easyAccountAgent;
    }

    private static class WSSession {
        WebSocketSession conn;
        int userId;
        String userName;
        String threadId;
        volatile boolean busy;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttributes()
                .get(WebSocketAuthHandshakeInterceptor.ATTR_USER);
        if (user == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            } catch (Exception ignored) {
                // ignore
            }
            return;
        }
        WSSession ws = new WSSession();
        ws.conn = session;
        ws.userId = user.getId();
        ws.userName = user.getName();
        ws.threadId = "u-" + user.getId();
        sessions.put(session.getId(), ws);
        send(session, ChatServerMsg.builder()
                .type("connected")
                .content("记账助手已连接，用户=" + user.getName())
                .build());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WSSession ws = sessions.get(session.getId());
        if (ws == null) {
            return;
        }
        try {
            ChatClientMsg clientMsg = objectMapper.readValue(message.getPayload(), ChatClientMsg.class);
            if (!"chat".equals(clientMsg.getType())) {
                send(session, ChatServerMsg.builder().type("error").message("未知消息类型").build());
                return;
            }
            if (clientMsg.getContent() == null || clientMsg.getContent().isBlank()) {
                send(session, ChatServerMsg.builder().type("error").message("消息不能为空").build());
                return;
            }
            if (ws.busy) {
                send(session, ChatServerMsg.builder().type("error").message("上一条消息仍在处理中").build());
                return;
            }
            asyncExecutor.submit(() -> handleChat(ws, clientMsg.getContent().trim()));
        } catch (Exception e) {
            log.error("[EasyAccounts WS] 解析失败", e);
            send(session, ChatServerMsg.builder().type("error").message("消息格式错误").build());
        }
    }

    private void handleChat(WSSession ws, String content) {
        ws.busy = true;
        AuthContext.setUserId(ws.userId);
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(ws.threadId)
                    .addMetadata(AuthContext.METADATA_USER_ID, ws.userId)
                    .build();
            StringBuilder full = new StringBuilder();
            easyAccountAgent.streamMessages(content, config)
                    .filter(AssistantMessage.class::isInstance)
                    .map(m -> ((AssistantMessage) m).getText())
                    .filter(text -> text != null && !text.isEmpty())
                    .doOnNext(chunk -> {
                        full.append(chunk);
                        send(ws.conn, ChatServerMsg.builder().type("message_delta").content(chunk).build());
                    })
                    .blockLast();
            send(ws.conn, ChatServerMsg.builder().type("message_end").content(full.toString()).build());
        } catch (Exception e) {
            log.error("[EasyAccounts WS] 处理失败", e);
            send(ws.conn, ChatServerMsg.builder().type("error")
                    .message(e.getMessage() != null ? e.getMessage() : "处理失败").build());
        } finally {
            AuthContext.clear();
            ws.busy = false;
        }
    }

    private void send(WebSocketSession session, ChatServerMsg msg) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            }
        } catch (Exception e) {
            log.warn("[EasyAccounts WS] 发送失败: {}", e.getMessage());
        }
    }
}
