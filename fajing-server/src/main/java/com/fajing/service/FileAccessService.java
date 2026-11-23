package com.fajing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 本地图片访问控制：
 * - ai_ 开头的用户生成图视为私有资源，必须携带短时签名才能访问；
 * - ai_model_ 是管理端标准模特照、style- 是发型库封面，保持公开；
 * - 其它（用户上传的 UUID 文件、头像）暂保持公开。
 */
@Service
public class FileAccessService {

    private static final String UPLOAD_MARKER = "/uploads/";
    private static final String PRIVATE_PREFIX = "ai_";
    private static final String MODEL_PREFIX = "ai_model_";

    @Value("${token.secret:}")
    private String secret;

    /** 签名有效期，默认 2 小时；页面每次拉取列表都会重新签名 */
    @Value("${fajing.file-url-ttl-seconds:7200}")
    private long ttlSeconds;

    /** 给私有图片 URL 追加 ?e=&s=，公开图片原样返回 */
    public String sign(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        int marker = url.indexOf(UPLOAD_MARKER);
        if (marker < 0) {
            return url;
        }
        String rest = url.substring(marker + UPLOAD_MARKER.length());
        int q = rest.indexOf('?');
        String name = q > -1 ? rest.substring(0, q) : rest;
        if (!isPrivate(name)) {
            return url;
        }
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String sig = hmac(name + ":" + exp);
        return url.substring(0, marker) + UPLOAD_MARKER + name + "?e=" + exp + "&s=" + sig;
    }

    /** 校验文件名对应的签名是否有效且未过期 */
    public boolean verify(String name, Long exp, String signature) {
        if (!isPrivate(name)) {
            return true;
        }
        if (exp == null || signature == null || signature.isBlank()) {
            return false;
        }
        if (exp < Instant.now().getEpochSecond()) {
            return false;
        }
        String expected = hmac(name + ":" + exp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isPrivate(String name) {
        return name != null && name.startsWith(PRIVATE_PREFIX) && !name.startsWith(MODEL_PREFIX);
    }

    private String hmac(String message) {
        String key = secret == null ? "" : secret;
        if (key.isBlank()) {
            throw new IllegalStateException("token.secret 未配置，无法签名图片地址");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("图片地址签名失败", e);
        }
    }
}
