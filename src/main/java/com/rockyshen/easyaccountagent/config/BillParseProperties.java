package com.rockyshen.easyaccountagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyaccount.bill-parse")
public class BillParseProperties {

    /** DashScope 视觉模型，如 qwen-vl-plus */
    private String vlModel = "qwen-vl-plus";

    /** 单张图片最大字节数，默认 10MB */
    private long maxBytes = 10 * 1024 * 1024L;

    public String getVlModel() {
        return vlModel;
    }

    public void setVlModel(String vlModel) {
        this.vlModel = vlModel;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }
}
