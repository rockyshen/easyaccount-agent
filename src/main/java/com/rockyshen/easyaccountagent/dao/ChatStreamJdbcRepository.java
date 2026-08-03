package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.ChatStream;
import com.rockyshen.easyaccountagent.entity.ChatStreamEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * chat_stream / chat_stream_event 的 JDBC 访问（避免 MyBatis 结果映射在部分环境下 SELECT 失败）。
 */
@Repository
@RequiredArgsConstructor
public class ChatStreamJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ChatStream> STREAM_MAPPER = ChatStreamJdbcRepository::mapStream;
    private static final RowMapper<ChatStreamEvent> EVENT_MAPPER = ChatStreamJdbcRepository::mapEvent;

    public void insertStream(ChatStream stream) {
        jdbcTemplate.update(
                "INSERT INTO chat_stream (stream_id, user_id, status, full_text, last_event_id, created_at, updated_at, expire_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                stream.getStreamId(),
                stream.getUserId(),
                stream.getStatus(),
                stream.getFullText(),
                stream.getLastEventId() == null ? 0L : stream.getLastEventId(),
                toTs(stream.getCreatedAt()),
                toTs(stream.getUpdatedAt()),
                toTs(stream.getExpireAt()));
    }

    public ChatStream findById(String streamId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT stream_id, user_id, status, full_text, last_event_id, created_at, updated_at, expire_at "
                            + "FROM chat_stream WHERE stream_id = ?",
                    STREAM_MAPPER,
                    streamId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public ChatStream findRunningByUserId(int userId) {
        List<ChatStream> list = jdbcTemplate.query(
                "SELECT stream_id, user_id, status, full_text, last_event_id, created_at, updated_at, expire_at "
                        + "FROM chat_stream WHERE user_id = ? AND status = 'running' "
                        + "ORDER BY created_at DESC LIMIT 1",
                STREAM_MAPPER,
                userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<ChatStream> findAllRunning() {
        return jdbcTemplate.query(
                "SELECT stream_id, user_id, status, full_text, last_event_id, created_at, updated_at, expire_at "
                        + "FROM chat_stream WHERE status = 'running'",
                STREAM_MAPPER);
    }

    public void updateProgress(String streamId, String fullText, long lastEventId, Date updatedAt) {
        jdbcTemplate.update(
                "UPDATE chat_stream SET full_text = ?, last_event_id = ?, updated_at = ? WHERE stream_id = ?",
                fullText, lastEventId, toTs(updatedAt), streamId);
    }

    public void updateStatus(String streamId, String status, String fullText, long lastEventId, Date updatedAt) {
        jdbcTemplate.update(
                "UPDATE chat_stream SET status = ?, full_text = ?, last_event_id = ?, updated_at = ? WHERE stream_id = ?",
                status, fullText, lastEventId, toTs(updatedAt), streamId);
    }

    public List<String> findExpiredIds(Date now) {
        return jdbcTemplate.query(
                "SELECT stream_id FROM chat_stream WHERE expire_at < ?",
                (rs, i) -> rs.getString(1),
                toTs(now));
    }

    public int deleteExpired(Date now) {
        return jdbcTemplate.update("DELETE FROM chat_stream WHERE expire_at < ?", toTs(now));
    }

    public void insertEvent(ChatStreamEvent event) {
        jdbcTemplate.update(
                "INSERT INTO chat_stream_event (stream_id, event_id, event_name, data_json, created_at) VALUES (?,?,?,?,?)",
                event.getStreamId(),
                event.getEventId(),
                event.getEventName(),
                event.getDataJson(),
                toTs(event.getCreatedAt()));
    }

    public List<ChatStreamEvent> findEventsAfter(String streamId, long afterEventId) {
        return jdbcTemplate.query(
                "SELECT stream_id, event_id, event_name, data_json, created_at "
                        + "FROM chat_stream_event WHERE stream_id = ? AND event_id > ? ORDER BY event_id ASC",
                EVENT_MAPPER,
                streamId,
                afterEventId);
    }

    public int deleteEventsByStreamId(String streamId) {
        return jdbcTemplate.update("DELETE FROM chat_stream_event WHERE stream_id = ?", streamId);
    }

    public int deleteEventsByStreamIds(List<String> streamIds) {
        if (streamIds == null || streamIds.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM chat_stream_event WHERE stream_id IN (");
        for (int i = 0; i < streamIds.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        return jdbcTemplate.update(sql.toString(), streamIds.toArray());
    }

    private static ChatStream mapStream(ResultSet rs, int rowNum) throws SQLException {
        ChatStream s = new ChatStream();
        s.setStreamId(rs.getString("stream_id"));
        s.setUserId(rs.getInt("user_id"));
        if (rs.wasNull()) {
            s.setUserId(null);
        }
        s.setStatus(rs.getString("status"));
        s.setFullText(rs.getString("full_text"));
        long last = rs.getLong("last_event_id");
        s.setLastEventId(rs.wasNull() ? 0L : last);
        s.setCreatedAt(toDate(rs.getTimestamp("created_at")));
        s.setUpdatedAt(toDate(rs.getTimestamp("updated_at")));
        s.setExpireAt(toDate(rs.getTimestamp("expire_at")));
        return s;
    }

    private static ChatStreamEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        ChatStreamEvent e = new ChatStreamEvent();
        e.setStreamId(rs.getString("stream_id"));
        e.setEventId(rs.getLong("event_id"));
        e.setEventName(rs.getString("event_name"));
        e.setDataJson(rs.getString("data_json"));
        e.setCreatedAt(toDate(rs.getTimestamp("created_at")));
        return e;
    }

    private static Timestamp toTs(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private static Date toDate(Timestamp ts) {
        return ts == null ? null : new Date(ts.getTime());
    }
}
