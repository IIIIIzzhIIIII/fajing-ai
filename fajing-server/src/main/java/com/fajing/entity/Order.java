package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long taskId;
    private String plan;
    private BigDecimal amount;
    private String status;
    private String styleIds;
    private LocalDateTime createdAt;
    /** 商户订单号（唯一） */
    private String outTradeNo;
    /** wx / alipay / mock */
    private String payChannel;
    private String transactionId;
    private String prepayId;
    private LocalDateTime paidAt;
    /** none / refunding / refunded */
    private String refundStatus;
    private BigDecimal refundAmount;
}
