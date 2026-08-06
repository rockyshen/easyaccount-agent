package com.rockyshen.easyaccountagent.constant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyAccountsPromptTest {

    @Test
    void systemPrompt_loadedFromResource() {
        assertFalse(EasyAccountsPrompt.TEXT.isBlank());
        assertTrue(EasyAccountsPrompt.TEXT.contains("个人记账助手"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("禁止 Markdown"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("同一条回复里禁止出现两个不同的"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("附件识别结果必须先确认再写入"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("附件账单识别结果"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("首次引导"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("分类 type 为当前用户私有"));
        assertTrue(EasyAccountsPrompt.TEXT.contains("getOnboardingStatus"));
    }

    @Test
    void currentDateContext_usesAsiaShanghaiToday() {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String ctx = EasyAccountsPrompt.currentDateContext();
        assertTrue(ctx.contains(today), () -> "expected " + today + " in: " + ctx);
        assertTrue(ctx.contains("Asia/Shanghai"), ctx);
        assertTrue(ctx.contains("以本条为准"), ctx);
    }

    @Test
    void mergeSystemWithDateContext_replacesStaleDateBlock() {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String stale = "规则正文\n\n当前日期：2026-08-03（星期一），时区 Asia/Shanghai。旧注入。";
        String merged = EasyAccountsPrompt.mergeSystemWithDateContext(stale);
        assertTrue(merged.startsWith("规则正文"), merged);
        assertTrue(merged.contains("当前日期：" + today), merged);
        assertFalse(merged.contains("2026-08-03"), merged);
        assertEquals(1, merged.split("当前日期：", -1).length - 1, merged);
    }

    @Test
    void stripDateContext_removesAllDateBlocks() {
        String text = "A\n\n当前日期：2026-08-03（星期一），x\n\n当前日期：2026-08-04（星期二），y";
        assertEquals("A", EasyAccountsPrompt.stripDateContext(text));
    }
}
