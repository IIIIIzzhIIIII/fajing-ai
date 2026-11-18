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
    private String wxVirtualOfferId;
    private Integer wxVirtualEnv;
    /** 单张解锁的虚拟道具 ID（价格 6 元） */
    private String wxVirtualProductOne;
    /** 全部解锁的虚拟道具 ID（单价 3 元，按数量购买） */
    private String wxVirtualProductAll;
    /** 充值套餐虚拟道具 ID：10 / 20 / 29 / 99 元 */
    private String wxVirtualProductP10;
    private String wxVirtualProductP20;
    private String wxVirtualProductP29;
    private String wxVirtualProductP99;

    private Integer douyinEnabled;
    private String douyinAppId;
    private String douyinSecretEnc;
    /** 担保支付 SALT（请求签名用） */
    private String douyinSaltEnc;
    /** 支付设置中的 Token（回调签名用） */
    private String douyinTokenEnc;
    private String douyinNotifyUrl;
    private Integer douyinSandbox;

    private Integer xhsEnabled;
    private String xhsAppId;
    private String xhsSecretEnc;
    private String xhsMerchantId;
    private String xhsNotifyUrl;

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
