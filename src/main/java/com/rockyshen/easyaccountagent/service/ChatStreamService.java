package com.rockyshen.easyaccountagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockyshen.easyaccountagent.dao.ChatStreamDao;
import com.rockyshen.easyaccountagent.dao.ChatStreamEventDao;
import com.rockyshen.easyaccountagent.entity.ChatStream;
import com.rockyshen.easyaccountagent.entity.ChatStreamEvent;
import com.rockyshen.easyaccountagent.model.chat.ChatServerEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 对话流：持久化事件日志（方案 A）+ 内存 live 订阅，支持断线后续传。
 * <p>
 * 客户端断开 SSE 不会取消生成；仅 {@link #cancel(String, int)} 才会停止。
 */
@Slf4j
@Service
@DependsOn("chatStreamSchemaInitializer")
public class ChatStreamService {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    private static final Duration STREAM_TTL = Duration.ofMinutes(30);

    private final ChatStreamDao chatStreamDao;
    private final ChatStreamEventDao chatStreamEventDao;
    private final ObjectMapper objectMapper;

    /** userId -> 当前 running 的 streamId（进程内 busy） */
    private final ConcurrentHashMap<Integer, String> runningByUser = new ConcurrentHashMap<>();
    /** streamId -> 进行中会话 */
    private final ConcurrentHashMap<String, ActiveSession> sessions = new ConcurrentHashMap<>();

    public ChatStreamService(ChatStreamDao chatStreamDao,
                             ChatStreamEventDao chatStreamEventDao,
                             ObjectMapper objectMapper) {
        this.chatStreamDao = chatStreamDao;
        this.chatStreamEventDao = chatStreamEventDao;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void recoverStaleRunningStreams() {
        List<ChatStream> stuck;
        try {
            stuck = chatStreamDao.findAllRunning();
        } catch (Exception e) {
            log.warn("[ChatStream] 启动恢复跳过（表可能尚未创建）: {}", e.toString());
            return;
        }
        if (stuck == null || stuck.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (ChatStream stream : stuck) {
            try {
                failStaleOnStartup(stream, now);
            } catch (Exception e) {
                log.warn("[ChatStream] 启动恢复失败 streamId={}: {}", stream.getStreamId(), e.toString());
            }
        }
        log.info("[ChatStream] 启动时将 {} 条残留 running 流标记为 failed", stuck.size());
    }

    private void failStaleOnStartup(ChatStream stream, Date now) {
        long nextId = (stream.getLastEventId() == null ? 0L : stream.getLastEventId()) + 1;
        ChatServerEvent payload = ChatServerEvent.builder()
                .type("error")
                .message("服务重启，生成中断")
                .streamId(stream.getStreamId())
                .eventId(nextId)
                .build();
        persistEvent(stream.getStreamId(), nextId, "error", payload, now);
        chatStreamDao.updateStatus(
                stream.getStreamId(),
                STATUS_FAILED,
                stream.getFullText() == null ? "" : stream.getFullText(),
                nextId,
                now);
    }

    public boolean isUserBusy(int userId) {
        return runningByUser.containsKey(userId);
    }

    public ChatStream findRunningStream(int userId) {
        String streamId = runningByUser.get(userId);
        if (streamId != null) {
            ChatStream s = chatStreamDao.findById(streamId);
            if (s != null) {
                return s;
            }
        }
        return chatStreamDao.findRunningByUserId(userId);
    }

    public ChatStream getStream(String streamId) {
        return chatStreamDao.findById(streamId);
    }

    public boolean isExpired(ChatStream stream) {
        return stream == null || stream.getExpireAt() == null || !stream.getExpireAt().after(new Date());
    }

    /**
     * 原子占用 busy 并创建 running 流。若用户已忙则返回 {@code null}。
     * 调用方须在生成结束时 {@link #releaseBusy(int, String)}。
     */
    public ActiveSession tryBeginStream(int userId) {
        String streamId = "s-" + UUID.randomUUID().toString().replace("-", "");
        String previous = runningByUser.putIfAbsent(userId, streamId);
        if (previous != null) {
            return null;
        }

        Date now = new Date();
        Date expireAt = new Date(now.getTime() + STREAM_TTL.toMillis());
        try {
            ChatStream row = new ChatStream();
            row.setStreamId(streamId);
            row.setUserId(userId);
            row.setStatus(STATUS_RUNNING);
            row.setFullText("");
            row.setLastEventId(0L);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            row.setExpireAt(expireAt);
            chatStreamDao.insert(row);

            ActiveSession session = new ActiveSession(streamId, userId);
            sessions.put(streamId, session);
            return session;
        } catch (RuntimeException e) {
            runningByUser.remove(userId, streamId);
            throw e;
        }
    }

    public void releaseBusy(int userId, String streamId) {
        runningByUser.compute(userId, (id, current) -> {
            if (current != null && current.equals(streamId)) {
                return null;
            }
            return current;
        });
        sessions.remove(streamId);
    }

    public void attachSubscriber(ActiveSession session, SseEmitter emitter) {
        SseEmitter previous;
        synchronized (session.lock) {
            previous = session.subscriber;
            session.subscriber = emitter;
        }
        completeQuietly(previous);
    }

    public void detachSubscriber(ActiveSession session, SseEmitter emitter) {
        synchronized (session.lock) {
            if (session.subscriber == emitter) {
                session.subscriber = null;
            }
        }
    }

    public ActiveSession getSession(String streamId) {
        return sessions.get(streamId);
    }

    /**
     * 分配 eventId、写入 DB、更新 fullText，并尝试推给当前订阅者。
     * 推送失败仅拆掉订阅者，不中断生成。
     */
    public long publish(ActiveSession session, String eventName, ChatServerEvent.ChatServerEventBuilder builder) {
        long eventId;
        String fullTextSnapshot;
        synchronized (session.lock) {
            eventId = session.lastEventId.incrementAndGet();
            fullTextSnapshot = session.fullText.toString();
        }

        ChatServerEvent payload = builder
                .type(eventName)
                .streamId(session.streamId)
                .eventId(eventId)
                .build();

        Date now = new Date();
        persistEvent(session.streamId, eventId, eventName, payload, now);
        chatStreamDao.updateProgress(session.streamId, fullTextSnapshot, eventId, now);

        SseEmitter emitter;
        synchronized (session.lock) {
            emitter = session.subscriber;
        }
        if (emitter != null && !sendToEmitter(emitter, eventId, eventName, payload)) {
            detachSubscriber(session, emitter);
        }
        return eventId;
    }

    public void appendDelta(ActiveSession session, String chunk) {
        synchronized (session.lock) {
            session.fullText.append(chunk);
        }
        publish(session, "message_delta", ChatServerEvent.builder().content(chunk));
    }

    /** @return true 若成功进入 completed */
    public boolean completeSuccessfully(ActiveSession session) {
        String full;
        synchronized (session.lock) {
            if (!STATUS_RUNNING.equals(session.status)) {
                return false;
            }
            session.status = STATUS_COMPLETED;
            full = session.fullText.toString();
        }
        publish(session, "message_end", ChatServerEvent.builder().content(full));
        chatStreamDao.updateStatus(session.streamId, STATUS_COMPLETED, full, session.lastEventId.get(), new Date());
        completeCurrentSubscriber(session);
        return true;
    }

    /** @return true 若成功进入 failed */
    public boolean fail(ActiveSession session, String message) {
        synchronized (session.lock) {
            if (!STATUS_RUNNING.equals(session.status)) {
                return false;
            }
            session.status = STATUS_FAILED;
        }
        String msg = message == null || message.isBlank() ? "生成失败" : message;
        publish(session, "error", ChatServerEvent.builder().message(msg));
        chatStreamDao.updateStatus(
                session.streamId,
                STATUS_FAILED,
                session.fullText.toString(),
                session.lastEventId.get(),
                new Date());
        completeCurrentSubscriber(session);
        return true;
    }

    /**
     * 显式取消：停止生成、写 error、释放 busy。对已 cancelled 幂等。
     *
     * @return null 表示 404；ForbiddenStreamException 表示 403。
     *         若刚取消了进行中的生成，map 含 {@code metricClosed}=true，调用方应 sseStreamFinished。
     */
    public Map<String, Object> cancel(String streamId, int userId) {
        ChatStream stream = chatStreamDao.findById(streamId);
        if (stream == null || isExpired(stream)) {
            return null;
        }
        if (stream.getUserId() == null || stream.getUserId() != userId) {
            throw new ForbiddenStreamException();
        }
        if (STATUS_CANCELLED.equals(stream.getStatus())) {
            return statusBody(streamId, STATUS_CANCELLED);
        }
        if (!STATUS_RUNNING.equals(stream.getStatus())) {
            return statusBody(streamId, stream.getStatus());
        }

        ActiveSession session = sessions.get(streamId);
        if (session != null) {
            session.cancelRequested.set(true);
            Disposable d = session.disposable;
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
            boolean becameCancelled;
            synchronized (session.lock) {
                becameCancelled = STATUS_RUNNING.equals(session.status);
                if (becameCancelled) {
                    session.status = STATUS_CANCELLED;
                }
            }
            boolean metricClosed = false;
            if (becameCancelled) {
                publish(session, "error", ChatServerEvent.builder().message("已取消"));
                chatStreamDao.updateStatus(
                        streamId,
                        STATUS_CANCELLED,
                        session.fullText.toString(),
                        session.lastEventId.get(),
                        new Date());
                completeCurrentSubscriber(session);
                metricClosed = session.markMetricClosed();
            }
            releaseBusy(userId, streamId);
            Map<String, Object> body = statusBody(streamId, STATUS_CANCELLED);
            if (metricClosed) {
                body.put("metricClosed", true);
            }
            return body;
        } else {
            long nextId = (stream.getLastEventId() == null ? 0L : stream.getLastEventId()) + 1;
            ChatServerEvent payload = ChatServerEvent.builder()
                    .type("error")
                    .message("已取消")
                    .streamId(streamId)
                    .eventId(nextId)
                    .build();
            persistEvent(streamId, nextId, "error", payload, new Date());
            chatStreamDao.updateStatus(
                    streamId,
                    STATUS_CANCELLED,
                    stream.getFullText() == null ? "" : stream.getFullText(),
                    nextId,
                    new Date());
            runningByUser.compute(userId, (id, current) ->
                    streamId.equals(current) ? null : current);
        }
        return statusBody(streamId, STATUS_CANCELLED);
    }

    private static Map<String, Object> statusBody(String streamId, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("streamId", streamId);
        body.put("status", status);
        return body;
    }

    /**
     * 重放 afterEventId 之后的事件；若仍 running 则挂上 live。
     *
     * @return 是否已挂接 live（false 表示连接应在重放后结束）
     */
    public boolean replayAndMaybeAttach(String streamId, long afterEventId, SseEmitter emitter) throws IOException {
        ChatStream stream = chatStreamDao.findById(streamId);
        ActiveSession session = sessions.get(streamId);

        long serverLast = stream.getLastEventId() == null ? 0L : stream.getLastEventId();
        if (session != null) {
            serverLast = Math.max(serverLast, session.lastEventId.get());
        }

        sendToEmitter(emitter, null, "resume", ChatServerEvent.builder()
                .type("resume")
                .streamId(streamId)
                .afterEventId(afterEventId)
                .serverLastEventId(serverLast)
                .status(session != null ? session.status : stream.getStatus())
                .build());

        long cursor = afterEventId;
        List<ChatStreamEvent> buffered = chatStreamEventDao.findAfter(streamId, cursor);
        for (ChatStreamEvent ev : buffered) {
            sendStoredEvent(emitter, ev);
            cursor = ev.getEventId();
        }

        if (session != null && STATUS_RUNNING.equals(session.status)) {
            synchronized (session.lock) {
                List<ChatStreamEvent> more = chatStreamEventDao.findAfter(streamId, cursor);
                for (ChatStreamEvent ev : more) {
                    sendStoredEvent(emitter, ev);
                    cursor = ev.getEventId();
                }
                if (!STATUS_RUNNING.equals(session.status)) {
                    ensureTerminalDelivered(streamId, session.status, cursor, emitter);
                    return false;
                }
                SseEmitter previous = session.subscriber;
                session.subscriber = emitter;
                completeQuietly(previous);
                return true;
            }
        }

        String status = session != null ? session.status : stream.getStatus();
        ensureTerminalDelivered(streamId, status, cursor, emitter);
        return false;
    }

    private void ensureTerminalDelivered(String streamId, String status, long cursor, SseEmitter emitter)
            throws IOException {
        List<ChatStreamEvent> after = chatStreamEventDao.findAfter(streamId, cursor);
        if (!after.isEmpty()) {
            for (ChatStreamEvent ev : after) {
                sendStoredEvent(emitter, ev);
            }
            return;
        }

        ChatStream stream = chatStreamDao.findById(streamId);
        long lastId = stream.getLastEventId() == null ? 0L : stream.getLastEventId();

        // 已对齐：正常无需再推；仅当完全无事件日志时按状态补终态
        if (cursor >= lastId) {
            if (lastId == 0 && STATUS_CANCELLED.equals(status)) {
                sendToEmitter(emitter, 1L, "error", ChatServerEvent.builder()
                        .type("error")
                        .message("已取消")
                        .streamId(streamId)
                        .eventId(1L)
                        .build());
            } else if (lastId == 0 && STATUS_FAILED.equals(status)) {
                sendToEmitter(emitter, 1L, "error", ChatServerEvent.builder()
                        .type("error")
                        .message("生成失败")
                        .streamId(streamId)
                        .eventId(1L)
                        .build());
            }
            return;
        }

        // cursor < lastId 但 findAfter 为空（异常）：按状态补终态
        if (STATUS_CANCELLED.equals(status)) {
            sendToEmitter(emitter, lastId, "error", ChatServerEvent.builder()
                    .type("error")
                    .message("已取消")
                    .streamId(streamId)
                    .eventId(lastId)
                    .build());
        } else if (STATUS_FAILED.equals(status)) {
            sendToEmitter(emitter, lastId, "error", ChatServerEvent.builder()
                    .type("error")
                    .message("生成失败")
                    .streamId(streamId)
                    .eventId(lastId)
                    .build());
        } else if (STATUS_COMPLETED.equals(status)) {
            sendToEmitter(emitter, lastId, "message_end", ChatServerEvent.builder()
                    .type("message_end")
                    .content(stream.getFullText() == null ? "" : stream.getFullText())
                    .streamId(streamId)
                    .eventId(lastId)
                    .build());
        }
    }

    private void completeCurrentSubscriber(ActiveSession session) {
        SseEmitter emitter;
        synchronized (session.lock) {
            emitter = session.subscriber;
            session.subscriber = null;
        }
        completeQuietly(emitter);
    }

    private void persistEvent(String streamId, long eventId, String eventName,
                              ChatServerEvent payload, Date now) {
        ChatStreamEvent row = new ChatStreamEvent();
        row.setStreamId(streamId);
        row.setEventId(eventId);
        row.setEventName(eventName);
        row.setDataJson(toJson(payload));
        row.setCreatedAt(now);
        chatStreamEventDao.insert(row);
    }

    private void sendStoredEvent(SseEmitter emitter, ChatStreamEvent ev) throws IOException {
        emitter.send(SseEmitter.event()
                .id(String.valueOf(ev.getEventId()))
                .name(ev.getEventName())
                .data(ev.getDataJson(), MediaType.APPLICATION_JSON));
    }

    /**
     * @param eventId 可为 null（如 resume 提示帧不占序号）
     * @return false 表示发送失败
     */
    public boolean sendToEmitter(SseEmitter emitter, Long eventId, String eventName, ChatServerEvent payload) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON);
            if (eventId != null) {
                builder.id(String.valueOf(eventId));
            }
            emitter.send(builder);
            return true;
        } catch (Exception e) {
            log.debug("[ChatStream] SSE 推送失败（客户端可能已断开）: {}", e.toString());
            return false;
        }
    }

    private static void completeQuietly(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
    }

    private String toJson(ChatServerEvent payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 SSE 事件失败", e);
        }
    }

    @Scheduled(fixedDelayString = "${easyaccount.chat-stream.cleanup-interval-ms:60000}")
    public void cleanupExpired() {
        Date now = new Date();
        List<String> expiredIds;
        try {
            expiredIds = chatStreamDao.findExpiredIds(now);
        } catch (Exception e) {
            log.debug("[ChatStream] 清理跳过: {}", e.toString());
            return;
        }
        if (expiredIds == null || expiredIds.isEmpty()) {
            return;
        }
        try {
            chatStreamEventDao.deleteByStreamIds(expiredIds);
        } catch (Exception e) {
            for (String id : expiredIds) {
                try {
                    chatStreamEventDao.deleteByStreamId(id);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        int n = chatStreamDao.deleteExpired(now);
        if (n > 0) {
            log.info("[ChatStream] 清理过期流 {} 条", n);
        }
    }

    public static final class ForbiddenStreamException extends RuntimeException {
        public ForbiddenStreamException() {
            super("无权访问该流");
        }
    }

    public static final class ActiveSession {
        public final String streamId;
        public final int userId;
        public final AtomicLong lastEventId = new AtomicLong(0);
        public final StringBuilder fullText = new StringBuilder();
        public final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        public final Object lock = new Object();
        public volatile String status = STATUS_RUNNING;
        public volatile SseEmitter subscriber;
        public volatile Disposable disposable;
        /** 与 AgentMetrics.sseStreamStarted/Finished 成对，避免 cancel 与生成线程双减 */
        private final AtomicBoolean metricOpen = new AtomicBoolean(true);

        ActiveSession(String streamId, int userId) {
            this.streamId = streamId;
            this.userId = userId;
        }

        public boolean isCancelRequested() {
            return cancelRequested.get();
        }

        public void setDisposable(Disposable disposable) {
            this.disposable = disposable;
        }

        /** @return true 表示调用方应执行 sseStreamFinished */
        public boolean markMetricClosed() {
            return metricOpen.compareAndSet(true, false);
        }
    }
}
