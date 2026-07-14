package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AuthToken {
    private Long id;
    private Integer userId;
    private String tokenHash;
    private Date expiresAt;
    private Date createdAt;
    private Date lastUsedAt;
    private Boolean revoked;
    private String userAgent;
}
