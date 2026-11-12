package com.fajing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.UserRecord;
import com.fajing.entity.UserUnlock;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.UserRecordMapper;
import com.fajing.mapper.UserUnlockMapper;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final GenerationTaskMapper taskMapper;
    private final HairstyleMapper hairstyleMapper;
    private final UserRecordMapper recordMapper;
    private final UserUnlockMapper unlockMapper;
    private final ObjectMapper objectMapper;

    public GenerationTask create(String photoUrl, String gender, String mode, List<String> styleIds, String colorId) {
        if (styleIds == null || styleIds.isEmpty()) {
            throw new BizException("请先选择发型");
        }
        if (styleIds.size() > 4) {
            throw new BizException("最多同时生成 4 款");
        }
        GenerationTask task = new GenerationTask();
        task.setUserId(UserContext.require());
        task.setPhotoUrl(photoUrl == null ? "" : photoUrl);
        task.setGender("male".equals(gender) ? "male" : "female");
        task.setMode(colorId == null || colorId.isEmpty() ? "hair" : mode);
        task.setStyleIds(String.join(",", styleIds));
        task.setColorId(colorId == null ? "" : colorId);
        task.setStatus("pending");
        task.setProgress(0);
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    public void updateProgress(Long taskId, int progress) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setProgress(progress);
        task.setStatus(progress >= 100 ? "success" : "pending");
        taskMapper.updateById(task);
    }

    /** 生成完成：写入 result_json、历史记录，并默认解锁第 1 款 */
    public void finish(Long taskId, Map<String, String> images) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        try {
            List<Hairstyle> styles = resolveStyles(task);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("before", task.getPhotoUrl());
            result.put("faceShape", task.getGender().equals("male") ? "方脸" : "鹅蛋脸");
            result.put("score", ThreadLocalRandom.current().nextInt(88, 98));
            List<Map<String, Object>> items = new ArrayList<>();
            int index = 0;
            for (Hairstyle s : styles) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("styleId", s.getId());
                item.put("name", s.getName());
                item.put("len", s.getLen());
                item.put("face", s.getFace());
                item.put("cat", s.getCat());
                String aiUrl = images == null ? null : images.get(s.getId());
                item.put("img", aiUrl != null && !aiUrl.isBlank() ? aiUrl : placeholder(s.getId(), taskId));
                // 第 1 款免费解锁，其余以服务端 user_unlock 为准
                item.put("unlocked", index == 0 || isUnlocked(task.getUserId(), taskId, s.getId()));
                items.add(item);
                index++;
            }
            result.put("styles", items);

            task.setResultJson(objectMapper.writeValueAsString(result));
            task.setProgress(100);
            task.setStatus("success");
            taskMapper.updateById(task);

            if (!styles.isEmpty()) {
                UserRecord record = new UserRecord();
                record.setUserId(task.getUserId());
                record.setTaskId(taskId);
                record.setTitle(styles.size() > 1 ? styles.get(0).getName() + " 等 " + styles.size() + " 款" : styles.get(0).getName());
                record.setBeforeUrl(task.getPhotoUrl());
                String cover = images == null ? null : images.get(styles.get(0).getId());
                record.setCoverUrl(cover != null && !cover.isBlank() ? cover : placeholder(styles.get(0).getId(), taskId));
                record.setStyleIds(task.getStyleIds());
                record.setCreatedAt(LocalDateTime.now());
                recordMapper.insert(record);

                // 第 1 款默认免费解锁（按任务维度）
                unlock(task.getUserId(), styles.get(0).getId(), taskId);
            }
        } catch (Exception e) {
            log.error("生成结果写入失败 taskId={}", taskId, e);
            task.setStatus("failed");
            taskMapper.updateById(task);
        }
    }

    public GenerationTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    /** 解锁判定唯一入口：按「用户 + 任务 + 款式」查 user_unlock */
    public boolean isUnlocked(Long userId, Long taskId, String styleId) {
        Long count = unlockMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserUnlock>()
                        .eq("user_id", userId)
                        .eq("task_id", taskId == null ? 0L : taskId)
                        .eq("style_id", styleId));
        return count != null && count > 0;
    }

    public void unlock(Long userId, String styleId, Long taskId) {
        Long exists = unlockMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserUnlock>()
                        .eq("user_id", userId)
                        .eq("task_id", taskId == null ? 0L : taskId)
                        .eq("style_id", styleId));
        if (exists != null && exists > 0) {
            return;
        }
        UserUnlock unlock = new UserUnlock();
        unlock.setUserId(userId);
        unlock.setTaskId(taskId == null ? 0L : taskId);
        unlock.setStyleId(styleId);
        unlock.setCreatedAt(LocalDateTime.now());
        unlockMapper.insert(unlock);
    }

    public Map<String, Object> toMap(GenerationTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", task.getId());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("result", parseResult(task.getResultJson()));
        return map;
    }

    private Object parseResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Hairstyle> resolveStyles(GenerationTask task) {
        List<String> ids = new ArrayList<>(Arrays.asList(task.getStyleIds().split(",")));
        ids.removeIf(String::isBlank);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 严格按用户选择的款式出图：选 2 款就只出 2 张，不做自动补齐
        Map<String, Hairstyle> found = hairstyleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Hairstyle::getId, s -> s, (a, b) -> a));
        List<Hairstyle> list = new ArrayList<>();
        for (String id : ids) {
            Hairstyle s = found.get(id);
            if (s != null) {
                list.add(s);
            }
        }
        return list.size() > 4 ? new ArrayList<>(list.subList(0, 4)) : list;
    }

    /** AI 效果占位图（阶段后期替换为真实 AI 出图） */
    private String placeholder(String styleId, Long taskId) {
        return "https://picsum.photos/seed/fajing_" + styleId + "_" + taskId + "/600/800";
    }
}
