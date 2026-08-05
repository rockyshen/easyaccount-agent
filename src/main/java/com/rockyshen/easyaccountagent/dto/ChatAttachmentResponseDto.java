package com.rockyshen.easyaccountagent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatAttachmentResponseDto {
    private String id;
    private String kind;
    private String mimeType;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    private String url;
    private String expiresAt;
    private String createdAt;
}
