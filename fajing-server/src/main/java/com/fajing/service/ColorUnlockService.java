package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.UserColorUnlock;
import com.fajing.entity.UserQuota;
import com.fajing.mapper.UserColorUnlockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发色解锁：按「用户 + 发色」永久保留。
 *
 * 规则：
 * 1. 已永久解锁的发色不再扣费
 * 2. 仍有可用免费生成次数时，排名前 3 的基础发色免费
 * 3. 其余情况由用户选择：消耗 1 次免费解锁次数（广告获得），或消耗 10 点魅力值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColorUnlockService {

    private final UserColorUnlockMapper colorUnlockMapper;
    private final PointsService pointsService;

    /** 当前用户已永久解锁的发色 ID 集合 */
    public Set<String> unlockedColors(Long userId) {
        List<UserColorUnlock> list = colorUnlockMapper.selectList(new QueryWrapper<UserColorUnlock>()
                .eq("user_id", userId));
        if (list == null) {
            return new LinkedHashSet<>();
        }
        return list.stream()
                .map(UserColorUnlock::getColorId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isUnlocked(Long userId, String colorId) {
        if (userId == null || colorId == null || colorId.isBlank()) {
            return false;
        }
        Long count = colorUnlockMapper.selectCount(new QueryWrapper<UserColorUnlock>()
                .eq("user_id", userId)
                .eq("color_id", colorId));
        return count != null && count > 0;
    }

    /** 写入永久解锁记录；并发/重复调用视为已解锁 */
    public void unlock(Long userId, String colorId) {
        if (userId == null || colorId == null || colorId.isBlank() || isUnlocked(userId, colorId)) {
            return;
        }
        UserColorUnlock row = new UserColorUnlock();
        row.setUserId(userId);
        row.setColorId(colorId);
        row.setCreatedAt(LocalDateTime.now());
        try {
            colorUnlockMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("发色已解锁 user={} color={}", userId, colorId);
        }
    }

    /**
     * 解锁一种发色。
     *
     * @param rank      发色在本次推荐中的排名（前 3 名在有免费次数时免收）
     * @param payMethod 付费方式：credit=免费解锁次数；points=魅力值（默认）
     * @return 解锁结果 + 最新额度快照
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unlockColor(Long userId, String colorId, int rank, String payMethod) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("colorId", colorId);

        if (isUnlocked(userId, colorId)) {
            data.put("already", true);
            data.put("charged", "none");
            data.put("free", false);
        } else {
            UserQuota quota = pointsService.get(userId);
            boolean unlimited = pointsService.unlimited(quota);
            int freeBatches = quota.getFreeBatches() == null ? 0 : quota.getFreeBatches();
            int credits = quota.getAdCredits() == null ? 0 : quota.getAdCredits();
            boolean free = rank <= RecommendService.FREE_TOP && freeBatches > 0;

            String charged;
            if (free) {
                charged = "free";
            } else if (unlimited) {
                charged = "unlimited";
            } else if ("credit".equalsIgnoreCase(payMethod) && credits > 0
                    && pointsService.tryConsumeUnlockCredit(userId)) {
                charged = "credit";
            } else {
                // 余额不足会抛业务异常，由全局异常处理转成「魅力值不足，请充值后继续解锁」
                pointsService.spend(userId, PointsService.UNLOCK_COST);
                charged = "points";
            }

            // 免费试色不落永久记录：免费次数用完后仍需解锁；付费 / 免费次数 / 会员解锁永久保留
            if (!free) {
                unlock(userId, colorId);
            }
            data.put("already", false);
            data.put("charged", charged);
            data.put("free", free);
        }

        data.putAll(pointsService.toMap(pointsService.get(userId)));
        return data;
    }
}
