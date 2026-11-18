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
    /** 魅力值余额 */
    private Integer points;
    /** 剩余免费批次：前 3 批内每批前 3 款免费 */
    private Integer freeBatches;
    /** VIP 到期时间（29 元包月为 30 天） */
    private LocalDateTime vipExpireAt;
    /** VVIP 永久不限量 */
    private Boolean vvip;
    /** 激励广告奖励日期与当日次数（每日上限） */
    private LocalDate adDate;
    private Integer adCount;
    /** 广告获得的免费解锁次数：解锁时优先消耗 */
    private Integer adCredits;
    private LocalDateTime updatedAt;
}
