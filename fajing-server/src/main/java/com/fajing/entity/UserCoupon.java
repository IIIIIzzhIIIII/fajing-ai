package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户券包中的团购券，凭 code / 二维码到店核销 */
@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tierId;
    private Long orderId;
    private String title;
    private BigDecimal payAmount;
    private BigDecimal valueAmount;
    private String code;
    private String qrUrl;
    /** unused / used / expired */
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
