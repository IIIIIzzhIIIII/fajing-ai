package com.fajing.util;

/**
 * 当前登录用户上下文，由 AuthInterceptor 在请求进入时写入、结束时清理
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("当前请求没有登录用户");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
