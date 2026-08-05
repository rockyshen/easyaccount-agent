package com.rockyshen.easyaccountagent.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequestDto {
    /** 用户自然语言消息；可与 attachmentIds 二选一或同时提供 */
    private String content;

    /** 已上传附件 ID 列表（先 POST /api/chat/attachments） */
    private List<String> attachmentIds;
}
