package com.rockyshen.easyaccountagent.service;

import org.springframework.http.HttpStatus;

/**
 * 附件 API 可映射为明确 HTTP 状态的业务异常。
 */
public class AttachmentException extends RuntimeException {

    private final HttpStatus status;

    public AttachmentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
