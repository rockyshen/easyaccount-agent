package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

import java.util.Date;

@Data
public class ChatAttachment {
    private String id;
    private int userId;
    private String kind;
    private String mimeType;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    private String storagePath;
    private boolean referenced;
    private Date createdAt;
    private Date expiresAt;
    private Date referencedAt;
}
