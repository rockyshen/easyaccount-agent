package com.rockyshen.easyaccountagent.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthPropagatingToolCallbackTest {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void blocksWriteToolsWhenAttachmentConfirmOnly() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("addExpense");
        when(delegate.getToolDefinition()).thenReturn(def);

        AuthContext.setUserId(1);
        AuthContext.setAttachmentConfirmOnly(true);

        AuthPropagatingToolCallback wrapper = new AuthPropagatingToolCallback(delegate);
        String result = wrapper.call("{\"money\":\"1\"}");

        assertTrue(result.contains("附件识别确认阶段"));
        verify(delegate, never()).call(anyString());
        verify(delegate, never()).call(anyString(), any());
    }

    @Test
    void allowsReadToolsWhenAttachmentConfirmOnly() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("listAccounts");
        when(delegate.getToolDefinition()).thenReturn(def);
        when(delegate.call(anyString())).thenReturn("ok");

        AuthContext.setUserId(1);
        AuthContext.setAttachmentConfirmOnly(true);

        AuthPropagatingToolCallback wrapper = new AuthPropagatingToolCallback(delegate);
        String result = wrapper.call("{}");

        assertEquals("ok", result);
        verify(delegate).call("{}");
    }

    @Test
    void allowsWriteToolsWhenNotConfirmOnly() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("addExpense");
        when(delegate.getToolDefinition()).thenReturn(def);
        when(delegate.call(anyString())).thenReturn("记账成功");

        AuthContext.setUserId(1);
        AuthContext.setAttachmentConfirmOnly(false);

        AuthPropagatingToolCallback wrapper = new AuthPropagatingToolCallback(delegate);
        assertEquals("记账成功", wrapper.call("{}"));
        verify(delegate).call("{}");
    }
}
