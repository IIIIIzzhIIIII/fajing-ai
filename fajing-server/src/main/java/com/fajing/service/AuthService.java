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

/**
 * 微信登录：code → openid → 用户建档 → JWT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

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
            userMapper.insert(user);
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BizException("账号已被限制使用");
            }
            user.setLastActiveAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
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
