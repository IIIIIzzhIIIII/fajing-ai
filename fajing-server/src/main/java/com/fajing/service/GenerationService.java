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

    public GenerationTask create(String photoUrl, String gender, String mode, List<String> styleIds, String colorId,
                                 boolean freeQuota) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BizException("请先上传照片");
        }
        // 去重并保持用户选择顺序：重复/无效 ID 会导致出图数量与所选不一致
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        if (styleIds != null) {
            for (String id : styleIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        if (ids.isEmpty()) {
            throw new BizException("请先选择发型");
        }
        if (ids.size() > 4) {
            throw new BizException("最多同时生成 4 款");
        }
        long validCount = hairstyleMapper.selectBatchIds(ids).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .count();
        if (validCount != ids.size()) {
            throw new BizException("所选发型不存在或已下架，请刷新后重试");
        }
        GenerationTask task = new GenerationTask();
        task.setUserId(UserContext.require());
        task.setPhotoUrl(photoUrl == null ? "" : photoUrl);
        task.setGender("male".equals(gender) ? "male" : "female");
        // mode 枚举归一化：只接受 hair / hairColor，非法值回退 hair，避免后续流程解析失败
        String validMode;
        if (colorId != null && !colorId.isBlank()) {
            validMode = "hairColor".equals(mode) ? "hairColor" : "hair";
        } else {
            validMode = "hair";
        }
        task.setMode(validMode);
        task.setStyleIds(String.join(",", ids));
        task.setColorId(colorId == null ? "" : colorId);
        task.setStatus("pending");
        task.setProgress(0);
        task.setFreeQuota(freeQuota ? 1 : 0);
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    public void updateProgress(Long taskId, int progress) {
        // 原子更新且不覆盖终态：超时后仍在跑的 AI 线程不能把 success/failed 回退成 pending
        int bounded = Math.max(0, Math.min(99, progress));
        taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<GenerationTask>()
                .eq("id", taskId)
                .notIn("status", "success", "failed")
                .set("progress", bounded)
                .set("status", "pending"));
    }

    /** 生成完成：写入 result_json、历史记录，并默认解锁第 1 款；失败款不再回退假图 */
    public void finish(Long taskId, Map<String, String> images, java.util.Set<String> failed) {
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
            // 免费批次才把第 1 款作为免费款；付费批次所有款式都要支付后才能生成与解锁
            boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
            String freeStyleId = freeBatch && !styles.isEmpty() ? styles.get(0).getId() : null;
            boolean freeGenerated = false;
            for (Hairstyle s : styles) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("styleId", s.getId());
                item.put("name", s.getName());
                item.put("len", s.getLen());
                item.put("face", s.getFace());
                item.put("cat", s.getCat());
                String aiUrl = images == null ? null : images.get(s.getId());
                boolean hasImage = aiUrl != null && !aiUrl.isBlank()
                        && (failed == null || !failed.contains(s.getId()));
                boolean isFree = s.getId().equals(freeStyleId);
                boolean pending = !hasImage && !isFree;
                item.put("img", hasImage ? aiUrl : null);
                item.put("failed", !hasImage && isFree);
                item.put("pending", pending);
                if (!hasImage && isFree) {
                    item.put("failReason", "生成超时/服务异常");
                }
                if (hasImage && isFree) {
                    freeGenerated = true;
                }
                // 免费款生成成功才解锁；付费款以 user_unlock 为准，未生成前保持锁定
                item.put("unlocked", hasImage
                        && (isFree || isUnlocked(task.getUserId(), taskId, s.getId())));
                items.add(item);
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
                String cover = images == null ? "" : styles.stream()
                        .map(s -> images.get(s.getId()))
                        .filter(url -> url != null && !url.isBlank())
                        .findFirst().orElse("");
                record.setCoverUrl(cover);
                record.setStyleIds(task.getStyleIds());
                record.setCreatedAt(LocalDateTime.now());
                recordMapper.insert(record);

                // 免费款生成成功才解锁；失败时不再顺延
                if (freeGenerated && freeStyleId != null) {
                    unlock(task.getUserId(), freeStyleId, taskId);
                }
            }
        } catch (Exception e) {
            log.error("生成结果写入失败 taskId={}", taskId, e);
            task.setStatus("failed");
            taskMapper.updateById(task);
        }
    }

    /**
     * 单款重试后回写结果：img 为空表示这次仍失败
     */
    public void patchStyle(Long taskId, String styleId, String img) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || task.getResultJson() == null || task.getResultJson().isBlank()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(task.getResultJson(), Map.class);
            Object stylesObj = result.get("styles");
            if (!(stylesObj instanceof List<?> list)) {
                return;
            }
            boolean changed = false;
            for (Object o : list) {
                if (o instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) raw;
                    if (styleId.equals(String.valueOf(item.get("styleId")))) {
                        boolean ok = img != null && !img.isBlank();
                        item.put("img", ok ? img : null);
                        item.put("failed", !ok);
                        item.remove("pending");
                        if (ok) {
                            String[] ids = task.getStyleIds() == null ? new String[0] : task.getStyleIds().split(",");
                            boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
                            if (freeBatch && ids.length > 0 && styleId.equals(ids[0].trim())) {
                                // 免费款重试成功后补解锁，避免首款失败后免费资格丢失
                                unlock(task.getUserId(), styleId, taskId);
                            }
                        }
                        if (ok) {
                            item.remove("failReason");
                        } else {
                            item.put("failReason", "生成超时/服务异常");
                        }
                        item.put("unlocked", ok && isUnlocked(task.getUserId(), taskId, styleId));
                        changed = true;
                        break;
                    }
                }
            }
            if (!changed) {
                return;
            }
            task.setResultJson(objectMapper.writeValueAsString(result));
            task.setProgress(100);
            task.setStatus("success");
            taskMapper.updateById(task);

            // 记录封面为空时用这次成功的图补上
            if (img != null && !img.isBlank()) {
                UserRecord record = recordMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserRecord>()
                        .eq("task_id", taskId).last("limit 1"));
                if (record != null && (record.getCoverUrl() == null || record.getCoverUrl().isBlank())) {
                    record.setCoverUrl(img);
                    recordMapper.updateById(record);
                }
            }
        } catch (Exception e) {
            log.warn("重试结果回写失败 taskId={} styleId={}: {}", taskId, styleId, e.getMessage());
        }
    }

    public GenerationTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    /** 判断某款是否已经有生成图，避免支付后重复生成历史任务里已存在的图片 */
    public boolean hasStyleImage(Long taskId, String styleId) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || task.getResultJson() == null || task.getResultJson().isBlank()) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(task.getResultJson(), Map.class);
            Object stylesObj = result.get("styles");
            if (!(stylesObj instanceof List<?> list)) {
                return false;
            }
            for (Object o : list) {
                if (o instanceof Map<?, ?> item
                        && styleId.equals(String.valueOf(item.get("styleId")))) {
                    Object img = item.get("img");
                    return img != null && !String.valueOf(img).isBlank()
                            && !Boolean.TRUE.equals(item.get("failed"));
                }
            }
        } catch (Exception e) {
            log.warn("读取任务图片状态失败 taskId={} styleId={}: {}", taskId, styleId, e.getMessage());
        }
        return false;
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
        try {
            unlockMapper.insert(unlock);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发回调/重复通知下唯一索引兜底，视为已解锁
            log.debug("解锁记录已存在 user={} task={} style={}", userId, taskId, styleId);
        }
    }

    /** 全额退款后收回解锁：部分退款不调用，避免用户既没拿到全款又失去内容 */
    public void revokeUnlocks(Long userId, Long taskId, String styleIds) {
        if (userId == null || taskId == null || taskId <= 0 || styleIds == null || styleIds.isBlank()) {
            return;
        }
        List<String> ids = Arrays.stream(styleIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        unlockMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserUnlock>()
                .eq("user_id", userId)
                .eq("task_id", taskId)
                .in("style_id", ids));
    }

    public Map<String, Object> toMap(GenerationTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", task.getId());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("result", parseResult(task.getResultJson()));
        map.put("done", doneCount(task.getResultJson()));
        map.put("failed", failedCount(task.getResultJson()));
        return map;
    }

    /** 已成功出图的数量，前端进度条显示「已出图 n/N」 */
    private int doneCount(String json) {
        return countStyles(json, false);
    }

    private int failedCount(String json) {
        return countStyles(json, true);
    }

    private int countStyles(String json, boolean failed) {
        Object parsed = parseResult(json);
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("styles") instanceof List<?> styles)) {
            return 0;
        }
        int count = 0;
        for (Object o : styles) {
            if (o instanceof Map<?, ?> item) {
                boolean isFailed = Boolean.TRUE.equals(item.get("failed"));
                Object img = item.get("img");
                boolean hasImg = img != null && !String.valueOf(img).isBlank();
                if (failed ? isFailed : (hasImg && !isFailed)) {
                    count++;
                }
            }
        }
        return count;
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
}
