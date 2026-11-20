package com.fajing.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）
 * 密钥从环境变量 TOKEN_SECRET 注入，长度至少 32 位；未配置时启动即失败，避免用默认弱密钥上线。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${token.secret:}") String secret,
                   @Value("${token.expire-days:7}") long expireDays) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("token.secret 未配置或长度不足 32 位，请设置环境变量 TOKEN_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.trim().getBytes(StandardCharsets.UTF_8));
        this.expireMillis = Math.max(1, expireDays) * 24L * 3600 * 1000;
    }

    public String create(Long userId, String role) {
        return create(userId, role, java.util.Map.of());
    }

    /** 额外 claims 用于管理端携带 adminRole / tenantId / storeId，避免前端伪造身份 */
    public String create(Long userId, String role, java.util.Map<String, Object> extraClaims) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis));
        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }
        return builder.signWith(key).compact();
    }

    /** 校验失败（过期、篡改、格式错误）返回 null，由调用方决定拒绝方式 */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
