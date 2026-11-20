package com.fajing.util;

import com.fajing.common.BizException;

/**
 * 管理端登录上下文：由 AdminInterceptor 在校验 JWT 后写入，请求结束后清理。
 * 平台总管理员与店长共用同一套后台，但数据范围不同。
 */
public final class AdminContext {

    public record Session(Long adminId, String username, String role, Long tenantId,
                          Long storeId, String displayName) {
    }

    private static final ThreadLocal<Session> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(Long adminId, String username, String role, Long tenantId,
                           Long storeId, String displayName) {
        HOLDER.set(new Session(adminId, username, role, tenantId, storeId, displayName));
    }

    public static Session get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static boolean isPlatform() {
        Session session = get();
        return session != null && "PLATFORM_ADMIN".equals(session.role());
    }

    public static void requirePlatform() {
        if (!isPlatform()) {
            throw new BizException("仅平台总管理员可操作");
        }
    }

    public static Long requireStoreId() {
        Session session = get();
        if (session == null) {
            throw new BizException("未登录或登录已过期");
        }
        Long storeId = session.storeId();
        if (storeId == null || storeId <= 0) {
            throw new BizException("当前账号未绑定门店");
        }
        return storeId;
    }

    public static Long requireTenantId() {
        Session session = get();
        if (session == null) {
            throw new BizException("未登录或登录已过期");
        }
        Long tenantId = session.tenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new BizException("当前账号未绑定商户");
        }
        return tenantId;
    }

    public static Long adminId() {
        Session session = get();
        return session == null ? 0L : session.adminId();
    }
}
