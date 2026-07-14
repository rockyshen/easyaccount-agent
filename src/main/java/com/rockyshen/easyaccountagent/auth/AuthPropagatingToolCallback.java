package com.rockyshen.easyaccountagent.auth;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 包装 ToolCallback：在工具线程上从 RunnableConfig / threadId 恢复 AuthContext。
 */
public final class AuthPropagatingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public AuthPropagatingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    public static ToolCallback wrap(ToolCallback callback) {
        if (callback instanceof AuthPropagatingToolCallback) {
            return callback;
        }
        return new AuthPropagatingToolCallback(callback);
    }

    public static ToolCallback[] wrapAll(ToolCallback... callbacks) {
        ToolCallback[] out = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            out[i] = wrap(callbacks[i]);
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
        Integer previous = AuthContext.getUserIdOrNull();
        boolean boundHere = false;
        try {
            if (AuthContext.getUserIdOrNull() == null) {
                Integer resolved = resolveUserId(toolContext);
                if (resolved != null) {
                    AuthContext.setUserId(resolved);
                    boundHere = true;
                }
            }
            if (toolContext != null) {
                return delegate.call(toolInput, toolContext);
            }
            return delegate.call(toolInput);
        } finally {
            if (boundHere) {
                if (previous == null) {
                    AuthContext.clear();
                } else {
                    AuthContext.setUserId(previous);
                }
            }
        }
    }

    private static Integer resolveUserId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        return ToolContextHelper.getConfig(toolContext)
                .map(AuthPropagatingToolCallback::userIdFromConfig)
                .orElse(null);
    }

    private static Integer userIdFromConfig(RunnableConfig config) {
        Object meta = config.metadata(AuthContext.METADATA_USER_ID).orElse(null);
        if (meta instanceof Integer i) {
            return i;
        }
        if (meta instanceof Number n) {
            return n.intValue();
        }
        if (meta instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return config.threadId()
                .map(AuthContext::parseUserIdFromThreadId)
                .orElse(null);
    }
}
