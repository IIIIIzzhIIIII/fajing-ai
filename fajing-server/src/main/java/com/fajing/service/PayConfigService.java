package com.fajing.service;

import com.fajing.common.BizException;
import com.fajing.entity.PayConfig;
import com.fajing.mapper.PayConfigMapper;
import com.fajing.util.SecretCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付配置读取/保存：数据库唯一来源，敏感字段加密存储。
 * 参照 AiConfigService：GET 掩码、PUT 掩码不回写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayConfigService {

    private static final Long CONFIG_ID = 1L;
    public static final String DEFAULT_GATEWAY = "https://openapi.alipay.com/gateway.do";

    private final PayConfigMapper payConfigMapper;
    private final SecretCodec codec;

    public PayConfig get() {
        PayConfig cfg = payConfigMapper.selectById(CONFIG_ID);
        if (cfg == null) {
            cfg = defaults();
            try {
                payConfigMapper.insert(cfg);
            } catch (Exception e) {
                log.warn("支付配置初始化失败，返回内存默认值: {}", e.getMessage());
            }
        }
        return cfg;
    }

    public PayConfig save(PayConfig req, Long adminId) {
        PayConfig cfg = get();
        // 普通字段：非空才覆盖
        if (req.getWxEnabled() != null) cfg.setWxEnabled(req.getWxEnabled());
        if (notBlank(req.getWxPayMode())) cfg.setWxPayMode(req.getWxPayMode().trim());
        if (notBlank(req.getWxAppid())) cfg.setWxAppid(req.getWxAppid().trim());
        if (notBlank(req.getWxMchId())) cfg.setWxMchId(req.getWxMchId().trim());
        if (notBlank(req.getWxCertSerialNo())) cfg.setWxCertSerialNo(req.getWxCertSerialNo().trim());
        if (notBlank(req.getWxPrivateKeyPath())) cfg.setWxPrivateKeyPath(req.getWxPrivateKeyPath().trim());
        if (notBlank(req.getWxNotifyUrl())) cfg.setWxNotifyUrl(req.getWxNotifyUrl().trim());

        if (req.getAlipayEnabled() != null) cfg.setAlipayEnabled(req.getAlipayEnabled());
        if (notBlank(req.getAlipayAppId())) cfg.setAlipayAppId(req.getAlipayAppId().trim());
        if (notBlank(req.getAlipayGateway())) cfg.setAlipayGateway(req.getAlipayGateway().trim());
        if (notBlank(req.getAlipayNotifyUrl())) cfg.setAlipayNotifyUrl(req.getAlipayNotifyUrl().trim());
        if (notBlank(req.getAlipayReturnUrl())) cfg.setAlipayReturnUrl(req.getAlipayReturnUrl().trim());

        // 敏感字段：空值或掩码值视为未修改
        cfg.setWxApiV3KeyEnc(applySecret(req.getWxApiV3KeyEnc(), cfg.getWxApiV3KeyEnc()));
        cfg.setWxVirtualAppkeyEnc(applySecret(req.getWxVirtualAppkeyEnc(), cfg.getWxVirtualAppkeyEnc()));
        cfg.setAlipayPrivateKeyEnc(applySecret(req.getAlipayPrivateKeyEnc(), cfg.getAlipayPrivateKeyEnc()));
        cfg.setAlipayPublicKeyEnc(applySecret(req.getAlipayPublicKeyEnc(), cfg.getAlipayPublicKeyEnc()));

        cfg.setId(CONFIG_ID);
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(adminId == null ? 0L : adminId);
        payConfigMapper.updateById(cfg);
        return cfg;
    }

    private String applySecret(String input, String oldCipher) {
        if (input == null || input.isBlank() || input.contains("*")) {
            return oldCipher;
        }
        if (!codec.configured()) {
            throw new BizException("未配置 PAY_CONFIG_SECRET（至少 32 位），无法保存密钥");
        }
        return codec.encrypt(input.trim());
    }

    /** 返回给管理端的视图：密钥只露前 4 后 4 */
    public PayConfig masked(PayConfig cfg) {
        PayConfig view = new PayConfig();
        view.setId(cfg.getId());
        view.setWxEnabled(cfg.getWxEnabled());
        view.setWxPayMode(cfg.getWxPayMode());
        view.setWxAppid(cfg.getWxAppid());
        view.setWxMchId(cfg.getWxMchId());
        view.setWxCertSerialNo(cfg.getWxCertSerialNo());
        view.setWxPrivateKeyPath(cfg.getWxPrivateKeyPath());
        view.setWxNotifyUrl(cfg.getWxNotifyUrl());
        view.setAlipayEnabled(cfg.getAlipayEnabled());
        view.setAlipayAppId(cfg.getAlipayAppId());
        view.setAlipayGateway(cfg.getAlipayGateway());
        view.setAlipayNotifyUrl(cfg.getAlipayNotifyUrl());
        view.setAlipayReturnUrl(cfg.getAlipayReturnUrl());
        view.setUpdatedAt(cfg.getUpdatedAt());
        view.setUpdatedBy(cfg.getUpdatedBy());

        view.setWxApiV3KeyEnc(mask(codec.decryptOrNull(cfg.getWxApiV3KeyEnc())));
        view.setWxVirtualAppkeyEnc(mask(codec.decryptOrNull(cfg.getWxVirtualAppkeyEnc())));
        view.setAlipayPrivateKeyEnc(mask(codec.decryptOrNull(cfg.getAlipayPrivateKeyEnc())));
        view.setAlipayPublicKeyEnc(mask(codec.decryptOrNull(cfg.getAlipayPublicKeyEnc())));
        return view;
    }

    public boolean wxReady(PayConfig cfg) {
        return cfg != null && Integer.valueOf(1).equals(cfg.getWxEnabled())
                && notBlank(cfg.getWxAppid())
                && notBlank(cfg.getWxMchId())
                && notBlank(codec.decryptOrNull(cfg.getWxApiV3KeyEnc()))
                && notBlank(cfg.getWxCertSerialNo())
                && notBlank(cfg.getWxPrivateKeyPath())
                && notBlank(cfg.getWxNotifyUrl());
    }

    public boolean alipayReady(PayConfig cfg) {
        return cfg != null && Integer.valueOf(1).equals(cfg.getAlipayEnabled())
                && notBlank(cfg.getAlipayAppId())
                && notBlank(codec.decryptOrNull(cfg.getAlipayPrivateKeyEnc()))
                && notBlank(codec.decryptOrNull(cfg.getAlipayPublicKeyEnc()))
                && notBlank(cfg.getAlipayGateway());
    }

    public String wxApiV3Key(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getWxApiV3KeyEnc());
    }

    public String wxVirtualAppkey(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getWxVirtualAppkeyEnc());
    }

    public String alipayPrivateKey(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getAlipayPrivateKeyEnc());
    }

    public String alipayPublicKey(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getAlipayPublicKeyEnc());
    }

    private PayConfig defaults() {
        PayConfig cfg = new PayConfig();
        cfg.setId(CONFIG_ID);
        cfg.setWxEnabled(0);
        cfg.setWxPayMode("virtual");
        cfg.setWxAppid("");
        cfg.setWxMchId("");
        cfg.setWxApiV3KeyEnc("");
        cfg.setWxCertSerialNo("");
        cfg.setWxPrivateKeyPath("");
        cfg.setWxNotifyUrl("");
        cfg.setWxVirtualAppkeyEnc("");
        cfg.setAlipayEnabled(0);
        cfg.setAlipayAppId("");
        cfg.setAlipayPrivateKeyEnc("");
        cfg.setAlipayPublicKeyEnc("");
        cfg.setAlipayGateway(DEFAULT_GATEWAY);
        cfg.setAlipayNotifyUrl("");
        cfg.setAlipayReturnUrl("");
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(0L);
        return cfg;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String mask(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        if (plain.length() <= 8) {
            return "****";
        }
        return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
    }
}
