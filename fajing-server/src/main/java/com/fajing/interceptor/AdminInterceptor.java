package com.fajing.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.Result;
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
 * 管理端鉴权：优先校验 Authorization: Bearer（role=ADMIN）
 * 旧的 X-Admin-Token 仅作兼容，默认关闭，由 admin.legacy-token-enabled 控制
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

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
                try {
                    request.setAttribute("adminId", Long.valueOf(claims.getSubject()));
                } catch (NumberFormatException ignored) {
                    request.setAttribute("adminId", 0L);
                }
                return true;
            }
        }
        if (legacyEnabled && legacyToken != null && !legacyToken.isBlank()
                && legacyToken.equals(request.getHeader("X-Admin-Token"))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail("未登录或登录已过期")));
        return false;
    }
}
