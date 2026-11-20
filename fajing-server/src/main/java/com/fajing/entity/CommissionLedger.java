package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 平台佣金台账：支付成功 available，核销 settled，退款 reversed */
@Data
@TableName("commission_ledger")
public class CommissionLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long staffId;
    private Long orderId;
    private BigDecimal orderAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    /** available / settled / reversed */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime settledAt;
    private LocalDateTime reversedAt;
}
