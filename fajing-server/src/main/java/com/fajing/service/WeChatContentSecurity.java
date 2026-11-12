package com.fajing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
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
import java.util.List;
import java.util.Map;

/**
 * UGC 内容安全：
 * 1) 本地敏感词兜底（未配置微信或接口异常时生效）
 * 2) 配置了 wechat.appid/secret 时调用微信 msgSecCheck v2
 */
@Slf4j
@Service
public class WeChatContentSecurity {

    private static final List<String> LOCAL_WORDS = List.of(
            "代开发票", "赌博", "博彩", "色情", "约炮", "枪支", "毒品", "法轮功",
            "傻逼", "操你", "去死", "加微信领", "刷单返利");

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${content-security.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private volatile String accessToken;
    private volatile long tokenExpireAt;

    public void check(String text, String openid) {
        if (!enabled || text == null || text.isBlank()) {
            return;
        }
        if (hitLocal(text)) {
            throw new BizException("内容可能不合规，请修改后再发");
        }
        if (isBlank(appid) || isBlank(secret)) {
            return;
        }
        try {
            if (!remotePass(text, openid)) {
                throw new BizException("内容可能不合规，请修改后再发");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("微信内容安全接口调用失败，降级为本地词表：{}", e.getMessage());
        }
    }

    private boolean hitLocal(String text) {
        String lower = text.toLowerCase();
        return LOCAL_WORDS.stream().anyMatch(lower::contains);
    }

    private boolean remotePass(String text, String openid) throws Exception {
        String token = accessToken();
        if (token == null) {
            return true;
        }
        String url = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        Map<String, Object> body = Map.of(
                "content", text,
                "version", 2,
                "scene", 2,
                "openid", openid == null ? "" : openid);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = objectMapper.readTree(resp.body());
        if (root.path("errcode").asInt(0) != 0) {
            throw new IllegalStateException("msgSecCheck errcode=" + root.path("errcode").asInt());
        }
        String suggest = root.path("result").path("suggest").asText("");
        return !"risky".equalsIgnoreCase(suggest) && !"review".equalsIgnoreCase(suggest);
    }

    private String accessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return accessToken;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + URLEncoder.encode(appid, StandardCharsets.UTF_8)
                + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = objectMapper.readTree(resp.body());
        String token = root.path("access_token").asText("");
        if (token.isBlank()) {
            log.warn("获取微信 access_token 失败：{}", resp.body());
            return null;
        }
        accessToken = token;
        tokenExpireAt = System.currentTimeMillis() + Math.max(60, root.path("expires_in").asLong(7200) - 300) * 1000;
        return token;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
