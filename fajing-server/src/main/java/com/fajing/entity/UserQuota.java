package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_quota")
public class UserQuota {
    @TableId
    private Long userId;
    private Integer freeLeft;
    private LocalDate quotaDate;
    private Boolean shareClaimed;
    private Boolean adUsed;
    private LocalDateTime updatedAt;
}
