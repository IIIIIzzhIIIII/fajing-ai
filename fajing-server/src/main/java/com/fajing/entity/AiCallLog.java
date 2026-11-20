package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long userId;
    private Long tenantId;
    private Long storeId;
    private Long staffId;
    private String provider;
    private String model;
    private Integer costCents;
    private Boolean ok;
    private LocalDateTime createdAt;
}
