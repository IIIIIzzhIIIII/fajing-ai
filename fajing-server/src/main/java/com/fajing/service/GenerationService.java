package com.fajing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.HairColor;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Recommendation;
import com.fajing.entity.UserRecord;
import com.fajing.entity.UserUnlock;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.HairColorMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.RecommendationMapper;
import com.fajing.mapper.UserRecordMapper;
import com.fajing.mapper.UserUnlockMapper;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final GenerationTaskMapper taskMapper;
    private final HairstyleMapper hairstyleMapper;
    private final HairColorMapper hairColorMapper;
    private final UserRecordMapper recordMapper;
    private final UserUnlockMapper unlockMapper;
    private final RecommendationMapper recommendationMapper;
    private final ObjectMapper objectMapper;

    public GenerationTask create(String photoUrl, String gender, String mode, List<String> styleIds, String colorId,
                                 boolean freeQuota, Long recommendId) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BizException("请先上传照片");
        }
        // 小程序临时文件路径（http://tmp/、wxfile://）服务端无法访问，提前拦截避免白跑 AI
        if (photoUrl.startsWith("http://tmp/") || photoUrl.startsWith("http://usr/")
                || photoUrl.startsWith("wxfile://")) {
            throw new BizException("照片未上传成功，请重新选择照片");
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
        if (ids.size() > 12) {
            throw new BizException("同时生成数量过多");
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
        task.setRecommendId(recommendId == null ? 0L : recommendId);
        task.setAutoGen(0);
        task.setSource("user");
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    /** 员工代客试发：免费生成全部所选款式，生成后由顾客扫码领取结果 */
    public GenerationTask createEmployee(String photoUrl, String gender, List<String> styleIds,
                                         Long storeId, Long staffId, String claimToken) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BizException("请先上传照片");
        }
        if (photoUrl.startsWith("http://tmp/") || photoUrl.startsWith("http://usr/")
                || photoUrl.startsWith("wxfile://")) {
            throw new BizException("照片未上传成功，请重新选择照片");
        }
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
        if (ids.size() > 8) {
            throw new BizException("员工演示一次最多生成 8 款");
        }
        long validCount = hairstyleMapper.selectBatchIds(ids).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .count();
        if (validCount != ids.size()) {
            throw new BizException("所选发型不存在或已下架，请刷新后重试");
        }
        GenerationTask task = new GenerationTask();
        task.setUserId(UserContext.require());
        task.setStoreId(storeId == null ? 0L : storeId);
        task.setStaffId(staffId == null ? 0L : staffId);
        task.setSource("employee");
        task.setClaimToken(claimToken == null ? "" : claimToken);
        task.setClaimedUserId(0L);
        task.setPhotoUrl(photoUrl);
        task.setGender("male".equals(gender) ? "male" : "female");
        task.setMode("hair");
        task.setStyleIds(String.join(",", ids));
        task.setColorId("");
        task.setStatus("pending");
        task.setProgress(0);
        task.setFreeQuota(0);
        task.setRecommendId(0L);
        // autoGen=1 表示全部款式立即生成并在结果中直接可看
        task.setAutoGen(1);
        task.setExpireAt(LocalDateTime.now().plusHours(24));
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    /** 发色变体：单款发型 + 指定发色，创建后立即生成并在成功后解锁 */
    public GenerationTask createColor(String photoUrl, String gender, String styleId, String colorId, Long recommendId) {
        GenerationTask task = new GenerationTask();
        task.setUserId(UserContext.require());
        task.setPhotoUrl(photoUrl == null ? "" : photoUrl);
        task.setGender("male".equals(gender) ? "male" : "female");
        task.setMode("hairColor");
        task.setStyleIds(styleId == null ? "" : styleId);
        task.setColorId(colorId == null ? "" : colorId);
        task.setStatus("pending");
        task.setProgress(0);
        task.setFreeQuota(0);
        task.setAutoGen(1);
        task.setSource("user");
        task.setRecommendId(recommendId == null ? 0L : recommendId);
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

    /** 生成完成：写入 result_json、历史记录；免费批次解锁前 3 款，发色变体成功即解锁 */
    public void finish(Long taskId, Map<String, String> images, java.util.Set<String> failed) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        try {
            List<Hairstyle> styles = resolveStyles(task);
            Map<String, Map<String, Object>> styleMeta = recommendStyles(task);
            Map<String, Map<String, Object>> colorMeta = recommendColors(task);
            Map<String, Object> recommendation = recommendHeader(task);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("before", task.getPhotoUrl());
            result.put("faceShape", recommendation.getOrDefault("faceShape",
                    task.getGender().equals("male") ? "方脸" : "鹅蛋脸"));
            result.put("skinTone", recommendation.getOrDefault("skinTone", "中性皮"));
            int topScore = styleMeta.values().stream()
                    .map(m -> m.get("score"))
                    .filter(Number.class::isInstance)
                    .mapToInt(v -> ((Number) v).intValue())
                    .findFirst().orElse(ThreadLocalRandom.current().nextInt(88, 98));
            result.put("score", topScore);
            List<Map<String, Object>> items = new ArrayList<>();
            boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
            boolean autoGen = task.getAutoGen() != null && task.getAutoGen() == 1;
            boolean employee = "employee".equals(task.getSource());
            // 免费批次：仅排名前 3 款自动解锁；第 4 款起需用魅力值解锁
            Set<String> freeIds = new HashSet<>();
            if (freeBatch) {
                for (int i = 0; i < Math.min(3, styles.size()); i++) {
                    freeIds.add(styles.get(i).getId());
                }
            }
            for (int index = 0; index < styles.size(); index++) {
                Hairstyle s = styles.get(index);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("styleId", s.getId());
                item.put("name", s.getName());
                item.put("len", s.getLen());
                item.put("face", s.getFace());
                item.put("cat", s.getCat());
                Map<String, Object> meta = styleMeta.get(s.getId());
                if (meta != null) {
                    item.put("rank", meta.get("rank"));
                    item.put("score", meta.get("score"));
                    item.put("reason", meta.get("reason"));
                }
                String colorName = "";
                Map<String, Object> color = task.getColorId() == null || task.getColorId().isBlank()
                        ? null : colorMeta.get(task.getColorId());
                if (color != null) {
                    colorName = String.valueOf(color.getOrDefault("name", ""));
                    item.put("colorId", color.get("colorId"));
                    item.put("colorName", colorName);
                    item.put("colorRank", color.get("rank"));
                    item.put("colorScore", color.get("score"));
                    item.put("colorReason", color.get("reason"));
                }
                String aiUrl = images == null ? null : images.get(s.getId());
                boolean hasImage = aiUrl != null && !aiUrl.isBlank()
                        && (failed == null || !failed.contains(s.getId()));
                boolean isFree = freeIds.contains(s.getId());
                // 只预生成第 1 款：第 2/3 款仍在本批免费名额内，但等用户点击后再生成
                boolean freePending = freeBatch && index > 0 && index < 3 && !hasImage;
                boolean pending = !hasImage && (!isFree || freePending) && !autoGen;
                item.put("img", hasImage ? aiUrl : null);
                item.put("failed", !hasImage && ((isFree && !freePending) || autoGen));
                item.put("pending", pending);
                item.put("freePending", freePending);
                if (!hasImage && (isFree || autoGen) && !freePending) {
                    item.put("failReason", "生成超时/服务异常");
                }
                item.put("unlocked", hasImage && (employee || isFree || autoGen
                        || isUnlocked(task.getUserId(), taskId, s.getId())));
                items.add(item);
            }
            result.put("styles", items);
            result.put("recommendId", task.getRecommendId());
            result.put("mode", task.getMode());
            result.put("colorId", task.getColorId());

            task.setResultJson(objectMapper.writeValueAsString(result));
            task.setProgress(100);
            task.setStatus("success");
            taskMapper.updateById(task);

            if (!styles.isEmpty() && !employee) {
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

                // 免费批次的前 3 款、以及发色变体，生成成功即解锁
                for (Hairstyle s : styles) {
                    String url = images == null ? null : images.get(s.getId());
                    boolean ok = url != null && !url.isBlank() && (failed == null || !failed.contains(s.getId()));
                    if (ok && (freeIds.contains(s.getId()) || autoGen)) {
                        unlock(task.getUserId(), s.getId(), taskId);
                    }
                }
                if (autoGen && cover != null && !cover.isBlank()) {
                    attachColorImage(task.getRecommendId(), task.getColorId(),
                            resolveStyleIds(task).stream().findFirst().orElse(""), cover);
                }
            }
        } catch (Exception e) {
            log.error("生成结果写入失败 taskId={}", taskId, e);
            task.setStatus("failed");
            taskMapper.updateById(task);
        }
    }

    /**
     * 单款出图即写回结果：前端轮询时可以先看到已完成的图，不必等整批结束。
     * 只更新 result_json，不改任务状态与进度。
     */
    @SuppressWarnings("unchecked")
    public void patchStylePreview(Long taskId, String styleId, String img) {
        if (styleId == null || styleId.isBlank() || img == null || img.isBlank()) {
            return;
        }
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        try {
            Map<String, Object> result;
            if (task.getResultJson() == null || task.getResultJson().isBlank()) {
                result = skeletonResult(task);
            } else {
                result = objectMapper.readValue(task.getResultJson(), Map.class);
            }
            Object stylesObj = result.get("styles");
            if (!(stylesObj instanceof List<?> list)) {
                return;
            }
            List<String> ids = resolveStyleIds(task);
            int index = ids.indexOf(styleId);
            boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
            boolean isFree = freeBatch && index >= 0 && index < 3;
            boolean employee = "employee".equals(task.getSource());
            // 免费批次前三款出图即解锁，前端可直接点击预览
            if (isFree && !employee) {
                unlock(task.getUserId(), styleId, taskId);
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) raw;
                if (!styleId.equals(String.valueOf(item.get("styleId")))) {
                    continue;
                }
                item.put("img", img);
                item.put("failed", false);
                item.remove("pending");
                item.remove("freePending");
                item.put("unlocked", employee || isFree || isUnlocked(task.getUserId(), taskId, styleId));
                break;
            }
            taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<GenerationTask>()
                    .eq("id", taskId)
                    .set("result_json", objectMapper.writeValueAsString(result)));
        } catch (Exception e) {
            log.warn("单款预览写回失败 taskId={} styleId={}: {}", taskId, styleId, e.getMessage());
        }
    }

    /** 结果骨架：所有款式先以待生成状态落库，后续单款成功再逐个补图 */
    private Map<String, Object> skeletonResult(GenerationTask task) {
        List<Hairstyle> styles = resolveStyles(task);
        Map<String, Map<String, Object>> styleMeta = recommendStyles(task);
        Map<String, Object> recommendation = recommendHeader(task);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("before", task.getPhotoUrl());
        result.put("faceShape", recommendation.getOrDefault("faceShape",
                task.getGender().equals("male") ? "方脸" : "鹅蛋脸"));
        result.put("skinTone", recommendation.getOrDefault("skinTone", "中性皮"));
        boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
        boolean autoGen = task.getAutoGen() != null && task.getAutoGen() == 1;
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < styles.size(); i++) {
            Hairstyle s = styles.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("styleId", s.getId());
            item.put("name", s.getName());
            item.put("len", s.getLen());
            item.put("face", s.getFace());
            item.put("cat", s.getCat());
            Map<String, Object> meta = styleMeta.get(s.getId());
            if (meta != null) {
                item.put("rank", meta.get("rank"));
                item.put("score", meta.get("score"));
                item.put("reason", meta.get("reason"));
            }
            boolean isFree = freeBatch && i < 3;
            boolean freePending = freeBatch && i > 0 && i < 3;
            item.put("img", null);
            item.put("failed", false);
            item.put("pending", freePending || (!isFree && !autoGen));
            item.put("freePending", freePending);
            item.put("unlocked", false);
            items.add(item);
        }
        result.put("styles", items);
        result.put("recommendId", task.getRecommendId());
        result.put("mode", task.getMode());
        result.put("colorId", task.getColorId());
        Object topScore = styleMeta.values().stream()
                .map(m -> m.get("score")).filter(Number.class::isInstance)
                .findFirst().orElse(92);
        result.put("score", topScore);
        return result;
    }

    /** 推荐排名信息：styleId -> 排名/评分/依据 */
    private Map<String, Map<String, Object>> recommendStyles(GenerationTask task) {
        return recommendItems(task, true);
    }

    private Map<String, Map<String, Object>> recommendColors(GenerationTask task) {
        return recommendItems(task, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> recommendItems(GenerationTask task, boolean styles) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (task.getRecommendId() == null || task.getRecommendId() <= 0) {
            return map;
        }
        try {
            Recommendation row = recommendationMapper.selectById(task.getRecommendId());
            if (row == null) {
                return map;
            }
            String json = styles ? row.getStylesJson() : row.getColorsJson();
            if (json == null || json.isBlank()) {
                return map;
            }
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            for (Map<String, Object> item : list) {
                Object id = styles ? item.get("styleId") : item.get("colorId");
                if (id != null) {
                    map.put(String.valueOf(id), item);
                }
            }
        } catch (Exception e) {
            log.debug("读取推荐排名失败 taskId={}: {}", task.getId(), e.getMessage());
        }
        return map;
    }

    private Map<String, Object> recommendHeader(GenerationTask task) {
        Map<String, Object> header = new LinkedHashMap<>();
        if (task.getRecommendId() == null || task.getRecommendId() <= 0) {
            return header;
        }
        try {
            Recommendation row = recommendationMapper.selectById(task.getRecommendId());
            if (row != null) {
                header.put("faceShape", row.getFaceShape());
                header.put("skinTone", row.getSkinTone());
            }
        } catch (Exception e) {
            log.debug("读取推荐头信息失败: {}", e.getMessage());
        }
        return header;
    }

    /** 发色变体生成成功后写回推荐缓存（按「发型 + 发色」维度），下次同组合不再重复调用 AI */
    @SuppressWarnings("unchecked")
    private void attachColorImage(Long recommendId, String colorId, String styleId, String img) {
        if (recommendId == null || recommendId <= 0 || colorId == null || colorId.isBlank()
                || styleId == null || styleId.isBlank()) {
            return;
        }
        try {
            Recommendation row = recommendationMapper.selectById(recommendId);
            if (row == null || row.getColorsJson() == null || row.getColorsJson().isBlank()) {
                return;
            }
            List<Map<String, Object>> list = objectMapper.readValue(row.getColorsJson(), List.class);
            for (Map<String, Object> item : list) {
                if (colorId.equals(String.valueOf(item.get("colorId")))) {
                    Map<String, Object> variants;
                    Object exists = item.get("variants");
                    if (exists instanceof Map<?, ?> map) {
                        variants = (Map<String, Object>) map;
                    } else {
                        variants = new LinkedHashMap<>();
                        item.put("variants", variants);
                    }
                    variants.put(styleId, img);
                }
            }
            row.setColorsJson(objectMapper.writeValueAsString(list));
            recommendationMapper.updateById(row);
        } catch (Exception e) {
            log.debug("写回发色缓存失败: {}", e.getMessage());
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
                        boolean employee = "employee".equals(task.getSource());
                        if (ok) {
                            String[] ids = task.getStyleIds() == null ? new String[0] : task.getStyleIds().split(",");
                            boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
                            boolean autoGen = task.getAutoGen() != null && task.getAutoGen() == 1;
                            int index = -1;
                            for (int i = 0; i < ids.length; i++) {
                                if (styleId.equals(ids[i].trim())) {
                                    index = i;
                                    break;
                                }
                            }
                            // 免费批次前 3 款、以及发色变体重试成功后补解锁；员工演示不写个人解锁
                            if (!employee && (autoGen || (freeBatch && index > -1 && index < 3))) {
                                unlock(task.getUserId(), styleId, taskId);
                            }
                        }
                        if (ok) {
                            item.remove("failReason");
                        } else {
                            item.put("failReason", "生成超时/服务异常");
                        }
                        item.put("unlocked", ok && (employee || isUnlocked(task.getUserId(), taskId, styleId)));
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

    /** 任务包含的款式 ID（按创建时的顺序） */
    public List<String> resolveStyleIds(GenerationTask task) {
        if (task == null || task.getStyleIds() == null || task.getStyleIds().isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(task.getStyleIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
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

    /** 某任务下已解锁的款式 ID 列表（发色页实时同步「已解锁发型」） */
    public List<String> unlockedStyleIds(Long userId, Long taskId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<UserUnlock> list = unlockMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserUnlock>()
                        .eq("user_id", userId)
                        .eq("task_id", taskId == null ? 0L : taskId));
        if (list == null) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(UserUnlock::getStyleId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
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
        map.put("storeId", task.getStoreId());
        map.put("staffId", task.getStaffId());
        map.put("source", task.getSource());
        map.put("claimToken", task.getClaimToken());
        map.put("claimed", task.getClaimedUserId() != null && task.getClaimedUserId() > 0);
        map.put("claimedUserId", task.getClaimedUserId());
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
        return list;
    }
}
