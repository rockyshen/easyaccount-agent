package com.rockyshen.easyaccountagent.auth;

/**
 * 当前请求/WS 工作线程上的登录用户。
 * WS 异步 worker 必须在入口 set、finally clear。
 */
public final class AuthContext {

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
}
