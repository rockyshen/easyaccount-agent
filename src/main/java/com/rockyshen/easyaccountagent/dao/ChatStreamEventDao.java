package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.ChatStreamEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatStreamEventDao {

    @Insert("INSERT INTO chat_stream_event (stream_id, event_id, event_name, data_json, created_at) "
            + "VALUES (#{streamId}, #{eventId}, #{eventName}, #{dataJson}, #{createdAt})")
    void insert(ChatStreamEvent event);

    @Select("SELECT stream_id AS streamId, event_id AS eventId, event_name AS eventName, "
            + "data_json AS dataJson, created_at AS createdAt "
            + "FROM chat_stream_event WHERE stream_id = #{streamId} AND event_id > #{afterEventId} "
            + "ORDER BY event_id ASC")
    List<ChatStreamEvent> findAfter(@Param("streamId") String streamId,
                                    @Param("afterEventId") long afterEventId);

    @Delete("DELETE FROM chat_stream_event WHERE stream_id = #{streamId}")
    int deleteByStreamId(@Param("streamId") String streamId);

    @Delete("<script>"
            + "DELETE FROM chat_stream_event WHERE stream_id IN "
            + "<foreach collection='streamIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteByStreamIds(@Param("streamIds") List<String> streamIds);
}
