package com.rockyshen.easyaccountagent.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dto.ChatRequestDto;
import com.rockyshen.easyaccountagent.entity.ChatStream;
import com.rockyshen.easyaccountagent.metrics.AgentMetrics;
import com.rockyshen.easyaccountagent.model.chat.ChatServerEvent;
import com.rockyshen.easyaccountagent.service.ChatStreamService;
import com.rockyshen.easyaccountagent.service.ChatStreamService.ActiveSession;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 流式对话与断点续传。
 * <ul>
 *   <li>{@code POST /api/chat} — 开流</li>
 *   <li>{@code GET /api/chat/streams/{streamId}} — 续传 SSE</li>
 *   <li>{@code POST /api/chat/streams/{streamId}/cancel} — 显式取消</li>
 *   <li>{@code GET /api/chat/streams/{streamId}/status} — 流状态 JSON</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatSseController {

    private static final long SSE_TIMEOUT_MS = 300_000L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ReactAgent easyAccountAgent;
    private final AgentMetrics agentMetrics;
    private final ChatStreamService chatStreamService;
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "easyaccounts-sse-worker");
        t.setDaemon(true);
        return t;
    });

    public ChatSseController(@Qualifier("easyAccountAgent") ReactAgent easyAccountAgent,
                             AgentMetrics agentMetrics,
                             ChatStreamService chatStreamService) {
        this.easyAccountAgent = easyAccountAgent;
        this.agentMetrics = agentMetrics;
        this.chatStreamService = chatStreamService;
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

        String content = body.getContent().trim();
        String threadId = "u-" + userId;
        ActiveSession session;
        try {
            session = chatStreamService.tryBeginStream(userId);
        } catch (Exception e) {
            log.error("[EasyAccounts SSE] 创建流失败 userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "创建对话流失败"));
        }
        if (session == null) {
            agentMetrics.chatBusy();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflictBody(userId));
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        agentMetrics.sseStreamStarted();
        chatStreamService.attachSubscriber(session, emitter);

        // 断线只拆订阅，不取消生成、不释放 busy
        emitter.onCompletion(() -> chatStreamService.detachSubscriber(session, emitter));
        emitter.onTimeout(() -> {
            log.warn("[EasyAccounts SSE] 连接超时（生成仍继续） userId={} streamId={}",
                    userId, session.streamId);
            chatStreamService.detachSubscriber(session, emitter);
        });
        emitter.onError(ex -> {
            log.warn("[EasyAccounts SSE] 连接中断（生成仍继续） userId={} streamId={}: {}",
                    userId, session.streamId, ex.toString());
            chatStreamService.detachSubscriber(session, emitter);
        });

        asyncExecutor.submit(() -> handleChat(session, threadId, content));
        return emitter;
    }

    @GetMapping(path = "/streams/{streamId}",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object resume(@PathVariable("streamId") String streamId,
                         @RequestParam(value = "afterEventId", required = false) Long afterEventId,
                         @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未登录或会话已失效"));
        }

        long after = resolveAfterEventId(afterEventId, lastEventIdHeader);
        if (after < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "afterEventId 非法"));
        }

        ChatStream stream = chatStreamService.getStream(streamId);
        if (stream == null || chatStreamService.isExpired(stream)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "流不存在或已过期"));
        }
        if (stream.getUserId() == null || stream.getUserId() != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无权访问该流"));
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        asyncExecutor.submit(() -> {
            try {
                boolean live = chatStreamService.replayAndMaybeAttach(streamId, after, emitter);
                if (!live) {
                    emitter.complete();
                } else {
                    ActiveSession session = chatStreamService.getSession(streamId);
                    if (session != null) {
                        emitter.onCompletion(() -> chatStreamService.detachSubscriber(session, emitter));
                        emitter.onTimeout(() -> chatStreamService.detachSubscriber(session, emitter));
                        emitter.onError(ex -> chatStreamService.detachSubscriber(session, emitter));
                    }
                }
            } catch (Exception e) {
                log.warn("[EasyAccounts SSE] 续传失败 streamId={}: {}", streamId, e.toString());
                try {
                    chatStreamService.sendToEmitter(emitter, null, "error", ChatServerEvent.builder()
                            .type("error")
                            .message("续传失败")
                            .streamId(streamId)
                            .build());
                } catch (Exception ignored) {
                    // ignore
                }
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        });
        return emitter;
    }

    @PostMapping(path = "/streams/{streamId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancel(@PathVariable("streamId") String streamId) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未登录或会话已失效"));
        }
        try {
            Map<String, Object> body = chatStreamService.cancel(streamId, userId);
            if (body == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "流不存在或已过期"));
            }
            if (Boolean.TRUE.equals(body.remove("metricClosed"))) {
                agentMetrics.sseStreamFinished();
            }
            return ResponseEntity.ok(body);
        } catch (ChatStreamService.ForbiddenStreamException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无权访问该流"));
        }
    }

    @GetMapping(path = "/streams/{streamId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(@PathVariable("streamId") String streamId) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未登录或会话已失效"));
        }
        ChatStream stream = chatStreamService.getStream(streamId);
        if (stream == null || chatStreamService.isExpired(stream)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "流不存在或已过期"));
        }
        if (stream.getUserId() == null || stream.getUserId() != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无权访问该流"));
        }

        ActiveSession session = chatStreamService.getSession(streamId);
        long lastEventId = stream.getLastEventId() == null ? 0L : stream.getLastEventId();
        String fullText = stream.getFullText() == null ? "" : stream.getFullText();
        String status = stream.getStatus();
        if (session != null) {
            lastEventId = Math.max(lastEventId, session.lastEventId.get());
            synchronized (session.lock) {
                fullText = session.fullText.toString();
            }
            status = session.status;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("streamId", streamId);
        body.put("status", status);
        body.put("lastEventId", lastEventId);
        body.put("contentLength", fullText.length());
        body.put("expireAt", formatExpireAt(stream.getExpireAt()));
        return ResponseEntity.ok(body);
    }

    private void handleChat(ActiveSession session, String threadId, String content) {
        AuthContext.setUserId(session.userId);
        Timer.Sample sample = agentMetrics.startChat();
        String outcome = "success";
        AtomicBoolean busyReleased = new AtomicBoolean(false);
        try {
            chatStreamService.publish(session, "started",
                    ChatServerEvent.builder().content("ok"));

            if (session.isCancelRequested()) {
                outcome = "error";
                return;
            }

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .addMetadata(AuthContext.METADATA_USER_ID, session.userId)
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            Disposable disposable = easyAccountAgent.streamMessages(content, config)
                    .filter(AssistantMessage.class::isInstance)
                    .map(m -> ((AssistantMessage) m).getText())
                    .filter(text -> text != null && !text.isEmpty())
                    .doOnNext(chunk -> {
                        if (session.isCancelRequested()) {
                            throw new StreamCancelledException();
                        }
                        chatStreamService.appendDelta(session, chunk);
                    })
                    .doOnError(errorRef::set)
                    .doFinally(sig -> latch.countDown())
                    .subscribe();
            session.setDisposable(disposable);

            boolean finished = latch.await(SSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                disposable.dispose();
                if (!session.isCancelRequested()) {
                    chatStreamService.fail(session, "生成超时");
                }
                outcome = "error";
                return;
            }

            if (session.isCancelRequested()) {
                outcome = "error";
                return;
            }

            Throwable err = errorRef.get();
            if (err != null) {
                if (err instanceof StreamCancelledException || isCancellation(err)) {
                    outcome = "error";
                    return;
                }
                chatStreamService.fail(session, err.getMessage());
                outcome = "error";
                return;
            }

            if (!chatStreamService.completeSuccessfully(session)) {
                outcome = "error";
            }
        } catch (Exception e) {
            outcome = "error";
            if (!session.isCancelRequested()) {
                log.error("[EasyAccounts SSE] 处理失败 userId={} streamId={}",
                        session.userId, session.streamId, e);
                chatStreamService.fail(session,
                        e.getMessage() != null ? e.getMessage() : "处理失败");
            }
        } finally {
            if (busyReleased.compareAndSet(false, true)) {
                chatStreamService.releaseBusy(session.userId, session.streamId);
            }
            if (session.markMetricClosed()) {
                agentMetrics.sseStreamFinished();
            }
            agentMetrics.stopChat(sample, outcome);
            AuthContext.clear();
        }
    }

    private Map<String, Object> conflictBody(int userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "上一条消息仍在处理中");
        ChatStream running = chatStreamService.findRunningStream(userId);
        if (running != null) {
            body.put("streamId", running.getStreamId());
            body.put("lastEventId", running.getLastEventId() == null ? 0L : running.getLastEventId());
            body.put("status", running.getStatus());
        }
        return body;
    }

    private static long resolveAfterEventId(Long queryParam, String lastEventIdHeader) {
        if (queryParam != null) {
            return queryParam;
        }
        if (lastEventIdHeader != null && !lastEventIdHeader.isBlank()) {
            try {
                return Long.parseLong(lastEventIdHeader.trim());
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return 0L;
    }

    private static String formatExpireAt(Date expireAt) {
        if (expireAt == null) {
            return null;
        }
        return ISO_OFFSET.format(expireAt.toInstant().atZone(SHANGHAI));
    }

    private static boolean isCancellation(Throwable err) {
        Throwable cur = err;
        while (cur != null) {
            if (cur instanceof StreamCancelledException
                    || cur instanceof java.util.concurrent.CancellationException
                    || (cur.getMessage() != null && cur.getMessage().toLowerCase().contains("cancel"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static final class StreamCancelledException extends RuntimeException {
        StreamCancelledException() {
            super("cancelled");
        }
    }
}
