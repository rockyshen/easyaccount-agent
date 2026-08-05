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

    /** 本地存储根目录 */
    private String storageDir = "./data/chat-attachments";

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

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }
}
