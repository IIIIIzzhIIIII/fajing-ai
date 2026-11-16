package com.fajing.service;

import com.fajing.common.BizException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地限流：同一用户每秒只允许发起 1 次生成，避免误触与刷接口。
 * 单机内存实现，多实例部署时改用 Redis 计数器。
 */
@Service
public class RateLimitService {

    private static final long INTERVAL_MS = 1000L;
    /** 同一 IP 24 小时内最多创建的游客账号数 */
    private static final int GUEST_CREATE_MAX = 20;
    private static final long GUEST_WINDOW_MS = 24 * 60 * 60 * 1000L;

    private final Map<Long, Long> lastCallAt = new ConcurrentHashMap<>();
    /** IP → 窗口内建档时间戳，防止 H5 游客登录被批量注册 */
    private final Map<String, List<Long>> guestCreates = new ConcurrentHashMap<>();

    public void checkGenerate(Long userId) {
        if (userId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastCallAt.put(userId, now);
        if (prev != null && now - prev < INTERVAL_MS) {
            throw new BizException("操作过于频繁，请稍后再试");
        }
        // 顺手清理 10 分钟前的记录，避免长期运行内存增长
        if (lastCallAt.size() > 1000) {
            long expireBefore = now - 10 * 60 * 1000L;
            lastCallAt.entrySet().removeIf(e -> e.getValue() < expireBefore);
        }
    }

    /** 同一 IP 24 小时内最多创建 20 个游客账号 */
    public void checkGuestCreate(String ip) {
        String key = (ip == null || ip.isBlank()) ? "unknown" : ip;
        long now = System.currentTimeMillis();
        List<Long> records = guestCreates.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (records) {
            records.removeIf(t -> now - t > GUEST_WINDOW_MS);
            if (records.size() >= GUEST_CREATE_MAX) {
                throw new BizException("今日游客账号创建过于频繁，请明天再试");
            }
            records.add(now);
        }
        if (guestCreates.size() > 5000) {
            guestCreates.entrySet().removeIf(e -> {
                List<Long> list = e.getValue();
                synchronized (list) {
                    list.removeIf(t -> now - t > GUEST_WINDOW_MS);
                    return list.isEmpty();
                }
            });
        }
    }
}
