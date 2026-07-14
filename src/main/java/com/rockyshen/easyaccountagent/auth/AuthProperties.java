package com.rockyshen.easyaccountagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyaccount.auth")
public class AuthProperties {

    /** 生产默认开启；仅本地调试可关（关则数据不隔离，危险） */
    private boolean enabled = true;
    private int tokenTtlDays = 30;
    private int slidingRenewDays = 7;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTokenTtlDays() {
        return tokenTtlDays;
    }

    public void setTokenTtlDays(int tokenTtlDays) {
        this.tokenTtlDays = tokenTtlDays;
    }

    public int getSlidingRenewDays() {
        return slidingRenewDays;
    }

    public void setSlidingRenewDays(int slidingRenewDays) {
        this.slidingRenewDays = slidingRenewDays;
    }
}
