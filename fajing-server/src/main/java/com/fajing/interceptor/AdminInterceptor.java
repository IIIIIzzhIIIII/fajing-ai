package com.fajing.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.Result;
import com.fajing.entity.AdminUser;
import com.fajing.mapper.AdminUserMapper;
import com.fajing.util.AdminContext;
import com.fajing.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端鉴权：
 * - 平台总管理员（PLATFORM_ADMIN）可访问全部 /api/admin/**
 * - 店长（STORE_MANAGER）只能访问 /api/admin/store/**
 * 角色与 tenantId/storeId 以数据库为准，不信任前端传参。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private static final String STORE_PATH_PREFIX = "/api/admin/store";

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;

    @Value("${fajing.admin-token:}")
    private String legacyToken;

    @Value("${admin.legacy-token-enabled:false}")
    private boolean legacyEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Claims claims = jwtUtil.parse(auth.substring(7).trim());
            if (claims != null && "ADMIN".equals(String.valueOf(claims.get("role")))) {
                Long adminId = null;
                try {
                    adminId = Long.valueOf(claims.getSubject());
                } catch (NumberFormatException ignored) {
                    // 非法 subject 直接拒绝
                }
                if (adminId != null && adminId > 0) {
                    AdminUser admin = adminUserMapper.selectById(adminId);
                    if (admin != null && admin.getStatus() != null && admin.getStatus() == 1) {
                        String role = normalizeRole(admin.getRole());
                        Long tenantId = admin.getTenantId() == null ? 0L : admin.getTenantId();
                        Long storeId = admin.getStoreId() == null ? 0L : admin.getStoreId();
                        // 平台总管理员查看门店数据时，允许通过 X-Store-Id 指定门店
                        if ("PLATFORM_ADMIN".equals(role)) {
                            Long requested = parseLong(request.getHeader("X-Store-Id"));
                            if (requested != null && requested > 0) {
                                storeId = requested;
                            }
                        }
                        String path = request.getRequestURI();
                        if ("STORE_MANAGER".equals(role)) {
                            if (tenantId <= 0 || storeId <= 0) {
                                return reject(response, HttpServletResponse.SC_FORBIDDEN, "店长账号未绑定商户或门店");
                            }
                            if (!path.startsWith(STORE_PATH_PREFIX)) {
                                return reject(response, HttpServletResponse.SC_FORBIDDEN, "店长账号无权访问平台功能");
                            }
                        } else if (!"PLATFORM_ADMIN".equals(role)) {
                            return reject(response, HttpServletResponse.SC_FORBIDDEN, "未知的管理员角色");
                        }
                        AdminContext.set(adminId, admin.getUsername(), role, tenantId, storeId,
                                admin.getDisplayName() == null ? admin.getUsername() : admin.getDisplayName());
                        request.setAttribute("adminId", adminId);
                        return true;
                    }
                }
                return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期");
            }
        }
        if (legacyEnabled && legacyToken != null && !legacyToken.isBlank()
                && legacyToken.equals(request.getHeader("X-Admin-Token"))) {
            AdminContext.set(0L, "legacy", "PLATFORM_ADMIN", 0L, 0L, "兼容管理员");
            return true;
        }
        return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AdminContext.clear();
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank() || "ADMIN".equalsIgnoreCase(role)) {
            return "PLATFORM_ADMIN";
        }
        return role.trim().toUpperCase();
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(message)));
        return false;
    }
}
