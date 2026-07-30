package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

import java.util.Date;

@Data
public class ChatStreamEvent {
    private String streamId;
    private Long eventId;
    private String eventName;
    private String dataJson;
    private Date createdAt;
}
