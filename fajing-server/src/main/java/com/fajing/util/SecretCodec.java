package com.fajing.util;

import com.fajing.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 支付密钥加解密：AES-256-GCM，口令来自环境变量 PAY_CONFIG_SECRET。
 * 密文格式：Base64(12 字节 IV + 密文 + 16 字节 Tag)。
 */
@Component
public class SecretCodec {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    @Value("${pay.config-secret:}")
    private String secret;

    private final SecureRandom random = new SecureRandom();

    public boolean configured() {
        return secret != null && secret.trim().length() >= 32;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        byte[] key = key();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BizException("支付密钥加密失败，请检查 PAY_CONFIG_SECRET");
        }
    }

    /** 解密失败返回 null（用于掩码展示时容错） */
    public String decryptOrNull(String cipherText) {
        if (cipherText == null || cipherText.isBlank() || !configured()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText);
            if (raw.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] body = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] key() {
        if (!configured()) {
            throw new BizException("未配置 PAY_CONFIG_SECRET（至少 32 位），无法保存支付密钥");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BizException("PAY_CONFIG_SECRET 初始化失败");
        }
    }
}
