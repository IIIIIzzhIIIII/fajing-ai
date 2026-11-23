package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.UserQuota;
import com.fajing.mapper.UserQuotaMapper;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 魅力值体系：
 * 1. 新用户注册赠 50 点
 * 2. 前 3 个免费批次内，每批前 3 款发型/发色免费
 * 3. 免费批次用尽或第 4 款起，每款解锁消耗 10 点
 * 4. 29 元 VIP 30 天内、99 元 VVIP 永久不限量
 * 5. 激励广告每完整观看 1 次 +10 点（每日上限 5 次）
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    /** 单款解锁成本（魅力值） */
    public static final int UNLOCK_COST = 10;
    /** 注册赠送 */
    public static final int REGISTER_BONUS = 50;
    /** 免费批次数 */
    public static final int FREE_BATCH_TOTAL = 3;
    /** 每批免费款数 */
    public static final int FREE_PER_BATCH = 3;
    /** 激励广告每日上限 */
    public static final int AD_DAILY_MAX = 5;
    /** 看一次广告兑换的解锁额度（等值 1 款） */
    public static final int AD_REWARD_POINTS = 10;

    private final UserQuotaMapper quotaMapper;

    /** 读取（不存在则初始化注册赠送），保证 points/freeBatches 有值 */
    public UserQuota get() {
        return get(UserContext.require());
    }

    public UserQuota get(Long userId) {
        UserQuota quota = quotaMapper.selectById(userId);
        if (quota != null) {
            return fill(quota);
        }
        quota = new UserQuota();
        quota.setUserId(userId);
        quota.setFreeLeft(0);
        quota.setQuotaDate(LocalDate.now());
        quota.setShareClaimed(false);
        quota.setAdUsed(false);
        quota.setPoints(REGISTER_BONUS);
        quota.setFreeBatches(FREE_BATCH_TOTAL);
        quota.setVvip(false);
        quota.setAdCount(0);
        quota.setAdCredits(0);
        quota.setUpdatedAt(LocalDateTime.now());
        try {
            quotaMapper.insert(quota);
            return quota;
        } catch (DuplicateKeyException e) {
            UserQuota existed = quotaMapper.selectById(userId);
            if (existed == null) {
                throw e;
            }
            return fill(existed);
        }
    }

    /** 老数据兼容：points/freeBatches 为空时按默认值处理 */
    private UserQuota fill(UserQuota quota) {
        if (quota.getPoints() == null) {
            quota.setPoints(0);
        }
        if (quota.getFreeBatches() == null) {
            quota.setFreeBatches(0);
        }
        if (quota.getVvip() == null) {
            quota.setVvip(false);
        }
        if (quota.getAdCount() == null) {
            quota.setAdCount(0);
        }
        if (quota.getAdCredits() == null) {
            quota.setAdCredits(0);
        }
        return quota;
    }

    /** VIP（未过期）或 VVIP 表示解锁不限量 */
    public boolean unlimited(UserQuota quota) {
        if (Boolean.TRUE.equals(quota.getVvip())) {
            return true;
        }
        return quota.getVipExpireAt() != null && quota.getVipExpireAt().isAfter(LocalDateTime.now());
    }

    public void addPoints(Long userId, int points) {
        if (points <= 0) {
            return;
        }
        get(userId);
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .setSql("points = IFNULL(points, 0) + " + points)
                .set("updated_at", LocalDateTime.now()));
    }

    /**
     * 扣减魅力值：不限量用户直接通过且不扣点。
     * 余额不足抛业务异常，由前端引导充值。
     */
    public void spend(Long userId, int points) {
        UserQuota quota = get(userId);
        if (unlimited(quota) || points <= 0) {
            return;
        }
        int rows = quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .ge("IFNULL(points, 0)", points)
                .setSql("points = points - " + points)
                .set("updated_at", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("魅力值不足，请充值后继续解锁");
        }
    }

    /** 原子占用 1 个免费批次；返回 true 表示本批前 3 款免费 */
    public boolean tryConsumeFreeBatch(Long userId) {
        UserQuota quota = get(userId);
        if (quota.getFreeBatches() == null || quota.getFreeBatches() <= 0) {
            return false;
        }
        int rows = quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .gt("free_batches", 0)
                .setSql("free_batches = free_batches - 1")
                .set("updated_at", LocalDateTime.now()));
        return rows > 0;
    }

    /** 整批生成失败退回免费批次 */
    public void refundFreeBatch(Long userId) {
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .lt("free_batches", FREE_BATCH_TOTAL)
                .setSql("free_batches = free_batches + 1")
                .set("updated_at", LocalDateTime.now()));
    }

    /**
     * 单次解锁：优先消耗广告获得的免费解锁次数，否则扣 10 点魅力值。
     * VIP / VVIP 不限量直接通过。
     */
    public void consumeUnlock(Long userId) {
        UserQuota quota = get(userId);
        if (unlimited(quota)) {
            return;
        }
        int credits = quota.getAdCredits() == null ? 0 : quota.getAdCredits();
        if (credits > 0) {
            int rows = quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                    .eq("user_id", userId)
                    .gt("ad_credits", 0)
                    .setSql("ad_credits = ad_credits - 1")
                    .set("updated_at", LocalDateTime.now()));
            if (rows > 0) {
                return;
            }
        }
        spend(userId, UNLOCK_COST);
    }

    /**
     * 尝试消耗 1 次广告获得的免费解锁次数（原子扣减，避免并发超扣）。
     * 成功返回 true；没有可用次数返回 false。
     */
    public boolean tryConsumeUnlockCredit(Long userId) {
        int rows = quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .gt("ad_credits", 0)
                .setSql("ad_credits = ad_credits - 1")
                .set("updated_at", LocalDateTime.now()));
        return rows > 0;
    }

    /** 激励广告奖励：每日最多 5 次，每次获得 1 次免费解锁次数 */
    public Map<String, Object> rewardAd() {
        return rewardAd(UserContext.require());
    }

    /** 分享奖励：每账号限 1 次，奖励 10 点魅力值 */
    public Map<String, Object> claimShareReward() {
        Long userId = UserContext.require();
        UserQuota quota = get(userId);
        if (Boolean.TRUE.equals(quota.getShareClaimed())) {
            throw new BizException("分享奖励已领取（每账号限 1 次）");
        }
        int rows = quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .and(w -> w.eq("share_claimed", false).or().isNull("share_claimed"))
                .set("share_claimed", true)
                .setSql("points = IFNULL(points, 0) + 10")
                .set("updated_at", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("分享奖励已领取（每账号限 1 次）");
        }
        Map<String, Object> data = toMap(get(userId));
        data.put("reward", 10);
        return data;
    }

    /** 激励广告奖励：每日最多 5 次，每次 +1 次免费解锁次数 */
    public Map<String, Object> rewardAd(Long userId) {
        UserQuota quota = get(userId);
        LocalDate today = LocalDate.now();
        boolean sameDay = today.equals(quota.getAdDate());
        int used = sameDay ? (quota.getAdCount() == null ? 0 : quota.getAdCount()) : 0;
        if (used >= AD_DAILY_MAX) {
            throw new BizException("今日广告奖励次数已用完，明天再来");
        }
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("ad_date", today)
                .set("ad_count", used + 1)
                .setSql("ad_credits = IFNULL(ad_credits, 0) + 1")
                .set("updated_at", LocalDateTime.now()));
        Map<String, Object> data = toMap(get(userId));
        data.put("reward", 1);
        data.put("rewardType", "unlock");
        data.put("adLeft", Math.max(0, AD_DAILY_MAX - used - 1));
        return data;
    }

    /** 充值到账 / VIP 开通 */
    public void grantPoints(Long userId, int points) {
        addPoints(userId, points);
    }

    public void grantVip(Long userId, int days) {
        UserQuota quota = get(userId);
        LocalDateTime base = quota.getVipExpireAt() != null && quota.getVipExpireAt().isAfter(LocalDateTime.now())
                ? quota.getVipExpireAt() : LocalDateTime.now();
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("vip_expire_at", base.plusDays(days))
                .set("updated_at", LocalDateTime.now()));
    }

    public void grantVvip(Long userId) {
        get(userId);
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("vvip", true)
                .set("updated_at", LocalDateTime.now()));
    }

    /** 退款回收 VIP：从到期时间倒扣购买天数，扣成过去时间即视为失效 */
    public void revokeVip(Long userId, int days) {
        UserQuota quota = get(userId);
        if (quota.getVipExpireAt() == null || days <= 0) {
            return;
        }
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("vip_expire_at", quota.getVipExpireAt().minusDays(days))
                .set("updated_at", LocalDateTime.now()));
    }

    /** 退款回收 VVIP */
    public void revokeVvip(Long userId) {
        get(userId);
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("vvip", false)
                .set("updated_at", LocalDateTime.now()));
    }

    public Map<String, Object> toMap(UserQuota quota) {
        Map<String, Object> map = new LinkedHashMap<>();
        int freeBatches = quota.getFreeBatches() == null ? 0 : quota.getFreeBatches();
        map.put("points", quota.getPoints() == null ? 0 : quota.getPoints());
        map.put("freeBatches", freeBatches);
        // 兼容旧字段：免费次数即剩余免费批次
        map.put("freeLeft", freeBatches);
        map.put("shareClaimed", Boolean.TRUE.equals(quota.getShareClaimed()));
        map.put("unlimited", unlimited(quota));
        map.put("vvip", Boolean.TRUE.equals(quota.getVvip()));
        map.put("adCredits", quota.getAdCredits() == null ? 0 : quota.getAdCredits());
        map.put("vipExpireAt", quota.getVipExpireAt());
        LocalDate today = LocalDate.now();
        boolean sameDay = today.equals(quota.getAdDate());
        int used = sameDay && quota.getAdCount() != null ? quota.getAdCount() : 0;
        map.put("adLeft", Math.max(0, AD_DAILY_MAX - used));
        return map;
    }
}
