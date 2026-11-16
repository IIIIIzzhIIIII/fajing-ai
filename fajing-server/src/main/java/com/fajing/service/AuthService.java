package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.AppUser;
import com.fajing.mapper.AppUserMapper;
import com.fajing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 微信登录：code → openid → 用户建档 → JWT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 游客标识：浏览器本地生成，只允许安全字符，避免写入异常 openid */
    private static final Pattern GUEST_KEY = Pattern.compile("^[A-Za-z0-9_-]{16,48}$");

    private final AppUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RateLimitService rateLimitService;

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    /** H5 游客登录开关：默认开启，生产也保留（浏览器匿名账号，用于 H5 端可用登录入口） */
    @Value("${auth.guest-enabled:true}")
    private boolean guestEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Map<String, Object> loginByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("缺少微信登录凭证");
        }
        if (isBlank(appid) || isBlank(secret)) {
            throw new BizException("微信登录未配置");
        }
        JsonNode session = jscode2session(code);
        String openid = session.path("openid").asText("");
        if (openid.isBlank()) {
            throw new BizException("微信登录失败，请重试");
        }
        String sessionKey = session.path("session_key").asText("");

        AppUser user = userMapper.selectOne(new QueryWrapper<AppUser>()
                .eq("openid", openid).last("limit 1"));
        if (user == null) {
            user = new AppUser();
            user.setOpenid(openid);
            user.setNickname("发型试镜用户");
            user.setGender("unknown");
            user.setStatus(1);
            user.setRegisterAt(LocalDateTime.now());
            user.setLastActiveAt(LocalDateTime.now());
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                // 同一 openid 并发首次登录时可能同时建档，回读已存在账号，避免 500
                user = userMapper.selectOne(new QueryWrapper<AppUser>()
                        .eq("openid", openid).last("limit 1"));
                if (user == null) {
                    throw e;
                }
            }
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被限制使用");
        }
        user.setLastActiveAt(LocalDateTime.now());
        userMapper.updateById(user);
        // session_key 只落库，不下发前端
        if (!sessionKey.isBlank()) {
            user.setSessionKey(sessionKey);
            userMapper.updateById(user);
        }
        return tokenPayload(user);
    }

    /** 本地/H5 演示登录：固定体验用户，不调用微信 */
    public Map<String, Object> mockLogin() {
        AppUser user = userMapper.selectById(1L);
        if (user == null) {
            user = new AppUser();
            user.setNickname("发镜体验用户");
            user.setGender("female");
            user.setStatus(1);
            user.setRegisterAt(LocalDateTime.now());
            user.setLastActiveAt(LocalDateTime.now());
            userMapper.insert(user);
        }
        return tokenPayload(user);
    }

    /**
     * H5 游客登录：浏览器本地保存的 guestKey 换取匿名账号。
     * 同一浏览器复用同一账号，额度与记录都按账号隔离；新账号按 IP 限流，避免批量注册。
     */
    public Map<String, Object> guestLogin(String guestKey, String ip) {
        if (!guestEnabled) {
            throw new BizException("游客登录未开启");
        }
        String key = guestKey == null ? "" : guestKey.trim();
        if (!GUEST_KEY.matcher(key).matches()) {
            throw new BizException("游客标识无效，请刷新页面后重试");
        }
        String openid = "guest:" + key;
        AppUser user = findByOpenid(openid);
        if (user == null) {
            rateLimitService.checkGuestCreate(ip);
            user = new AppUser();
            user.setOpenid(openid);
            user.setNickname("游客" + key.substring(key.length() - 4));
            user.setGender("unknown");
            user.setStatus(1);
            user.setRegisterAt(LocalDateTime.now());
            user.setLastActiveAt(LocalDateTime.now());
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                // 同一浏览器并发发起登录时可能同时建档，取已存在的账号
                user = findByOpenid(openid);
                if (user == null) {
                    throw e;
                }
            }
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BizException("账号已被限制使用");
            }
            user.setLastActiveAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
        return tokenPayload(user);
    }

    private AppUser findByOpenid(String openid) {
        return userMapper.selectOne(new QueryWrapper<AppUser>()
                .eq("openid", openid).last("limit 1"));
    }

    public Map<String, Object> me(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("gender", user.getGender());
        return data;
    }

    private Map<String, Object> tokenPayload(AppUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtUtil.create(user.getId(), "USER"));
        data.put("userId", user.getId());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        return data;
    }

    private JsonNode jscode2session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid="
                + URLEncoder.encode(appid, StandardCharsets.UTF_8)
                + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            int errcode = root.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("jscode2session 失败：{}", resp.body());
                throw new BizException(loginErrorMessage(errcode));
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("jscode2session 调用异常：{}", e.getMessage());
            throw new BizException("微信登录失败，请检查网络后重试");
        }
    }

    private String loginErrorMessage(int errcode) {
        return switch (errcode) {
            case 40029 -> "登录凭证已失效，请重新进入小程序";
            case 40163 -> "登录凭证已被使用，请重新进入小程序";
            case 40125 -> "微信 AppSecret 配置不正确";
            case 45011 -> "登录过于频繁，请稍后再试";
            default -> "微信登录失败（" + errcode + "）";
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
