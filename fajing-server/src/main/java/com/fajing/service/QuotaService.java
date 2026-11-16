package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.QuotaRefund;
import com.fajing.entity.UserQuota;
import com.fajing.mapper.QuotaRefundMapper;
import com.fajing.mapper.UserQuotaMapper;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserQuotaMapper quotaMapper;
    private final QuotaRefundMapper refundMapper;

    public UserQuota get() {
        Long userId = UserContext.require();
        UserQuota quota = quotaMapper.selectById(userId);
        LocalDate today = LocalDate.now();
        if (quota == null) {
            quota = new UserQuota();
            quota.setUserId(userId);
            quota.setFreeLeft(1);
            quota.setQuotaDate(today);
            quota.setShareClaimed(false);
            quota.setAdUsed(false);
            quota.setUpdatedAt(LocalDateTime.now());
            try {
                quotaMapper.insert(quota);
                return quota;
            } catch (DuplicateKeyException e) {
                // 并发首次请求可能同时插入，重新读取已存在的记录
                UserQuota existed = quotaMapper.selectById(userId);
                if (existed == null) {
                    throw e;
                }
                quota = existed;
            }
        }
        // 每日重置免费次数；分享奖励标记终身只领一次，不随日期重置
        if (!today.equals(quota.getQuotaDate())) {
            quota.setFreeLeft(1);
            quota.setQuotaDate(today);
            quota.setAdUsed(false);
            quota.setUpdatedAt(LocalDateTime.now());
            quotaMapper.updateById(quota);
        }
        return quota;
    }

    /**
     * 额度变更统一走条件更新，避免并发下先读后写导致的重复扣减/重复领取。
     * free：扣减 1 次；share：终身 +1；ad：每日 +1。
     */
    public UserQuota consume(String type) {
        Long userId = UserContext.require();
        get(); // 确保额度行存在，并完成跨天重置
        LocalDate today = LocalDate.now();
        UpdateWrapper<UserQuota> update = new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("updated_at", LocalDateTime.now());
        int rows;
        switch (type == null ? "" : type) {
            case "free" -> {
                update.gt("free_left", 0).setSql("free_left = free_left - 1");
                rows = quotaMapper.update(null, update);
                if (rows == 0) {
                    throw new BizException("今日免费次数已用完");
                }
            }
            case "share" -> {
                update.eq("quota_date", today)
                        .and(w -> w.eq("share_claimed", false).or().isNull("share_claimed"))
                        .setSql("free_left = IFNULL(free_left, 0) + 1")
                        .set("share_claimed", true);
                rows = quotaMapper.update(null, update);
                if (rows == 0) {
                    throw new BizException("分享奖励已领取（每账号限 1 次）");
                }
            }
            case "ad" -> {
                update.eq("quota_date", today)
                        .and(w -> w.eq("ad_used", false).or().isNull("ad_used"))
                        .setSql("free_left = IFNULL(free_left, 0) + 1")
                        .set("ad_used", true);
                rows = quotaMapper.update(null, update);
                if (rows == 0) {
                    throw new BizException("今日广告奖励已领取，明天再来");
                }
            }
            default -> throw new BizException("未知的额度类型");
        }
        return get();
    }

    /** 整批生成全部失败时自动退还 1 次额度，并写 quota_refund 流水（按 taskId 幂等） */
    public void refund(Long userId, Long taskId, String reason) {
        if (userId == null) {
            return;
        }
        if (taskId != null && taskId > 0) {
            Long refunded = refundMapper.selectCount(new QueryWrapper<QuotaRefund>()
                    .eq("user_id", userId).eq("task_id", taskId));
            if (refunded != null && refunded > 0) {
                return;
            }
        }
        UserQuota quota = quotaMapper.selectById(userId);
        LocalDate today = LocalDate.now();
        if (quota == null) {
            quota = new UserQuota();
            quota.setUserId(userId);
            quota.setFreeLeft(2);
            quota.setQuotaDate(today);
            quota.setShareClaimed(false);
            quota.setAdUsed(false);
            quota.setUpdatedAt(LocalDateTime.now());
            quotaMapper.insert(quota);
        } else {
            if (!today.equals(quota.getQuotaDate())) {
                quota.setFreeLeft(1);
                quota.setQuotaDate(today);
                quota.setAdUsed(false);
                quota.setUpdatedAt(LocalDateTime.now());
                quotaMapper.updateById(quota);
            }
            // 原子自增，避免与并发的领取/扣减互相覆盖导致回退的额度丢失
            quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                    .eq("user_id", userId)
                    .setSql("free_left = IFNULL(free_left, 0) + 1")
                    .set("updated_at", LocalDateTime.now()));
        }
        QuotaRefund refund = new QuotaRefund();
        refund.setUserId(userId);
        refund.setTaskId(taskId == null ? 0L : taskId);
        refund.setReason(reason == null ? "" : reason);
        refund.setCreatedAt(LocalDateTime.now());
        refundMapper.insert(refund);
    }

    public Map<String, Object> toMap(UserQuota quota) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("freeLeft", quota.getFreeLeft() == null ? 0 : quota.getFreeLeft());
        map.put("shareClaimed", Boolean.TRUE.equals(quota.getShareClaimed()));
        map.put("adUsed", Boolean.TRUE.equals(quota.getAdUsed()));
        return map;
    }
}
