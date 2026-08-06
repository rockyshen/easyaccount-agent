package com.rockyshen.easyaccountagent.dto;

/**
 * 附件内容字节（供 /content 接口返回）。
 */
public record ChatAttachmentContent(byte[] bytes, String mimeType, String variant) {
}
