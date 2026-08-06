package com.rockyshen.easyaccountagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyaccount.onboarding")
public class OnboardingProperties {

    /** 注册成功后若用户无分类则克隆模板 */
    private boolean cloneTypesOnRegister = true;
    /** 登录时若用户无分类则补种（覆盖存量空分类用户） */
    private boolean cloneTypesOnLoginIfEmpty = true;

    public boolean isCloneTypesOnRegister() {
        return cloneTypesOnRegister;
    }

    public void setCloneTypesOnRegister(boolean cloneTypesOnRegister) {
        this.cloneTypesOnRegister = cloneTypesOnRegister;
    }

    public boolean isCloneTypesOnLoginIfEmpty() {
        return cloneTypesOnLoginIfEmpty;
    }

    public void setCloneTypesOnLoginIfEmpty(boolean cloneTypesOnLoginIfEmpty) {
        this.cloneTypesOnLoginIfEmpty = cloneTypesOnLoginIfEmpty;
    }
}
