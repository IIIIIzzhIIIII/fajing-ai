package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.service.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.AppUser;
import com.fajing.entity.EventLog;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.EventLogMapper;
import com.fajing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final EventLogMapper eventLogMapper;
    private final JwtUtil jwtUtil;
    private final RateLimitService rateLimitService;
    private final JdbcTemplate jdbc;
    private final StorageService storageService;

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    /** 公众号（服务号）AppID/Secret：仅用于 H5 网页授权，不能与小程序 AppID 混用 */
    @Value("${wechat.oauth-appid:}")
    private String oauthAppid;

    @Value("${wechat.oauth-secret:}")
    private String oauthSecret;

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

        LoginUser login = upsertByOpenid(openid, "发型试镜用户");
        AppUser user = login.user();
        // session_key 只落库，不下发前端
        if (!sessionKey.isBlank()) {
            user.setSessionKey(sessionKey);
            userMapper.updateById(user);
        }
        return tokenPayload(user, login.isNew());
    }

    /** 公众号（服务号）网页授权是否已配置 */
    public boolean oauthEnabled() {
        return !isBlank(oauthAppid) && !isBlank(oauthSecret);
    }

    public String oauthAppid() {
        return oauthEnabled() ? oauthAppid : "";
    }

    /**
     * H5 公众号网页授权：snsapi_base 静默拿 openid → 建档 → JWT。
     * 与小程序 openid 不互通，同一用户在小程序/公众号下会是两个账号。
     */
    public Map<String, Object> loginByOauthCode(String code) {
        if (isBlank(code)) {
            throw new BizException("缺少微信授权凭证");
        }
        if (!oauthEnabled()) {
            throw new BizException("公众号网页授权未配置");
        }
        JsonNode root = oauthAccessToken(code);
        String openid = root.path("openid").asText("");
        if (openid.isBlank()) {
            throw new BizException("公众号授权失败，请重试");
        }
        LoginUser login = upsertByOpenid(openid, "发型试镜用户");
        return tokenPayload(login.user(), login.isNew());
    }

    private record LoginUser(AppUser user, boolean isNew) {
    }

    /** 按 openid 建档或更新活跃时间；并发首次登录时回读已有账号 */
    private LoginUser upsertByOpenid(String openid, String defaultNickname) {
        AppUser user = findByOpenid(openid);
        boolean isNewUser = false;
        if (user == null) {
            isNewUser = true;
            user = new AppUser();
            user.setOpenid(openid);
            user.setNickname(defaultNickname);
            user.setGender("unknown");
            user.setStatus(1);
            user.setRegisterAt(LocalDateTime.now());
            user.setLastActiveAt(LocalDateTime.now());
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                user = findByOpenid(openid);
                if (user == null) {
                    throw e;
                }
                isNewUser = false;
            }
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被限制使用");
        }
        user.setLastActiveAt(LocalDateTime.now());
        userMapper.updateById(user);
        return new LoginUser(user, isNewUser);
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
        boolean isNewUser = false;
        if (user == null) {
            rateLimitService.checkGuestCreate(ip);
            isNewUser = true;
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
                isNewUser = false;
            }
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BizException("账号已被限制使用");
            }
            user.setLastActiveAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
        return tokenPayload(user, isNewUser);
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
        String phone = user.getPhone() == null ? "" : user.getPhone();
        data.put("phone", phone);
        data.put("phoneBound", !phone.isBlank());
        return data;
    }

    /** 登录返回载荷；isNewUser 仅微信真实登录有意义，mock / 游客登录固定传 false */
    private Map<String, Object> tokenPayload(AppUser user, boolean isNewUser) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", jwtUtil.create(user.getId(), "USER"));
        data.put("userId", user.getId());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("isNewUser", isNewUser);
        return data;
    }

    /** mock / 游客登录共用：不携带 isNewUser（前端忽略） */
    private Map<String, Object> tokenPayload(AppUser user) {
        return tokenPayload(user, false);
    }

    /** 公众号网页授权 code → openid（snsapi_base 只需 openid） */
    private JsonNode oauthAccessToken(String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid="
                + URLEncoder.encode(oauthAppid, StandardCharsets.UTF_8)
                + "&secret=" + URLEncoder.encode(oauthSecret, StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
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
                log.warn("公众号网页授权失败：{}", resp.body());
                throw new BizException(oauthErrorMessage(errcode));
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("公众号网页授权调用异常：{}", e.getMessage());
            throw new BizException("公众号授权失败，请检查网络后重试");
        }
    }

    private String oauthErrorMessage(int errcode) {
        return switch (errcode) {
            case 40029 -> "授权码已失效，请重新进入页面";
            case 40163 -> "授权码已被使用，请重新进入页面";
            case 40013 -> "公众号 AppID 配置不正确";
            case 40125 -> "公众号 AppSecret 配置不正确";
            default -> "公众号授权失败（" + errcode + "）";
        };
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

    // ---- 微信手机号授权 ----

    /** 小程序 access_token 缓存：有效期约 2 小时，提前刷新避免临界失效 */
    private volatile String cachedAccessToken = "";
    private volatile long cachedAccessTokenAt = 0;

    /**
     * 绑定微信手机号：前端 getPhoneNumber 返回的 code → 微信接口换取手机号 → 只更新 phone 字段。
     *
     * @return 更新后的资料（与 /api/auth/me 同结构）
     */
    public Map<String, Object> bindPhone(long userId, String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("缺少手机号授权凭证");
        }
        if (isBlank(appid) || isBlank(secret)) {
            throw new BizException("微信登录未配置，暂时无法授权手机号");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        String phone = fetchPhoneNumber(code);
        if (phone.isBlank()) {
            throw new BizException("未获取到手机号，请重试");
        }
        // 只更新 phone 字段，避免整对象覆盖写坏 openid / session_key
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AppUser>()
                .eq("id", userId)
                .set("phone", phone));
        log.info("用户 {} 绑定手机号 {}", userId, maskPhone(phone));
        return me(userId);
    }

    /**
     * H5 手动绑定手机号：当前没有短信通道，只做格式校验（带登录态与接口限流），
     * 供公众号/H5 授权页在微信内无法调用 getPhoneNumber 时使用。
     */
    public Map<String, Object> bindPhoneManual(long userId, String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("请输入正确的手机号");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException("用户不存在或已注销");
        }
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AppUser>()
                .eq("id", userId)
                .set("phone", value));
        log.info("用户 {} 手动绑定手机号 {}", userId, maskPhone(value));
        return me(userId);
    }

    /** H5 演示绑定手机号：复用正式实现，仅 auth.mock-enabled=true 时由演示接口暴露 */
    public Map<String, Object> bindPhoneMock(long userId, String phone) {
        return bindPhoneManual(userId, phone);
    }

    /**
     * 注销账号：删除照片、试发记录、解锁与推荐数据，账号匿名化并停用。
     * 订单与券包属于交易记录，按《电子商务法》等要求保留，便于对账与售后。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelAccount(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return;
        }
        List<String> photos = jdbc.queryForList(
                "SELECT photo_url FROM generation_task WHERE user_id=? AND photo_url IS NOT NULL AND photo_url<>''",
                String.class, userId);
        for (String url : photos) {
            try {
                storageService.delete(url);
            } catch (Exception e) {
                log.warn("注销账号删除原图失败 userId={}: {}", userId, e.getMessage());
            }
        }
        jdbc.update("DELETE FROM generation_task WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_record WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_unlock WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_color_unlock WHERE user_id=?", userId);
        jdbc.update("DELETE FROM recommendation WHERE user_id=?", userId);
        jdbc.update("DELETE FROM user_quota WHERE user_id=?", userId);
        jdbc.update("DELETE FROM quota_refund WHERE user_id=?", userId);
        jdbc.update("DELETE FROM event_log WHERE user_id=?", userId);
        jdbc.update("DELETE FROM staff_customer_binding WHERE customer_id=?", userId);
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AppUser>()
                .eq("id", userId)
                .set("openid", "deleted_" + userId)
                .set("phone", "")
                .set("nickname", "已注销用户")
                .set("avatar", "")
                .set("session_key", "")
                .set("password_hash", "")
                .set("status", 0)
                .set("last_active_at", LocalDateTime.now()));
        log.info("用户 {} 注销账号，已删除照片与记录，订单与券包按法规保留", userId);
    }

    /** 用 getPhoneNumber 的 code 换取手机号，access_token 失效时自动刷新重试一次 */    private String fetchPhoneNumber(String code) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("code", code));
        } catch (Exception e) {
            throw new BizException("手机号授权失败，请重试");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = wechatAccessToken(attempt > 0);
            String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonNode root = objectMapper.readTree(resp.body());
                int errcode = root.path("errcode").asInt(0);
                if (errcode == 40001 || errcode == 40014 || errcode == 42001) {
                    // access_token 失效：清缓存后重试一次
                    cachedAccessToken = "";
                    cachedAccessTokenAt = 0;
                    continue;
                }
                if (errcode != 0) {
                    log.warn("获取手机号失败：{}", resp.body());
                    throw new BizException(phoneErrorMessage(errcode));
                }
                JsonNode info = root.path("phone_info");
                String pure = info.path("purePhoneNumber").asText("");
                return pure.isBlank() ? info.path("phoneNumber").asText("") : pure;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("获取手机号调用异常：{}", e.getMessage());
                throw new BizException("手机号授权失败，请检查网络后重试");
            }
        }
        throw new BizException("微信授权已过期，请重试");
    }

    private String wechatAccessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && !cachedAccessToken.isBlank() && now - cachedAccessTokenAt < 110 * 60_000L) {
            return cachedAccessToken;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + URLEncoder.encode(appid, StandardCharsets.UTF_8)
                + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            String token = root.path("access_token").asText("");
            if (token.isBlank()) {
                log.warn("获取微信 access_token 失败：{}", resp.body());
                throw new BizException("微信授权服务不可用，请稍后重试");
            }
            cachedAccessToken = token;
            cachedAccessTokenAt = System.currentTimeMillis();
            return token;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("获取微信 access_token 异常：{}", e.getMessage());
            throw new BizException("微信授权服务不可用，请稍后重试");
        }
    }

    private String phoneErrorMessage(int errcode) {
        return switch (errcode) {
            case 40029, 42003 -> "手机号授权凭证已失效，请重新授权";
            case 45011 -> "授权过于频繁，请稍后再试";
            case 48001, 61007 -> "当前小程序未开通手机号授权能力，请联系管理员";
            default -> "手机号授权失败（" + errcode + "）";
        };
    }

    /** 日志脱敏：138****1234 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ---- T2 资料更新 ----

    /** 允许头像 http(s) 或本站 /uploads/ 路径 */
    private static final Pattern ALLOWED_AVATAR = Pattern.compile("^(https?://|/uploads/).+", Pattern.CASE_INSENSITIVE);

    /**
     * 更新用户昵称 / 头像。只更新非空字段，二选一或都传。
     *
     * @return 更新后的资料（与 /api/auth/me 同结构）
     */
    public Map<String, Object> updateProfile(long userId, String nickname, String avatar) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        boolean changed = false;
        Map<String, String> changes = new LinkedHashMap<>();

        if (nickname != null) {
            String cleaned = nickname.trim()
                    .replace("\n", " ").replace("\r", " ")
                    .replace("<", "").replace(">", "");
            if (cleaned.isEmpty()) {
                throw new BizException("昵称不能为空");
            }
            if (cleaned.length() > 20) {
                throw new BizException("昵称不能超过 20 个字符");
            }
            if (!cleaned.equals(user.getNickname())) {
                changes.put("nickname", cleaned);
                user.setNickname(cleaned);
                changed = true;
            }
        }

        if (avatar != null && !avatar.isBlank()) {
            if (!ALLOWED_AVATAR.matcher(avatar).matches()) {
                throw new BizException("头像地址格式不正确");
            }
            if (!avatar.equals(user.getAvatar())) {
                changes.put("avatar", avatar);
                user.setAvatar(avatar);
                changed = true;
            }
        }

        if (changed) {
            // 只更新 nickname / avatar 两个字段，防止整对象覆盖写坏 openid / session_key 等敏感字段
            userMapper.updateById(user);

            // T5：写事件日志（不记昵称原值等敏感信息）
            try {
                EventLog logRow = new EventLog();
                logRow.setUserId(userId);
                logRow.setEvent("profile.update");
                logRow.setPayloadJson(objectMapper.writeValueAsString(changes));
                logRow.setCreatedAt(LocalDateTime.now());
                eventLogMapper.insert(logRow);
            } catch (Exception e) {
                log.warn("写资料更新日志失败：{}", e.getMessage());
            }
        }

        return me(userId);
    }
}
