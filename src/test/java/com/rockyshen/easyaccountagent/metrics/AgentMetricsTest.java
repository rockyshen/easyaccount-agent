package com.rockyshen.easyaccountagent.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentMetricsTest {

    private MeterRegistry registry;
    private AgentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentMetrics(registry);
    }

    @Test
    void sseActiveGaugeTracksStartFinish() {
        metrics.sseStreamStarted();
        metrics.sseStreamStarted();
        assertEquals(2.0, registry.get("easyaccount.sse.active").gauge().value());
        metrics.sseStreamFinished();
        assertEquals(1.0, registry.get("easyaccount.sse.active").gauge().value());
    }

    @Test
    void authAndChatMetricsRecorded() {
        metrics.authLogin(true);
        metrics.authLogin(false);
        metrics.authRegister(true);
        metrics.authLogout();
        var sample = metrics.startChat();
        metrics.stopChat(sample, "success");
        metrics.chatBusy();

        assertEquals(1.0, registry.get("easyaccount.auth.login").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("easyaccount.auth.login").tag("result", "failure").counter().count());
        assertEquals(1.0, registry.get("easyaccount.auth.register").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("easyaccount.auth.logout").counter().count());
        assertEquals(1.0, registry.get("easyaccount.sse.chat").tag("outcome", "success").timer().count());
        assertEquals(1.0, registry.get("easyaccount.sse.chat").tag("outcome", "busy").timer().count());
        assertEquals(1.0, registry.get("easyaccount.sse.busy").counter().count());
    }

    @Test
    void meteredToolCallbackRecordsSuccessAndError() {
        ToolCallback ok = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("listAccounts").description("d").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }
        };
        ToolCallback boom = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("addExpense").description("d").inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                throw new IllegalStateException("fail");
            }
        };

        assertEquals("ok", MeteredToolCallback.wrap(ok, metrics).call("{}"));
        assertThrows(IllegalStateException.class, () -> MeteredToolCallback.wrap(boom, metrics).call("{}"));

        assertEquals(1.0, registry.get("easyaccount.tool.calls")
                .tag("tool", "listAccounts").tag("outcome", "success").timer().count());
        assertEquals(1.0, registry.get("easyaccount.tool.calls")
                .tag("tool", "addExpense").tag("outcome", "error").timer().count());
    }
}
