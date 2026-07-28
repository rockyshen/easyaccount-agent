package com.rockyshen.easyaccountagent.metrics;

import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 包装 ToolCallback，记录 easyaccount.tool.calls 指标。
 * 实现 {@link StateAwareToolCallback}，避免打断 Agent 对 ToolContext 的注入链。
 */
public final class MeteredToolCallback implements StateAwareToolCallback {

    private final ToolCallback delegate;
    private final AgentMetrics metrics;

    public MeteredToolCallback(ToolCallback delegate, AgentMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    public static ToolCallback wrap(ToolCallback callback, AgentMetrics metrics) {
        if (callback instanceof MeteredToolCallback) {
            return callback;
        }
        return new MeteredToolCallback(callback, metrics);
    }

    public static ToolCallback[] wrapAll(AgentMetrics metrics, ToolCallback... callbacks) {
        ToolCallback[] out = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            out[i] = wrap(callbacks[i], metrics);
        }
        return out;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = getToolDefinition() != null ? getToolDefinition().name() : "unknown";
        return metrics.recordToolCall(name, () -> {
            if (toolContext != null) {
                return delegate.call(toolInput, toolContext);
            }
            return delegate.call(toolInput);
        });
    }
}
