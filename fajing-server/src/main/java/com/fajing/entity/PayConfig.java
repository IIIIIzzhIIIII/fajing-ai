package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付配置（单行，id 固定为 1）
 * 敏感字段以 AES-256-GCM 密文存储，字段名统一以 Enc 结尾。
 */
@Data
@TableName("pay_config")
public class PayConfig {
    @TableId
    private Long id;

    private Integer wxEnabled;
    /** normal=普通 JSAPI，virtual=小程序虚拟支付 */
    private String wxPayMode;
    private String wxAppid;
    private String wxMchId;
    private String wxApiV3KeyEnc;
    private String wxCertSerialNo;
    private String wxPrivateKeyPath;
    private String wxNotifyUrl;
    private String wxVirtualAppkeyEnc;

    private Integer alipayEnabled;
    private String alipayAppId;
    private String alipayPrivateKeyEnc;
    private String alipayPublicKeyEnc;
    private String alipayGateway;
    private String alipayNotifyUrl;
    private String alipayReturnUrl;

    private LocalDateTime updatedAt;
    private Long updatedBy;
}
