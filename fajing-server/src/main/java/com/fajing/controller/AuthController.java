package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.AuthService;
import com.fajing.service.RateLimitService;
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
    private final RateLimitService rateLimitService;

    /** 小程序：wx.login 的 code 换本系统 token */
    @Operation(summary = "小程序 wx.login 的 code 换 token")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                             HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimitService.checkLogin("wx", ip);
        String code = body == null ? null : body.get("code");
        return Result.ok(authService.loginByCode(code));
    }

    @Operation(summary = "H5 游客登录：浏览器本地 guestKey 换匿名账号 token")
    @PostMapping("/login/guest")
    public Result<Map<String, Object>> guestLogin(@RequestBody(required = false) Map<String, String> body,
                                                  HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimitService.checkLogin("guest", ip);
        String guestKey = body == null ? null : body.get("guestKey");
        return Result.ok(authService.guestLogin(guestKey, ip));
    }

    @Operation(summary = "公众号网页授权配置：前端用来拼 authorize 跳转")
    @GetMapping("/wechat/oauth/config")
    public Result<Map<String, Object>> oauthConfig() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("enabled", authService.oauthEnabled());
        data.put("appid", authService.oauthAppid());
        return Result.ok(data);
    }

    @Operation(summary = "公众号网页授权 code 换 token（H5 支付前拿 openid）")
    @PostMapping("/wechat/oauth")
    public Result<Map<String, Object>> oauthLogin(@RequestBody(required = false) Map<String, String> body,
                                                  HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimitService.checkLogin("oauth", ip);
        String code = body == null ? null : body.get("code");
        return Result.ok(authService.loginByOauthCode(code));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me(UserContext.require()));
    }

    @Operation(summary = "更新昵称 / 头像")
    @PostMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody(required = false) Map<String, String> body) {
        String nickname = body == null ? null : body.get("nickname");
        String avatar = body == null ? null : body.get("avatar");
        return Result.ok(authService.updateProfile(UserContext.require(), nickname, avatar));
    }

    @Operation(summary = "绑定微信手机号（getPhoneNumber 返回的 code）")
    @PostMapping("/phone")
    public Result<Map<String, Object>> bindPhone(@RequestBody(required = false) Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        return Result.ok(authService.bindPhone(UserContext.require(), code));
    }

    @Operation(summary = "H5 手动绑定手机号（无短信验证，带限流）")
    @PostMapping("/phone/manual")
    public Result<Map<String, Object>> bindPhoneManual(@RequestBody(required = false) Map<String, String> body,
                                                       HttpServletRequest request) {
        // 没有短信通道，至少按 IP 限流，避免被批量写入垃圾号码
        rateLimitService.checkLogin("phone", clientIp(request));
        String phone = body == null ? null : body.get("phone");
        return Result.ok(authService.bindPhoneManual(UserContext.require(), phone));
    }

    @Operation(summary = "退出登录（客户端清除 token）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    @Operation(summary = "注销账号：删除照片与记录并停用账号")
    @PostMapping("/cancel")
    public Result<Void> cancel() {
        authService.cancelAccount(UserContext.require());
        return Result.ok(null);
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
