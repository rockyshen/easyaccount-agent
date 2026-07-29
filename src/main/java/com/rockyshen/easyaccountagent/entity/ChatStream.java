package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

import java.util.Date;

@Data
public class ChatStream {
    private String streamId;
    private Integer userId;
    private String status;
    private String fullText;
    private Long lastEventId;
    private Date createdAt;
    private Date updatedAt;
    private Date expireAt;
}
