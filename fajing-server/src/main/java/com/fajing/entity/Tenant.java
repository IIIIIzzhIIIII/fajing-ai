package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商户/租户：一个店长购买系统后对应一个租户 */
@Data
@TableName("tenant")
public class Tenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private LocalDateTime createdAt;
}
