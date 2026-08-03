package com.rockyshen.easyaccountagent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 业务埋点：SSE 对话、鉴权、Agent Tool。
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger sseActive = new AtomicInteger();

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("easyaccount.sse.active", sseActive);
    }

    public void sseStreamStarted() {
        sseActive.incrementAndGet();
    }

    public void sseStreamFinished() {
        sseActive.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void chatBusy() {
        Counter.builder("easyaccount.sse.busy")
                .description("SSE 对话因忙拒绝次数")
                .register(registry)
                .increment();
        Timer.Sample sample = Timer.start(registry);
        stopChat(sample, "busy");
    }

    public Timer.Sample startChat() {
        return Timer.start(registry);
    }

    /** outcome: success | error | busy */
    public void stopChat(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("easyaccount.sse.chat")
                .description("SSE Agent 对话耗时")
                .tag("outcome", outcome == null ? "error" : outcome)
                .register(registry));
    }

    public void authLogin(boolean success) {
        Counter.builder("easyaccount.auth.login")
                .description("登录次数")
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    public void authRegister(boolean success) {
        Counter.builder("easyaccount.auth.register")
                .description("注册次数")
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    public void authLogout() {
        Counter.builder("easyaccount.auth.logout")
                .description("登出次数")
                .register(registry)
                .increment();
    }

    /** 记录 Tool 调用。outcome: success | error */
    public String recordToolCall(String toolName, Supplier<String> action) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder("easyaccount.tool.calls")
                    .description("Agent Tool 调用耗时")
                    .tag("tool", sanitize(toolName))
                    .tag("outcome", outcome)
                    .register(registry));
        }
    }

    private static String sanitize(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "unknown";
        }
        return toolName.length() > 64 ? toolName.substring(0, 64) : toolName;
    }
}
