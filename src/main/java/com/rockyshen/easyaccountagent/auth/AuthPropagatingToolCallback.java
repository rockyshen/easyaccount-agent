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

import java.util.Set;

/**
 * 包装 ToolCallback：在工具线程上从 RunnableConfig / threadId 恢复 AuthContext。
 * <p>
 * 必须实现 {@link StateAwareToolCallback}：AgentToolNode 仅在回调为
 * StateAware / FunctionToolCallback / MethodToolCallback 时才会向 ToolContext
 * 注入 {@code _AGENT_CONFIG_}。普通包装类会导致配置无法注入，传播失效。
 * <p>
 * 另：附件识别「待确认」当轮拦截写入类工具，避免未确认就落库。
 */
@Slf4j
public final class AuthPropagatingToolCallback implements StateAwareToolCallback {

    private static final Set<String> WRITE_TOOLS = Set.of(
            "addExpense", "addIncome", "transferMoney", "updateFlow", "deleteFlow",
            "repayCreditCard", "toggleFavorite",
            "createAccount", "updateAccount", "deleteAccount",
            "createType", "updateType", "deleteType");

    private static final String CONFIRM_BLOCK_MESSAGE =
            "本轮为附件识别确认阶段，禁止写入。请先向用户展示待确认清单并等待确认；"
                    + "用户明确确认后，在下一轮再调用写入工具。";

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
        Integer previousUser = AuthContext.getUserIdOrNull();
        Boolean previousConfirm = AuthContext.getAttachmentConfirmOnlyOrNull();
        try {
            if (AuthContext.getUserIdOrNull() == null) {
                Integer resolved = resolveUserId(toolContext);
                if (resolved != null) {
                    AuthContext.setUserId(resolved);
                } else {
                    log.warn("[AuthPropagating] 无法从 ToolContext 解析 userId，tool={}",
                            getToolDefinition() != null ? getToolDefinition().name() : "?");
                }
            }

            Boolean confirmOnly = resolveAttachmentConfirmOnly(toolContext);
            if (confirmOnly != null) {
                AuthContext.setAttachmentConfirmOnly(confirmOnly);
            }

            String toolName = getToolDefinition() != null ? getToolDefinition().name() : "";
            if (AuthContext.isAttachmentConfirmOnly() && WRITE_TOOLS.contains(toolName)) {
                log.info("[AuthPropagating] 拦截确认阶段写入 tool={}", toolName);
                return CONFIRM_BLOCK_MESSAGE;
            }

            if (toolContext != null) {
                return delegate.call(toolInput, toolContext);
            }
            return delegate.call(toolInput);
        } finally {
            if (previousUser == null) {
                AuthContext.clear();
            } else {
                AuthContext.setUserId(previousUser);
            }
            AuthContext.setAttachmentConfirmOnly(previousConfirm);
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

    private static Boolean resolveAttachmentConfirmOnly(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        return ToolContextHelper.getConfig(toolContext)
                .or(() -> readConfigDirect(toolContext))
                .map(AuthPropagatingToolCallback::confirmOnlyFromConfig)
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

    private static Boolean confirmOnlyFromConfig(RunnableConfig config) {
        Object meta = config.metadata(AuthContext.METADATA_ATTACHMENT_CONFIRM_ONLY).orElse(null);
        if (meta instanceof Boolean b) {
            return b;
        }
        if (meta instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return null;
    }
}
