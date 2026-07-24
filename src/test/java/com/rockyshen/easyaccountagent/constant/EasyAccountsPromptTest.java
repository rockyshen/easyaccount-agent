package com.rockyshen.easyaccountagent.constant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyAccountsPromptTest {

    @Test
    void currentDateContext_usesAsiaShanghaiToday() {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String ctx = EasyAccountsPrompt.currentDateContext();
        assertTrue(ctx.contains(today), () -> "expected " + today + " in: " + ctx);
        assertTrue(ctx.contains("Asia/Shanghai"), ctx);
    }
}
