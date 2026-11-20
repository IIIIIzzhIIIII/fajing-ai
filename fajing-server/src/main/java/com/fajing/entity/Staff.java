package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 门店员工：店长创建后，员工用邀请码 + 手机号绑定账号 */
@Data
@TableName("staff")
public class Staff {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long userId;
    private String name;
    private String phone;
    private String role;
    /** 员工绑定用的邀请码 */
    private String inviteCode;
    /** 顾客扫码归因用的员工码 */
    private String staffCode;
    private Integer dailyLimit;
    private Integer status;
    private LocalDateTime createdAt;
}
