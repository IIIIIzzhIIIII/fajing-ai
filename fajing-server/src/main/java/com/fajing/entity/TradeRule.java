package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_rule")
public class TradeRule {
    @TableId
    private Long id;
    /** 支付完成后多少天内可退款，0 表示不限制 */
    private Integer refundDays;
    private String afterSaleText;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
