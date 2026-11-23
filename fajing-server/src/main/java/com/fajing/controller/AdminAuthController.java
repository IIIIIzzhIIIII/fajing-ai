package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AdminUser;
import com.fajing.entity.Store;
import com.fajing.mapper.AdminUserMapper;
import com.fajing.mapper.StoreMapper;
import com.fajing.service.AdminLogService;
import com.fajing.service.RateLimitService;
import com.fajing.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端登录：账号密码 → JWT。
 * 平台总管理员与店长共用登录入口，角色由 admin_user.role 决定。
 */
@Tag(name = "管理端 · 登录", description = "账号密码登录换取 JWT（平台总管理员 / 店长）")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminUserMapper adminUserMapper;
    private final StoreMapper storeMapper;
    private final JwtUtil jwtUtil;
    private final RateLimitService rateLimitService;
    private final AdminLogService adminLogService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                             HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimitService.checkLogin("admin", ip);
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new BizException("请输入账号和密码");
        }
        AdminUser user = adminUserMapper.selectOne(new QueryWrapper<AdminUser>()
                .eq("username", username.trim()).last("limit 1"));
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            // 记录失败审计，便于发现撞库；不记录密码
            adminLogService.log("ADMIN_LOGIN_FAIL", username.trim() + ",ip:" + ip);
            throw new BizException("账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("该管理员账号已被停用");
        }
        String role = normalizeRole(user.getRole());
        Long tenantId = user.getTenantId() == null ? 0L : user.getTenantId();
        Long storeId = user.getStoreId() == null ? 0L : user.getStoreId();
        String storeName = "";
        if ("STORE_MANAGER".equals(role)) {
            if (tenantId <= 0 || storeId <= 0) {
                throw new BizException("店长账号未绑定商户或门店，请联系平台管理员");
            }
            Store store = storeMapper.selectById(storeId);
            if (store == null || store.getStatus() == null || store.getStatus() != 1) {
                throw new BizException("门店已停用，请联系平台管理员");
            }
            storeName = store.getName();
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("adminRole", role);
        claims.put("tenantId", tenantId);
        claims.put("storeId", storeId);
        claims.put("username", user.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtUtil.create(user.getId(), "ADMIN", claims));
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName());
        data.put("role", role);
        data.put("tenantId", tenantId);
        data.put("storeId", storeId);
        data.put("storeName", storeName);
        return Result.ok(data);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank() || "ADMIN".equalsIgnoreCase(role)) {
            return "PLATFORM_ADMIN";
        }
        return role.trim().toUpperCase();
    }

    /** 取真实客户端 IP：仅当直连方是受信代理（本机/内网）时才采信转发头 */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean trusted = "127.0.0.1".equals(remote) || "::1".equals(remote)
                || "0:0:0:0:0:0:0:1".equals(remote)
                || remote.startsWith("10.") || remote.startsWith("192.168.")
                || remote.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*");
        if (!trusted) {
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
}
