package com.fajing.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.GenerationTask;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每天凌晨 3 点清理过期原图（24h 自动删除）
 * 走 StorageService，本地磁盘与对象存储都能删。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupTask {

    private final GenerationTaskMapper taskMapper;
    private final StorageService storageService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredPhotos() {
        List<GenerationTask> tasks = taskMapper.selectList(new QueryWrapper<GenerationTask>()
                .lt("expire_at", LocalDateTime.now())
                .isNotNull("photo_url")
                .ne("photo_url", ""));
        int cleaned = 0;
        for (GenerationTask task : tasks) {
            try {
                storageService.delete(task.getPhotoUrl());
            } catch (Exception e) {
                log.warn("删除过期原图失败 taskId={}: {}", task.getId(), e.getMessage());
            }
            task.setPhotoUrl("");
            taskMapper.updateById(task);
            cleaned++;
        }
        log.info("过期原图清理完成，共 {} 条", cleaned);
    }
}
