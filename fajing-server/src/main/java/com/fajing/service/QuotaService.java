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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuotaService {

    /** 关闭额度限制时展示用的剩余次数，避免前端进入额度弹窗 */
    private static final int TEST_FREE_LEFT = 999;

    private final UserQuotaMapper quotaMapper;
    private final QuotaRefundMapper refundMapper;

    /** 本地联调用：false 时不扣减、不拦截；生产环境保持默认 true */
    @Value("${quota.enforce:true}")
    private boolean enforce;

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
        // 免费次数按账号终身计算：首次 1 次 + 分享奖励 1 次，不再每日重置
        return quota;
    }

    /**
     * 额度变更统一走条件更新，避免并发下先读后写导致的重复扣减/重复领取。
     * free：扣减 1 次（终身）；share：终身 +1。
     */
    public UserQuota consume(String type) {
        Long userId = UserContext.require();
        // 测试期关闭额度限制：不扣减、不抛「次数已用完」
        if (!enforce) {
            return get();
        }
        get(); // 确保额度行存在
        UpdateWrapper<UserQuota> update = new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .set("updated_at", LocalDateTime.now());
        int rows;
        switch (type == null ? "" : type) {
            case "free" -> {
                update.gt("free_left", 0).setSql("free_left = free_left - 1");
                rows = quotaMapper.update(null, update);
                if (rows == 0) {
                    throw new BizException("免费次数已用完");
                }
            }
            case "share" -> {
                // 分享奖励终身 1 次，与日期无关
                update.and(w -> w.eq("share_claimed", false).or().isNull("share_claimed"))
                        .setSql("free_left = IFNULL(free_left, 0) + 1")
                        .set("share_claimed", true);
                rows = quotaMapper.update(null, update);
                if (rows == 0) {
                    throw new BizException("分享奖励已领取（每账号限 1 次）");
                }
            }
            default -> throw new BizException("未知的额度类型");
        }
        return get();
    }

    /** 生成前尝试占用 1 次免费额度：成功返回 true；已用完返回 false，由调用方转为付费批次 */
    public boolean tryConsumeFree() {
        Long userId = UserContext.require();
        if (!enforce) {
            get();
            return true;
        }
        get();
        int rows = quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("user_id", userId)
                .gt("free_left", 0)
                .setSql("free_left = free_left - 1")
                .set("updated_at", LocalDateTime.now()));
        return rows > 0;
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
        int freeLeft = quota.getFreeLeft() == null ? 0 : quota.getFreeLeft();
        // 测试期回填一个足够大的剩余次数，前端不会弹「今天的免费次数用完了」
        map.put("freeLeft", enforce ? freeLeft : TEST_FREE_LEFT);
        map.put("shareClaimed", Boolean.TRUE.equals(quota.getShareClaimed()));
        return map;
    }
}
