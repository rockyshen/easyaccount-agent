package com.rockyshen.easyaccountagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockyshen.easyaccountagent.dao.ChatStreamJdbcRepository;
import com.rockyshen.easyaccountagent.entity.ChatStream;
import com.rockyshen.easyaccountagent.entity.ChatStreamEvent;
import com.rockyshen.easyaccountagent.model.chat.ChatServerEvent;
import com.rockyshen.easyaccountagent.service.ChatStreamService.ActiveSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatStreamServiceTest {

    @Mock
    private ChatStreamJdbcRepository repo;

    private ChatStreamService service;
    private final ConcurrentHashMap<String, ChatStream> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ChatStreamEvent>> events = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        service = new ChatStreamService(repo, new ObjectMapper());

        doAnswer(inv -> {
            ChatStream s = inv.getArgument(0);
            store.put(s.getStreamId(), copy(s));
            return null;
        }).when(repo).insertStream(any(ChatStream.class));

        when(repo.findById(anyString())).thenAnswer(inv -> {
            ChatStream s = store.get(inv.getArgument(0, String.class));
            return s == null ? null : copy(s);
        });

        doAnswer(inv -> {
            String id = inv.getArgument(0);
            ChatStream s = store.get(id);
            if (s != null) {
                s.setFullText(inv.getArgument(1));
                s.setLastEventId(inv.getArgument(2));
                s.setUpdatedAt(inv.getArgument(3));
            }
            return null;
        }).when(repo).updateProgress(anyString(), any(), anyLong(), any(Date.class));

        doAnswer(inv -> {
            String id = inv.getArgument(0);
            ChatStream s = store.get(id);
            if (s != null) {
                s.setStatus(inv.getArgument(1));
                s.setFullText(inv.getArgument(2));
                s.setLastEventId(inv.getArgument(3));
                s.setUpdatedAt(inv.getArgument(4));
            }
            return null;
        }).when(repo).updateStatus(anyString(), anyString(), any(), anyLong(), any(Date.class));

        doAnswer(inv -> {
            ChatStreamEvent e = inv.getArgument(0);
            events.computeIfAbsent(e.getStreamId(), k -> new ArrayList<>()).add(e);
            return null;
        }).when(repo).insertEvent(any(ChatStreamEvent.class));

        when(repo.findEventsAfter(anyString(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            long after = inv.getArgument(1);
            return events.getOrDefault(id, List.of()).stream()
                    .filter(e -> e.getEventId() > after)
                    .toList();
        });

        when(repo.findAllRunning()).thenReturn(List.of());
    }

    @Test
    void tryBeginStreamOccupiesBusyAndSecondReturnsNull() {
        ActiveSession a = service.tryBeginStream(1);
        assertNotNull(a);
        assertTrue(a.streamId.startsWith("s-"));
        assertTrue(service.isUserBusy(1));

        ActiveSession b = service.tryBeginStream(1);
        assertNull(b);

        verify(repo, atLeastOnce()).insertStream(any(ChatStream.class));
    }

    @Test
    void publishIncrementsEventIdAndPersists() {
        ActiveSession session = service.tryBeginStream(7);
        assertNotNull(session);

        long id1 = service.publish(session, "started", ChatServerEvent.builder().content("ok"));
        service.appendDelta(session, "你");
        service.appendDelta(session, "好");
        assertTrue(service.completeSuccessfully(session));

        assertEquals(1L, id1);
        assertEquals(4L, session.lastEventId.get());
        assertEquals("你好", session.fullText.toString());
        assertEquals(4, events.get(session.streamId).size());
        assertEquals(ChatStreamService.STATUS_COMPLETED, store.get(session.streamId).getStatus());
    }

    @Test
    void cancelIsIdempotentAndForbiddenForOtherUser() {
        ActiveSession session = service.tryBeginStream(3);
        assertNotNull(session);

        Map<String, Object> body = service.cancel(session.streamId, 3);
        assertEquals(ChatStreamService.STATUS_CANCELLED, body.get("status"));
        assertFalse(service.isUserBusy(3));

        Map<String, Object> again = service.cancel(session.streamId, 3);
        assertEquals(ChatStreamService.STATUS_CANCELLED, again.get("status"));

        store.get(session.streamId).setUserId(3);
        assertThrows(ChatStreamService.ForbiddenStreamException.class,
                () -> service.cancel(session.streamId, 99));
    }

    @Test
    void expiredStreamTreatedAsNotFoundOnCancel() {
        ActiveSession session = service.tryBeginStream(2);
        assertNotNull(session);
        // 释放内存会话，仅留 DB 行并设为过期
        service.releaseBusy(2, session.streamId);
        ChatStream row = store.get(session.streamId);
        row.setExpireAt(new Date(System.currentTimeMillis() - 1000));

        assertNull(service.cancel(session.streamId, 2));
    }

    @Test
    void getStreamFallsBackToMemoryWhenDbThrows() {
        ActiveSession session = service.tryBeginStream(9);
        assertNotNull(session);
        when(repo.findById(session.streamId)).thenThrow(new RuntimeException("db down"));

        ChatStream s = service.getStream(session.streamId);
        assertNotNull(s);
        assertEquals(session.streamId, s.getStreamId());
        assertEquals(9, s.getUserId());
    }

    private static ChatStream copy(ChatStream s) {
        ChatStream c = new ChatStream();
        c.setStreamId(s.getStreamId());
        c.setUserId(s.getUserId());
        c.setStatus(s.getStatus());
        c.setFullText(s.getFullText());
        c.setLastEventId(s.getLastEventId());
        c.setCreatedAt(s.getCreatedAt());
        c.setUpdatedAt(s.getUpdatedAt());
        c.setExpireAt(s.getExpireAt());
        return c;
    }
}
