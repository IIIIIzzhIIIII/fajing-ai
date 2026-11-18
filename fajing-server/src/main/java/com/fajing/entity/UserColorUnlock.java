package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发色解锁记录：按「用户 + 发色」永久保留，解锁后无需重复扣费
 */
@Data
@TableName("user_color_unlock")
public class UserColorUnlock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String colorId;
    private LocalDateTime createdAt;
}
