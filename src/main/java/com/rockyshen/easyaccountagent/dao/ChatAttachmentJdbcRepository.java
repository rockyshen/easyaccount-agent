package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.ChatAttachment;
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

@Repository
@RequiredArgsConstructor
public class ChatAttachmentJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_COLS =
            "id, user_id, kind, mime_type, size_bytes, width, height, storage_path, "
                    + "thumb_storage_path, thumb_width, thumb_height, "
                    + "referenced, created_at, expires_at, referenced_at";

    private static final RowMapper<ChatAttachment> MAPPER = ChatAttachmentJdbcRepository::mapRow;

    public void insert(ChatAttachment att) {
        jdbcTemplate.update(
                "INSERT INTO chat_attachment (id, user_id, kind, mime_type, size_bytes, width, height, "
                        + "storage_path, thumb_storage_path, thumb_width, thumb_height, "
                        + "referenced, created_at, expires_at, referenced_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                att.getId(),
                att.getUserId(),
                att.getKind(),
                att.getMimeType(),
                att.getSizeBytes(),
                att.getWidth(),
                att.getHeight(),
                att.getStoragePath(),
                att.getThumbStoragePath(),
                att.getThumbWidth(),
                att.getThumbHeight(),
                att.isReferenced() ? 1 : 0,
                toTs(att.getCreatedAt()),
                toTs(att.getExpiresAt()),
                toTs(att.getReferencedAt()));
    }

    public ChatAttachment findById(String id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT " + SELECT_COLS + " FROM chat_attachment WHERE id = ?",
                    MAPPER,
                    id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<ChatAttachment> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] args = ids.toArray();
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLS + " FROM chat_attachment WHERE id IN (" + placeholders + ")",
                MAPPER,
                args);
    }

    public void markReferenced(String id, Date referencedAt, Date expiresAt) {
        jdbcTemplate.update(
                "UPDATE chat_attachment SET referenced = 1, referenced_at = ?, expires_at = ? WHERE id = ?",
                toTs(referencedAt), toTs(expiresAt), id);
    }

    public void updateThumb(String id, String thumbStoragePath, Integer thumbWidth, Integer thumbHeight) {
        jdbcTemplate.update(
                "UPDATE chat_attachment SET thumb_storage_path = ?, thumb_width = ?, thumb_height = ? WHERE id = ?",
                thumbStoragePath, thumbWidth, thumbHeight, id);
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM chat_attachment WHERE id = ?", id);
    }

    public List<ChatAttachment> findExpiredUnreferenced(Date now) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLS
                        + " FROM chat_attachment WHERE referenced = 0 AND expires_at < ?",
                MAPPER,
                toTs(now));
    }

    private static ChatAttachment mapRow(ResultSet rs, int rowNum) throws SQLException {
        ChatAttachment att = new ChatAttachment();
        att.setId(rs.getString("id"));
        att.setUserId(rs.getInt("user_id"));
        att.setKind(rs.getString("kind"));
        att.setMimeType(rs.getString("mime_type"));
        att.setSizeBytes(rs.getLong("size_bytes"));
        int w = rs.getInt("width");
        att.setWidth(rs.wasNull() ? null : w);
        int h = rs.getInt("height");
        att.setHeight(rs.wasNull() ? null : h);
        att.setStoragePath(rs.getString("storage_path"));
        att.setThumbStoragePath(rs.getString("thumb_storage_path"));
        int tw = rs.getInt("thumb_width");
        att.setThumbWidth(rs.wasNull() ? null : tw);
        int th = rs.getInt("thumb_height");
        att.setThumbHeight(rs.wasNull() ? null : th);
        att.setReferenced(rs.getInt("referenced") == 1);
        att.setCreatedAt(fromTs(rs.getTimestamp("created_at")));
        att.setExpiresAt(fromTs(rs.getTimestamp("expires_at")));
        att.setReferencedAt(fromTs(rs.getTimestamp("referenced_at")));
        return att;
    }

    private static Timestamp toTs(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private static Date fromTs(Timestamp ts) {
        return ts == null ? null : new Date(ts.getTime());
    }
}
