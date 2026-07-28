package com.rockyshen.easyaccountagent.model.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 下行事件 JSON（event 名与 type 字段一致）。
 * started / message_delta / message_end / error
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatServerEvent {
    private String type;
    private String content;
    private String message;
}
