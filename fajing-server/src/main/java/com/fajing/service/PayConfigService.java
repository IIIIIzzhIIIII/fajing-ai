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
        if (notBlank(req.getWxVirtualOfferId())) cfg.setWxVirtualOfferId(req.getWxVirtualOfferId().trim());
        if (req.getWxVirtualEnv() != null) cfg.setWxVirtualEnv(req.getWxVirtualEnv());
        if (notBlank(req.getWxVirtualProductOne())) cfg.setWxVirtualProductOne(req.getWxVirtualProductOne().trim());
        if (notBlank(req.getWxVirtualProductAll())) cfg.setWxVirtualProductAll(req.getWxVirtualProductAll().trim());

        if (req.getDouyinEnabled() != null) cfg.setDouyinEnabled(req.getDouyinEnabled());
        if (notBlank(req.getDouyinAppId())) cfg.setDouyinAppId(req.getDouyinAppId().trim());
        if (notBlank(req.getDouyinNotifyUrl())) cfg.setDouyinNotifyUrl(req.getDouyinNotifyUrl().trim());
        if (req.getDouyinSandbox() != null) cfg.setDouyinSandbox(req.getDouyinSandbox());

        if (req.getXhsEnabled() != null) cfg.setXhsEnabled(req.getXhsEnabled());
        if (notBlank(req.getXhsAppId())) cfg.setXhsAppId(req.getXhsAppId().trim());
        if (notBlank(req.getXhsMerchantId())) cfg.setXhsMerchantId(req.getXhsMerchantId().trim());
        if (notBlank(req.getXhsNotifyUrl())) cfg.setXhsNotifyUrl(req.getXhsNotifyUrl().trim());

        if (req.getAlipayEnabled() != null) cfg.setAlipayEnabled(req.getAlipayEnabled());
        if (notBlank(req.getAlipayAppId())) cfg.setAlipayAppId(req.getAlipayAppId().trim());
        if (notBlank(req.getAlipayGateway())) cfg.setAlipayGateway(req.getAlipayGateway().trim());
        if (notBlank(req.getAlipayNotifyUrl())) cfg.setAlipayNotifyUrl(req.getAlipayNotifyUrl().trim());
        if (notBlank(req.getAlipayReturnUrl())) cfg.setAlipayReturnUrl(req.getAlipayReturnUrl().trim());

        // 敏感字段：空值或掩码值视为未修改
        cfg.setWxApiV3KeyEnc(applySecret(req.getWxApiV3KeyEnc(), cfg.getWxApiV3KeyEnc()));
        cfg.setWxVirtualAppkeyEnc(applySecret(req.getWxVirtualAppkeyEnc(), cfg.getWxVirtualAppkeyEnc()));
        cfg.setDouyinSecretEnc(applySecret(req.getDouyinSecretEnc(), cfg.getDouyinSecretEnc()));
        cfg.setDouyinSaltEnc(applySecret(req.getDouyinSaltEnc(), cfg.getDouyinSaltEnc()));
        cfg.setDouyinTokenEnc(applySecret(req.getDouyinTokenEnc(), cfg.getDouyinTokenEnc()));
        cfg.setXhsSecretEnc(applySecret(req.getXhsSecretEnc(), cfg.getXhsSecretEnc()));
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
        view.setWxVirtualOfferId(cfg.getWxVirtualOfferId());
        view.setWxVirtualEnv(cfg.getWxVirtualEnv());
        view.setWxVirtualProductOne(cfg.getWxVirtualProductOne());
        view.setWxVirtualProductAll(cfg.getWxVirtualProductAll());
        view.setDouyinEnabled(cfg.getDouyinEnabled());
        view.setDouyinAppId(cfg.getDouyinAppId());
        view.setDouyinNotifyUrl(cfg.getDouyinNotifyUrl());
        view.setDouyinSandbox(cfg.getDouyinSandbox());
        view.setXhsEnabled(cfg.getXhsEnabled());
        view.setXhsAppId(cfg.getXhsAppId());
        view.setXhsMerchantId(cfg.getXhsMerchantId());
        view.setXhsNotifyUrl(cfg.getXhsNotifyUrl());
        view.setAlipayEnabled(cfg.getAlipayEnabled());
        view.setAlipayAppId(cfg.getAlipayAppId());
        view.setAlipayGateway(cfg.getAlipayGateway());
        view.setAlipayNotifyUrl(cfg.getAlipayNotifyUrl());
        view.setAlipayReturnUrl(cfg.getAlipayReturnUrl());
        view.setUpdatedAt(cfg.getUpdatedAt());
        view.setUpdatedBy(cfg.getUpdatedBy());

        view.setWxApiV3KeyEnc(mask(codec.decryptOrNull(cfg.getWxApiV3KeyEnc())));
        view.setWxVirtualAppkeyEnc(mask(codec.decryptOrNull(cfg.getWxVirtualAppkeyEnc())));
        view.setDouyinSecretEnc(mask(codec.decryptOrNull(cfg.getDouyinSecretEnc())));
        view.setDouyinSaltEnc(mask(codec.decryptOrNull(cfg.getDouyinSaltEnc())));
        view.setDouyinTokenEnc(mask(codec.decryptOrNull(cfg.getDouyinTokenEnc())));
        view.setXhsSecretEnc(mask(codec.decryptOrNull(cfg.getXhsSecretEnc())));
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
        // 缺少异步通知地址时，用户付款后收不到回调，订单永远停在 created，因此与微信一致按必填校验
        return cfg != null && Integer.valueOf(1).equals(cfg.getAlipayEnabled())
                && notBlank(cfg.getAlipayAppId())
                && notBlank(codec.decryptOrNull(cfg.getAlipayPrivateKeyEnc()))
                && notBlank(codec.decryptOrNull(cfg.getAlipayPublicKeyEnc()))
                && notBlank(cfg.getAlipayGateway())
                && notBlank(cfg.getAlipayNotifyUrl());
    }

    public String wxApiV3Key(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getWxApiV3KeyEnc());
    }

    /** 微信小程序虚拟支付：用 offerId + AppKey 签名，由客户端拉起 wx.requestVirtualPayment */
    public boolean wxVirtualReady(PayConfig cfg) {
        return cfg != null && Integer.valueOf(1).equals(cfg.getWxEnabled())
                && "virtual".equalsIgnoreCase(cfg.getWxPayMode())
                && notBlank(cfg.getWxVirtualOfferId())
                && notBlank(codec.decryptOrNull(cfg.getWxVirtualAppkeyEnc()));
    }

    public boolean douyinReady(PayConfig cfg) {
        return cfg != null && Integer.valueOf(1).equals(cfg.getDouyinEnabled())
                && notBlank(cfg.getDouyinAppId())
                && notBlank(codec.decryptOrNull(cfg.getDouyinSecretEnc()))
                && notBlank(codec.decryptOrNull(cfg.getDouyinSaltEnc()))
                && notBlank(codec.decryptOrNull(cfg.getDouyinTokenEnc()))
                && notBlank(cfg.getDouyinNotifyUrl());
    }

    public boolean xhsReady(PayConfig cfg) {
        return cfg != null && Integer.valueOf(1).equals(cfg.getXhsEnabled())
                && notBlank(cfg.getXhsAppId())
                && notBlank(codec.decryptOrNull(cfg.getXhsSecretEnc()))
                && notBlank(cfg.getXhsMerchantId())
                && notBlank(cfg.getXhsNotifyUrl());
    }

    public String douyinSecret(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getDouyinSecretEnc());
    }

    public String douyinSalt(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getDouyinSaltEnc());
    }

    public String douyinToken(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getDouyinTokenEnc());
    }

    public String xhsSecret(PayConfig cfg) {
        return codec.decryptOrNull(cfg.getXhsSecretEnc());
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
        cfg.setWxPayMode("miniprogram");
        cfg.setWxAppid("");
        cfg.setWxMchId("");
        cfg.setWxApiV3KeyEnc("");
        cfg.setWxCertSerialNo("");
        cfg.setWxPrivateKeyPath("");
        cfg.setWxNotifyUrl("");
        cfg.setWxVirtualAppkeyEnc("");
        cfg.setWxVirtualOfferId("");
        cfg.setWxVirtualEnv(0);
        cfg.setWxVirtualProductOne("");
        cfg.setWxVirtualProductAll("");
        cfg.setDouyinEnabled(0);
        cfg.setDouyinAppId("");
        cfg.setDouyinSecretEnc("");
        cfg.setDouyinSaltEnc("");
        cfg.setDouyinTokenEnc("");
        cfg.setDouyinNotifyUrl("");
        cfg.setDouyinSandbox(0);
        cfg.setXhsEnabled(0);
        cfg.setXhsAppId("");
        cfg.setXhsSecretEnc("");
        cfg.setXhsMerchantId("");
        cfg.setXhsNotifyUrl("");
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
