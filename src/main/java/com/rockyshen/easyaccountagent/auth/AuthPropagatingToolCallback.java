package com.rockyshen.easyaccountagent.auth;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 包装 ToolCallback：在工具线程上从 RunnableConfig / threadId 恢复 AuthContext。
 * <p>
 * 必须实现 {@link StateAwareToolCallback}：AgentToolNode 仅在回调为
 * StateAware / FunctionToolCallback / MethodToolCallback 时才会向 ToolContext
 * 注入 {@code _AGENT_CONFIG_}。普通包装类会导致配置无法注入，传播失效。
 */
@Slf4j
public final class AuthPropagatingToolCallback implements StateAwareToolCallback {

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
                } else {
                    log.warn("[AuthPropagating] 无法从 ToolContext 解析 userId，tool={}",
                            getToolDefinition() != null ? getToolDefinition().name() : "?");
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
                .or(() -> readConfigDirect(toolContext))
                .map(AuthPropagatingToolCallback::userIdFromConfig)
                .orElse(null);
    }

    /**
     * 兜底：直接读 ToolContext map，避免 Helper 类型判断异常时丢配置。
     */
    private static java.util.Optional<RunnableConfig> readConfigDirect(ToolContext toolContext) {
        Object raw = toolContext.getContext() != null
                ? toolContext.getContext().get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY)
                : null;
        if (raw instanceof RunnableConfig config) {
            return java.util.Optional.of(config);
        }
        return java.util.Optional.empty();
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
