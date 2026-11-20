package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 顾客扫员工码后的归属关系，默认 30 天有效 */
@Data
@TableName("staff_customer_binding")
public class StaffCustomerBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long staffId;
    private Long customerId;
    private LocalDateTime boundAt;
    private LocalDateTime expireAt;
}
