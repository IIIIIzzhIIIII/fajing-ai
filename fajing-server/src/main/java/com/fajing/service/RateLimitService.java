package com.fajing.service;

import com.fajing.common.BizException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地限流：同一用户每秒只允许发起 1 次生成，避免误触与刷接口。
 * 单机内存实现，多实例部署时改用 Redis 计数器。
 */
@Service
public class RateLimitService {

    private static final long INTERVAL_MS = 1000L;

    private final Map<Long, Long> lastCallAt = new ConcurrentHashMap<>();

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
}
