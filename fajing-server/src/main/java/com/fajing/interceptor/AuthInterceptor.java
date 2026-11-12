package com.fajing.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.Result;
import com.fajing.util.JwtUtil;
import com.fajing.util.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * C 端鉴权：/api/** 需要 Authorization: Bearer
 * /api/auth/** 与 /api/admin/** 由各自逻辑处理
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        Claims claims = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            claims = jwtUtil.parse(auth.substring(7).trim());
        }
        if (claims == null) {
            unauthorized(response);
            return false;
        }
        try {
            UserContext.set(Long.valueOf(claims.getSubject()));
        } catch (NumberFormatException e) {
            unauthorized(response);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail("登录已过期，请重新登录")));
    }
}
