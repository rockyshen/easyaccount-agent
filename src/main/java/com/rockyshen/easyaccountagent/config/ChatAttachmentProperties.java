package com.rockyshen.easyaccountagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyaccount.attachments")
public class ChatAttachmentProperties {

    /** 单文件最大字节，默认 8 MiB */
    private long maxBytes = 8L * 1024 * 1024;

    /** 单次对话最多附件数 */
    private int maxCount = 9;

    /** 未引用附件保留小时数（同时作为可引用窗口） */
    private int ttlHours = 24;

    /**
     * 已被开聊成功引用后的保留天数（长期保留）。
     * 文档建议 ≥ 90 天。
     */
    private int referencedRetentionDays = 365;

    /** 本地存储根目录 */
    private String storageDir = "./data/chat-attachments";

    /**
     * 对外可访问的 Base URL（用于拼 url / thumbnailUrl）。
     * 为空时返回相对路径 /api/chat/attachments/{id}/content?...
     */
    private String publicBaseUrl = "";

    /** 缩略图最长边（像素） */
    private int thumbMaxEdge = 256;

    /** JPEG 缩略图质量 0–1 */
    private float thumbJpegQuality = 0.75f;

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public int getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(int ttlHours) {
        this.ttlHours = ttlHours;
    }

    public int getReferencedRetentionDays() {
        return referencedRetentionDays;
    }

    public void setReferencedRetentionDays(int referencedRetentionDays) {
        this.referencedRetentionDays = referencedRetentionDays;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getThumbMaxEdge() {
        return thumbMaxEdge;
    }

    public void setThumbMaxEdge(int thumbMaxEdge) {
        this.thumbMaxEdge = thumbMaxEdge;
    }

    public float getThumbJpegQuality() {
        return thumbJpegQuality;
    }

    public void setThumbJpegQuality(float thumbJpegQuality) {
        this.thumbJpegQuality = thumbJpegQuality;
    }
}
