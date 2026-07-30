package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.ChatStream;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

@Mapper
public interface ChatStreamDao {

    @Insert("INSERT INTO chat_stream (stream_id, user_id, status, full_text, last_event_id, created_at, updated_at, expire_at) "
            + "VALUES (#{streamId}, #{userId}, #{status}, #{fullText}, #{lastEventId}, #{createdAt}, #{updatedAt}, #{expireAt})")
    void insert(ChatStream stream);

    @Select("SELECT stream_id AS streamId, user_id AS userId, status, full_text AS fullText, "
            + "last_event_id AS lastEventId, created_at AS createdAt, updated_at AS updatedAt, expire_at AS expireAt "
            + "FROM chat_stream WHERE stream_id = #{streamId}")
    ChatStream findById(@Param("streamId") String streamId);

    @Select("SELECT stream_id AS streamId, user_id AS userId, status, full_text AS fullText, "
            + "last_event_id AS lastEventId, created_at AS createdAt, updated_at AS updatedAt, expire_at AS expireAt "
            + "FROM chat_stream WHERE user_id = #{userId} AND status = 'running' "
            + "ORDER BY created_at DESC LIMIT 1")
    ChatStream findRunningByUserId(@Param("userId") int userId);

    @Select("SELECT stream_id AS streamId, user_id AS userId, status, full_text AS fullText, "
            + "last_event_id AS lastEventId, created_at AS createdAt, updated_at AS updatedAt, expire_at AS expireAt "
            + "FROM chat_stream WHERE status = 'running'")
    List<ChatStream> findAllRunning();

    @Update("UPDATE chat_stream SET full_text = #{fullText}, last_event_id = #{lastEventId}, updated_at = #{updatedAt} "
            + "WHERE stream_id = #{streamId}")
    void updateProgress(@Param("streamId") String streamId,
                        @Param("fullText") String fullText,
                        @Param("lastEventId") long lastEventId,
                        @Param("updatedAt") Date updatedAt);

    @Update("UPDATE chat_stream SET status = #{status}, full_text = #{fullText}, last_event_id = #{lastEventId}, "
            + "updated_at = #{updatedAt} WHERE stream_id = #{streamId}")
    void updateStatus(@Param("streamId") String streamId,
                      @Param("status") String status,
                      @Param("fullText") String fullText,
                      @Param("lastEventId") long lastEventId,
                      @Param("updatedAt") Date updatedAt);

    @Delete("DELETE FROM chat_stream WHERE expire_at < #{now}")
    int deleteExpired(@Param("now") Date now);

    @Select("SELECT stream_id FROM chat_stream WHERE expire_at < #{now}")
    List<String> findExpiredIds(@Param("now") Date now);
}
