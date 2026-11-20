package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 门店：租户下可有多家门店，第一版至少一家 */
@Data
@TableName("store")
public class Store {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String brandName;
    private String address;
    private String phone;
    private String businessHours;
    private Integer dailyCapacity;
    /** 平台佣金比例，例如 10.00 表示 10% */
    private BigDecimal commissionRate;
    /** pending / approved / rejected */
    private String kycStatus;
    private Integer status;
    private LocalDateTime createdAt;
}
