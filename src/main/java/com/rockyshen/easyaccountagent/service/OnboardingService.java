package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.config.OnboardingProperties;
import com.rockyshen.easyaccountagent.dao.AccountDao;
import com.rockyshen.easyaccountagent.dao.TypeDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OnboardingProperties onboardingProperties;
    private final TypeSeedService typeSeedService;
    private final AccountDao accountDao;
    private final TypeDao typeDao;

    /**
     * 注册后调用：按配置为用户克隆预设分类（不创建默认账户）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void afterRegister(int userId) {
        if (onboardingProperties.isCloneTypesOnRegister()) {
            typeSeedService.cloneTemplateForUserIfEmpty(userId);
        }
    }

    /**
     * 登录后调用：存量空分类用户补种。
     */
    @Transactional(rollbackFor = Exception.class)
    public void afterLogin(int userId) {
        if (onboardingProperties.isCloneTypesOnLoginIfEmpty()) {
            typeSeedService.cloneTemplateForUserIfEmpty(userId);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(int userId) {
        boolean hasAccounts = !accountDao.findByDisableFalse(userId).isEmpty();
        boolean hasTypes = typeDao.countActiveByUserId(userId) > 0;
        Map<String, Object> onboarding = new LinkedHashMap<>();
        onboarding.put("needsOnboarding", !hasAccounts);
        onboarding.put("hasAccounts", hasAccounts);
        onboarding.put("hasTypes", hasTypes);
        onboarding.put("typesSeeded", hasTypes);
        return onboarding;
    }

    public String statusText(int userId) {
        Map<String, Object> s = status(userId);
        return String.format(
                "引导状态：needsOnboarding=%s, hasAccounts=%s, hasTypes=%s, typesSeeded=%s。"
                        + "若无账户请先对话引导用户创建账户，再确认分类后记账。",
                s.get("needsOnboarding"), s.get("hasAccounts"), s.get("hasTypes"), s.get("typesSeeded"));
    }
}
