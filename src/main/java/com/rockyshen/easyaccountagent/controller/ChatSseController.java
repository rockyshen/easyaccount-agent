package com.rockyshen.easyaccountagent.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dto.ChatRequestDto;
import com.rockyshen.easyaccountagent.metrics.AgentMetrics;
import com.rockyshen.easyaccountagent.model.chat.ChatServerEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 流式对话：{@code POST /api/chat}，响应 {@code text/event-stream}。
 * <p>
 * 鉴权走 {@code Authorization: Bearer}（与其它业务 REST 一致）。
 * 事件名：{@code started} / {@code message_delta} / {@code message_end} / {@code error}。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatSseController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ReactAgent easyAccountAgent;
    private final AgentMetrics agentMetrics;
    private final ConcurrentHashMap<Integer, AtomicBoolean> busyByUser = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "easyaccounts-sse-worker");
        t.setDaemon(true);
        return t;
    });

    public ChatSseController(@Qualifier("easyAccountAgent") ReactAgent easyAccountAgent,
                             AgentMetrics agentMetrics) {
        this.easyAccountAgent = easyAccountAgent;
        this.agentMetrics = agentMetrics;
    }

    @PostMapping(produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object chat(@RequestBody(required = false) ChatRequestDto body) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未登录或会话已失效"));
        }
        if (body == null || body.getContent() == null || body.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "消息不能为空"));
        }

        AtomicBoolean busy = busyByUser.computeIfAbsent(userId, id -> new AtomicBoolean(false));
        if (!busy.compareAndSet(false, true)) {
            agentMetrics.chatBusy();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "上一条消息仍在处理中"));
        }

        String content = body.getContent().trim();
        String threadId = "u-" + userId;
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        agentMetrics.sseStreamStarted();

        AtomicBoolean released = new AtomicBoolean(false);
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                busy.set(false);
                agentMetrics.sseStreamFinished();
            }
        };

        emitter.onCompletion(release);
        emitter.onTimeout(() -> {
            log.warn("[EasyAccounts SSE] 超时 userId={}", userId);
            release.run();
        });
        emitter.onError(ex -> {
            log.warn("[EasyAccounts SSE] 连接中断 userId={}: {}", userId, ex.toString());
            release.run();
        });

        asyncExecutor.submit(() -> handleChat(emitter, userId, threadId, content, release));
        return emitter;
    }

    private void handleChat(SseEmitter emitter, int userId, String threadId, String content,
                            Runnable release) {
        AuthContext.setUserId(userId);
        Timer.Sample sample = agentMetrics.startChat();
        String outcome = "success";
        try {
            send(emitter, "started", ChatServerEvent.builder()
                    .type("started")
                    .content("ok")
                    .build());

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .addMetadata(AuthContext.METADATA_USER_ID, userId)
                    .build();

            StringBuilder full = new StringBuilder();
            easyAccountAgent.streamMessages(content, config)
                    .filter(AssistantMessage.class::isInstance)
                    .map(m -> ((AssistantMessage) m).getText())
                    .filter(text -> text != null && !text.isEmpty())
                    .doOnNext(chunk -> {
                        full.append(chunk);
                        send(emitter, "message_delta", ChatServerEvent.builder()
                                .type("message_delta")
                                .content(chunk)
                                .build());
                    })
                    .blockLast();

            send(emitter, "message_end", ChatServerEvent.builder()
                    .type("message_end")
                    .content(full.toString())
                    .build());
            emitter.complete();
        } catch (Exception e) {
            outcome = "error";
            log.error("[EasyAccounts SSE] 处理失败 userId={}", userId, e);
            try {
                send(emitter, "error", ChatServerEvent.builder()
                        .type("error")
                        .message(e.getMessage() != null ? e.getMessage() : "处理失败")
                        .build());
                emitter.complete();
            } catch (Exception sendEx) {
                emitter.completeWithError(sendEx);
            }
        } finally {
            agentMetrics.stopChat(sample, outcome);
            AuthContext.clear();
            // complete 回调可能尚未触发；确保释放 busy
            release.run();
        }
    }

    private static void send(SseEmitter emitter, String event, ChatServerEvent payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 发送失败: " + e.getMessage(), e);
        }
    }
}
