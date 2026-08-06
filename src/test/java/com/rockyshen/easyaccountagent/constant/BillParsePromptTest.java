package com.rockyshen.easyaccountagent.constant;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillParsePromptTest {

    @Test
    void prompt_loadedAndRequiresJsonItems() {
        assertFalse(BillParsePrompt.TEXT.isBlank());
        assertTrue(BillParsePrompt.TEXT.contains("items"));
        assertTrue(BillParsePrompt.TEXT.contains("handle"));
        assertTrue(BillParsePrompt.TEXT.contains("今天"));
        assertTrue(BillParsePrompt.TEXT.contains("昨天"));
    }

    @Test
    void textWithCurrentDate_appendsAsiaShanghaiToday() {
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String text = BillParsePrompt.textWithCurrentDate();
        assertTrue(text.contains("当前日期：" + today), text);
        assertTrue(text.contains("Asia/Shanghai"), text);
        assertTrue(text.contains("相对日期"), text);
    }
}
