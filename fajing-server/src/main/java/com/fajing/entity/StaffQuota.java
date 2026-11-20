package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 员工每日免费 AI 额度 */
@Data
@TableName("staff_quota")
public class StaffQuota {
    @TableId
    private Long staffId;
    private LocalDate quotaDate;
    private Integer used;
    private LocalDateTime updatedAt;
}
