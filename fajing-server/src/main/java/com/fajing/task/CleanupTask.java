package com.fajing.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.GenerationTask;
import com.fajing.mapper.GenerationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每天凌晨 3 点清理过期原图（24h 自动删除）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupTask {

    private final GenerationTaskMapper taskMapper;

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredPhotos() {
        List<GenerationTask> tasks = taskMapper.selectList(new QueryWrapper<GenerationTask>()
                .lt("expire_at", LocalDateTime.now())
                .isNotNull("photo_url")
                .ne("photo_url", ""));
        int cleaned = 0;
        for (GenerationTask task : tasks) {
            deleteLocalFile(task.getPhotoUrl());
            task.setPhotoUrl("");
            taskMapper.updateById(task);
            cleaned++;
        }
        log.info("过期原图清理完成，共 {} 条", cleaned);
    }

    private void deleteLocalFile(String url) {
        if (url == null || !url.contains("/uploads/")) {
            return;
        }
        String name = url.substring(url.lastIndexOf("/uploads/") + "/uploads/".length());
        if (name.isBlank() || name.contains("..")) {
            return;
        }
        File file = new File(uploadDir, name);
        if (file.exists() && !file.delete()) {
            log.warn("删除原图失败：{}", file.getAbsolutePath());
        }
    }
}
