package com.rockyshen.easyaccountagent.auth;

/**
 * 当前登录用户上下文。
 * <p>
 * WS worker 上的 {@link ThreadLocal} 在 Agent 工具线程上不可见
 * （ReactAgent 常通过 AsyncToolCallbackAdapter 在其它线程执行工具），
 * 因此工具回调须通过 {@link AuthPropagatingToolCallback}（且为 StateAware）
 * 从 RunnableConfig / threadId（{@code u-{userId}}）注入。
 */
public final class AuthContext {

    public static final String METADATA_USER_ID = "userId";

    private static final ThreadLocal<Integer> USER_ID = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(int userId) {
        USER_ID.set(userId);
    }

    public static int requireUserId() {
        Integer id = USER_ID.get();
        if (id == null) {
            throw new IllegalStateException("未登录或缺少用户上下文");
        }
        return id;
    }

    public static Integer getUserIdOrNull() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }

    /**
     * 从 Agent threadId（约定 {@code u-{userId}}）解析用户 id。
     */
    public static Integer parseUserIdFromThreadId(String threadId) {
        if (threadId == null || !threadId.startsWith("u-") || threadId.length() <= 2) {
            return null;
        }
        try {
            return Integer.parseInt(threadId.substring(2));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
