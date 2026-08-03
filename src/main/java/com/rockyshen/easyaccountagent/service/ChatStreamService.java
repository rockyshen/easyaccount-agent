package com.rockyshen.easyaccountagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockyshen.easyaccountagent.dao.ChatStreamJdbcRepository;
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
import java.util.Objects;
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

    private final ChatStreamJdbcRepository repo;
    private final ObjectMapper objectMapper;

    /** userId -> 当前 running 的 streamId（进程内 busy） */
    private final ConcurrentHashMap<Integer, String> runningByUser = new ConcurrentHashMap<>();
    /** streamId -> 进行中会话 */
    private final ConcurrentHashMap<String, ActiveSession> sessions = new ConcurrentHashMap<>();

    public ChatStreamService(ChatStreamJdbcRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void recoverStaleRunningStreams() {
        List<ChatStream> stuck;
        try {
            stuck = repo.findAllRunning();
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
        repo.updateStatus(
                stream.getStreamId(),
                STATUS_FAILED,
                stream.getFullText() == null ? "" : stream.getFullText(),
                nextId,
                now);
    }

    public boolean isUserBusy(int userId) {
        return runningByUser.containsKey(userId);
    }

    /**
     * 优先内存中的 running 会话，避免仅因 DB 读失败导致 409 附带信息丢失。
     */
    public ChatStream findRunningStream(int userId) {
        String streamId = runningByUser.get(userId);
        if (streamId != null) {
            ActiveSession session = sessions.get(streamId);
            if (session != null) {
                return snapshot(session);
            }
            try {
                ChatStream s = repo.findById(streamId);
                if (s != null) {
                    return s;
                }
            } catch (Exception e) {
                log.warn("[ChatStream] findRunningStream DB 失败 streamId={}: {}", streamId, e.toString());
            }
        }
        try {
            return repo.findRunningByUserId(userId);
        } catch (Exception e) {
            log.warn("[ChatStream] findRunningByUserId 失败 userId={}: {}", userId, e.toString());
            return null;
        }
    }

    public ChatStream getStream(String streamId) {
        ActiveSession session = sessions.get(streamId);
        try {
            ChatStream fromDb = repo.findById(streamId);
            if (fromDb != null) {
                return fromDb;
            }
        } catch (Exception e) {
            log.error("[ChatStream] getStream DB 失败 streamId={}: {}", streamId, e.toString());
            if (session != null) {
                return snapshot(session);
            }
            throw e;
        }
        return session == null ? null : snapshot(session);
    }

    private ChatStream snapshot(ActiveSession session) {
        ChatStream s = new ChatStream();
        s.setStreamId(session.streamId);
        s.setUserId(session.userId);
        s.setStatus(session.status);
        synchronized (session.lock) {
            s.setFullText(session.fullText.toString());
        }
        s.setLastEventId(session.lastEventId.get());
        s.setExpireAt(new Date(System.currentTimeMillis() + STREAM_TTL.toMillis()));
        return s;
    }

    public boolean isExpired(ChatStream stream) {
        if (stream == null) {
            return true;
        }
        // 内存快照可能没有准确 expireAt；仅 DB 行按 expireAt 判断
        if (sessions.containsKey(stream.getStreamId())) {
            return false;
        }
        return stream.getExpireAt() == null || !stream.getExpireAt().after(new Date());
    }

    public boolean isOwner(ChatStream stream, int userId) {
        return stream != null && stream.getUserId() != null && Objects.equals(stream.getUserId(), userId);
    }

    /**
     * 原子占用 busy 并创建 running 流。若用户已忙则返回 {@code null}。
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
            repo.insertStream(row);

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
        repo.updateProgress(session.streamId, fullTextSnapshot, eventId, now);

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
        repo.updateStatus(session.streamId, STATUS_COMPLETED, full, session.lastEventId.get(), new Date());
        completeCurrentSubscriber(session);
        return true;
    }

    public boolean fail(ActiveSession session, String message) {
        synchronized (session.lock) {
            if (!STATUS_RUNNING.equals(session.status)) {
                return false;
            }
            session.status = STATUS_FAILED;
        }
        String msg = message == null || message.isBlank() ? "生成失败" : message;
        publish(session, "error", ChatServerEvent.builder().message(msg));
        repo.updateStatus(
                session.streamId,
                STATUS_FAILED,
                session.fullText.toString(),
                session.lastEventId.get(),
                new Date());
        completeCurrentSubscriber(session);
        return true;
    }

    /**
     * @return null 表示 404；ForbiddenStreamException 表示 403。
     *         若刚取消了进行中的生成，map 含 {@code metricClosed}=true。
     */
    public Map<String, Object> cancel(String streamId, int userId) {
        ChatStream stream;
        try {
            stream = getStream(streamId);
        } catch (Exception e) {
            log.error("[ChatStream] cancel 读取流失败 streamId={}", streamId, e);
            stream = null;
        }
        if (stream == null || isExpired(stream)) {
            return null;
        }
        if (!isOwner(stream, userId)) {
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
                repo.updateStatus(
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
        }

        long nextId = (stream.getLastEventId() == null ? 0L : stream.getLastEventId()) + 1;
        ChatServerEvent payload = ChatServerEvent.builder()
                .type("error")
                .message("已取消")
                .streamId(streamId)
                .eventId(nextId)
                .build();
        persistEvent(streamId, nextId, "error", payload, new Date());
        repo.updateStatus(
                streamId,
                STATUS_CANCELLED,
                stream.getFullText() == null ? "" : stream.getFullText(),
                nextId,
                new Date());
        runningByUser.compute(userId, (id, current) ->
                streamId.equals(current) ? null : current);
        return statusBody(streamId, STATUS_CANCELLED);
    }

    private static Map<String, Object> statusBody(String streamId, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("streamId", streamId);
        body.put("status", status);
        return body;
    }

    /**
     * @return 是否已挂接 live（false 表示连接应在重放后结束）
     */
    public boolean replayAndMaybeAttach(String streamId, long afterEventId, SseEmitter emitter) throws IOException {
        ChatStream stream = getStream(streamId);
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
        List<ChatStreamEvent> buffered = repo.findEventsAfter(streamId, cursor);
        for (ChatStreamEvent ev : buffered) {
            sendStoredEvent(emitter, ev);
            cursor = ev.getEventId();
        }

        if (session != null && STATUS_RUNNING.equals(session.status)) {
            synchronized (session.lock) {
                List<ChatStreamEvent> more = repo.findEventsAfter(streamId, cursor);
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
        List<ChatStreamEvent> after = repo.findEventsAfter(streamId, cursor);
        if (!after.isEmpty()) {
            for (ChatStreamEvent ev : after) {
                sendStoredEvent(emitter, ev);
            }
            return;
        }

        ChatStream stream = getStream(streamId);
        long lastId = stream == null || stream.getLastEventId() == null ? 0L : stream.getLastEventId();

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
        } else if (STATUS_COMPLETED.equals(status) && stream != null) {
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
        repo.insertEvent(row);
    }

    private void sendStoredEvent(SseEmitter emitter, ChatStreamEvent ev) throws IOException {
        emitter.send(SseEmitter.event()
                .id(String.valueOf(ev.getEventId()))
                .name(ev.getEventName())
                .data(ev.getDataJson(), MediaType.APPLICATION_JSON));
    }

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
            expiredIds = repo.findExpiredIds(now);
        } catch (Exception e) {
            log.debug("[ChatStream] 清理跳过: {}", e.toString());
            return;
        }
        if (expiredIds == null || expiredIds.isEmpty()) {
            return;
        }
        try {
            repo.deleteEventsByStreamIds(expiredIds);
        } catch (Exception e) {
            for (String id : expiredIds) {
                try {
                    repo.deleteEventsByStreamId(id);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        int n = repo.deleteExpired(now);
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

        public boolean markMetricClosed() {
            return metricOpen.compareAndSet(true, false);
        }
    }
}
