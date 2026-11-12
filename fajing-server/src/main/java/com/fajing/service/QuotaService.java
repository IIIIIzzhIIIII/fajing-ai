package com.fajing.service;

import com.fajing.common.BizException;
import com.fajing.entity.QuotaRefund;
import com.fajing.entity.UserQuota;
import com.fajing.mapper.QuotaRefundMapper;
import com.fajing.mapper.UserQuotaMapper;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
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
            quotaMapper.insert(quota);
            return quota;
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

    public UserQuota consume(String type) {
        UserQuota quota = get();
        switch (type == null ? "" : type) {
            case "free" -> {
                if (quota.getFreeLeft() == null || quota.getFreeLeft() <= 0) {
                    throw new BizException("今日免费次数已用完");
                }
                quota.setFreeLeft(quota.getFreeLeft() - 1);
            }
            case "share" -> {
                if (Boolean.TRUE.equals(quota.getShareClaimed())) {
                    throw new BizException("分享奖励已领取（每账号限 1 次）");
                }
                quota.setShareClaimed(true);
                quota.setFreeLeft((quota.getFreeLeft() == null ? 0 : quota.getFreeLeft()) + 1);
            }
            case "ad" -> {
                quota.setAdUsed(true);
                quota.setFreeLeft((quota.getFreeLeft() == null ? 0 : quota.getFreeLeft()) + 1);
            }
            default -> throw new BizException("未知的额度类型");
        }
        quota.setUpdatedAt(LocalDateTime.now());
        quotaMapper.updateById(quota);
        return quota;
    }

    /** 整批生成全部失败时自动退还 1 次额度，并写 quota_refund 流水 */
    public void refund(Long userId, Long taskId, String reason) {
        if (userId == null) {
            return;
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
            }
            quota.setFreeLeft((quota.getFreeLeft() == null ? 0 : quota.getFreeLeft()) + 1);
            quota.setUpdatedAt(LocalDateTime.now());
            quotaMapper.updateById(quota);
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
