package com.rockyshen.easyaccountagent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPlainTextTest {

    @Test
    void stripsHeadingsAndBold() {
        String raw = "## 账户列表\n\n**余额**：100.00\n";
        String plain = ChatPlainText.sanitize(raw);
        assertEquals("账户列表\n\n余额：100.00\n", plain);
        assertTrue(!plain.contains("#"));
        assertTrue(!plain.contains("**"));
    }

    @Test
    void stripsLoneHashChunk() {
        assertEquals("", ChatPlainText.sanitize("###"));
        assertEquals("", ChatPlainText.sanitize("#\n"));
    }

    @Test
    void stripsLinkKeepLabel() {
        assertEquals("详情", ChatPlainText.sanitize("[详情](https://example.com)"));
    }

    @Test
    void collapsesExcessNewlines() {
        assertEquals("a\n\nb", ChatPlainText.sanitize("a\n\n\n\nb"));
    }
}
