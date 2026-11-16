package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.AuthService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "登录", description = "微信登录与当前用户信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 小程序：wx.login 的 code 换本系统 token */
    @Operation(summary = "小程序 wx.login 的 code 换 token")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        return Result.ok(authService.loginByCode(code));
    }

    @Operation(summary = "H5 游客登录：浏览器本地 guestKey 换匿名账号 token")
    @PostMapping("/login/guest")
    public Result<Map<String, Object>> guestLogin(@RequestBody(required = false) Map<String, String> body,
                                                  HttpServletRequest request) {
        String guestKey = body == null ? null : body.get("guestKey");
        return Result.ok(authService.guestLogin(guestKey, clientIp(request)));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me(UserContext.require()));
    }

    @Operation(summary = "退出登录（客户端清除 token）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    /** 取真实客户端 IP：仅当直连方是受信代理（本机/内网）时才采信 X-Real-IP / X-Forwarded-For，避免伪造头绕过限流 */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                ip = forwarded.split(",")[0].trim();
            }
        }
        return ip == null || ip.isBlank() ? remote : ip;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        return ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }
}
