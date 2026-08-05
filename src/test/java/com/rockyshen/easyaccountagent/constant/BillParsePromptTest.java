package com.rockyshen.easyaccountagent.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillParsePromptTest {

    @Test
    void prompt_loadedAndRequiresJsonItems() {
        assertFalse(BillParsePrompt.TEXT.isBlank());
        assertTrue(BillParsePrompt.TEXT.contains("items"));
        assertTrue(BillParsePrompt.TEXT.contains("handle"));
    }
}
