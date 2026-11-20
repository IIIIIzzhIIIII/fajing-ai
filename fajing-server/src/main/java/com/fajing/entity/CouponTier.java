package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 团购券档位，如 50 元抵 69 元 */
@Data
@TableName("coupon_tier")
public class CouponTier {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 0 = 平台默认档位；>0 = 门店专属档位 */
    private Long storeId;
    private String title;
    private BigDecimal payAmount;
    private BigDecimal valueAmount;
    private String description;
    private Integer status;
    private Integer sort;
}
