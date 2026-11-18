package com.fajing.service;

import com.fajing.entity.AiCallLog;
import com.fajing.entity.AiConfig;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.HairColor;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Recommendation;
import com.fajing.mapper.AiCallLogMapper;
import com.fajing.mapper.HairColorMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.RecommendationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 真实 AI 出图流程：
 * 1. 读出任务与所选发型
 * 2. 为每一款生成编辑指令（模板 / 文案模型增强）
 * 3. 调用图像模型并行换发（最多 2 并发，避免触发限流）
 * 4. 失败的款式标记 failed，不再回退假图，可在结果页单款重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationProcessor {

    private final GenerationService generationService;
    private final AiConfigService aiConfigService;
    private final AiPromptService promptService;
    private final AiImageService imageService;
    private final HairstyleMapper hairstyleMapper;
    private final HairColorMapper hairColorMapper;
    private final RecommendationMapper recommendationMapper;
    private final PointsService pointsService;
    private final AiCallLogMapper aiCallLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 发色预览图单独的低并发线程池，避免和主流程抢并发额度 */
    private static final ExecutorService PREVIEW_POOL = Executors.newFixedThreadPool(2);
    /** 正在生成中的「发色 + 发型」组合，防止重复提交 */
    private static final Set<String> PREVIEW_JOBS = ConcurrentHashMap.newKeySet();
    private static final Object PREVIEW_LOCK = new Object();

    /** 单次图像调用的估算成本（分），用于管理端成本统计 */
    @Value("${ai.cost-cents-per-call:20}")
    private int costCentsPerCall;

    @Async
    public void process(Long taskId) {
        GenerationTask task = generationService.getTask(taskId);
        if (task == null) {
            log.warn("生成任务不存在 taskId={}", taskId);
            return;
        }
        // 进度只在关键节点写值：任务领取 5% → AI 调用开始 10% → 每完成一张推进 → finish 置 100%
        generationService.updateProgress(taskId, 5);

        List<Hairstyle> allStyles = resolveStyles(task);
        if (allStyles.isEmpty()) {
            // 发型被删除/下架导致无可生成内容时，本次免费批次需要退还
            if (task.getFreeQuota() != null && task.getFreeQuota() == 1) {
                pointsService.refundFreeBatch(task.getUserId());
            }
            generationService.finish(taskId, Map.of(), Set.of());
            return;
        }
        boolean freeBatch = task.getFreeQuota() != null && task.getFreeQuota() == 1;
        boolean autoGen = task.getAutoGen() != null && task.getAutoGen() == 1;
        // 普通付费批次：不预生成任何图，等用户用魅力值解锁后再按需调用 AI
        if (!freeBatch && !autoGen) {
            generationService.finish(taskId, Map.of(), Set.of());
            return;
        }
        // 临时节省 Token：免费批次只预生成排名第 1 款发型，其余款式解锁后再生成
        List<Hairstyle> styles = freeBatch && !autoGen
                ? allStyles.subList(0, 1)
                : allStyles;
        generationService.updateProgress(taskId, 10);

        Map<String, String> images = new ConcurrentHashMap<>();
        Set<String> failed = ConcurrentHashMap.newKeySet();
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(2, styles.size())));
        try {
            String colorName = colorName(task.getColorId());
            String colorEn = colorEn(task.getColorId());
            List<Future<?>> futures = new ArrayList<>();
            for (Hairstyle s : styles) {
                futures.add(pool.submit(() -> {
                    try {
                        String url = generateOne(task, s, colorName, colorEn);
                        images.put(s.getId(), url);
                        // 单款出图立刻写回结果，前端可以边生成边看到预览
                        generationService.patchStylePreview(taskId, s.getId(), url);
                        logCall(task, true);
                    } catch (Exception e) {
                        failed.add(s.getId());
                        logCall(task, false);
                        log.warn("AI 换发失败 taskId={} style={}: {}", taskId, s.getName(), e.getMessage());
                    } finally {
                        int finished = done.incrementAndGet();
                        generationService.updateProgress(taskId, 10 + (int) Math.round(80.0 * finished / styles.size()));
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(200, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("等待 AI 出图超时 taskId={}: {}", taskId, e.getMessage());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // 超时或没拿到图的也算失败
        for (Hairstyle s : styles) {
            if (!images.containsKey(s.getId())) {
                failed.add(s.getId());
            }
        }

        // 免费批次整批全部失败：先退还免费批次再落结果，保证前端看到 success 时额度已回退
        if (failed.size() == styles.size() && freeBatch && !autoGen) {
            pointsService.refundFreeBatch(task.getUserId());
        }
        // 发色变体整批失败：非会员退还本次消耗的 10 点魅力值
        if (failed.size() == styles.size() && autoGen) {
            com.fajing.entity.UserQuota quota = pointsService.get(task.getUserId());
            if (!pointsService.unlimited(quota)) {
                pointsService.addPoints(task.getUserId(), PointsService.UNLOCK_COST);
            }
        }
        generationService.finish(taskId, images, failed);
        // 前 3 款发型出图后，后台补「前 3 种发色」预览，供发色页直接展示小图
        if (freeBatch && !autoGen) {
            List<String> okIds = styles.stream()
                    .filter(s -> images.containsKey(s.getId()) && !failed.contains(s.getId()))
                    .map(Hairstyle::getId)
                    .toList();
            generateColorPreviews(taskId, okIds);
        }
    }

    /** 支付成功后生成剩余款式：只补未生成过的款，成功后回写结果 */
    @Async
    public void generatePaidStyles(Long taskId, List<String> styleIds) {
        GenerationTask task = generationService.getTask(taskId);
        if (task == null || styleIds == null || styleIds.isEmpty()) {
            return;
        }
        List<Hairstyle> targets = resolveStyles(task).stream()
                .filter(s -> styleIds.contains(s.getId()))
                .filter(s -> !generationService.hasStyleImage(taskId, s.getId()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        String colorName = colorName(task.getColorId());
        String colorEn = colorEn(task.getColorId());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(2, targets.size())));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Hairstyle s : targets) {
                futures.add(pool.submit(() -> {
                    try {
                        String url = generateOne(task, s, colorName, colorEn);
                        generationService.patchStyle(taskId, s.getId(), url);
                        logCall(task, true);
                    } catch (Exception e) {
                        log.warn("付费款生成失败 taskId={} style={}: {}", taskId, s.getName(), e.getMessage());
                        generationService.patchStyle(taskId, s.getId(), null);
                        logCall(task, false);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(200, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("等待付费款出图超时 taskId={}: {}", taskId, e.getMessage());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        // 新解锁的款式暂不预生成发色预览，避免额外 Token 消耗
    }

    /**
     * 为指定发型生成第 1 种发色的预览图，写入推荐记录的 colors_json.variants。
     * 已生成过的「发型 + 发色」组合直接跳过。
     */
    public void generateColorPreviews(Long taskId, List<String> styleIds) {
        if (styleIds == null || styleIds.isEmpty()) {
            return;
        }
        GenerationTask task = generationService.getTask(taskId);
        if (task == null || task.getRecommendId() == null || task.getRecommendId() <= 0) {
            return;
        }
        Recommendation row = recommendationMapper.selectById(task.getRecommendId());
        if (row == null) {
            return;
        }
        List<Map<String, Object>> colors = parseList(row.getColorsJson());
        List<Map<String, Object>> topColors = colors.stream()
                .filter(c -> intOf(c.get("rank"), 99) <= RecommendService.FREE_TOP)
                // 临时节省 Token：只生成排名第 1 种发色
                .limit(1)
                .toList();
        if (topColors.isEmpty()) {
            return;
        }
        for (String styleId : styleIds) {
            if (styleId == null || styleId.isBlank()) {
                continue;
            }
            Hairstyle style = hairstyleMapper.selectById(styleId);
            if (style == null) {
                continue;
            }
            for (Map<String, Object> color : topColors) {
                String colorId = str(color.get("colorId"));
                if (colorId.isBlank() || hasVariant(row, colorId, styleId)) {
                    continue;
                }
                final String key = task.getRecommendId() + ":" + styleId + ":" + colorId;
                if (!PREVIEW_JOBS.add(key)) {
                    continue;
                }
                PREVIEW_POOL.submit(() -> {
                    try {
                        HairColor hc = hairColorMapper.selectById(colorId);
                        String colorName = hc == null ? str(color.get("name")) : hc.getName();
                        String colorEn = hc == null || hc.getEn() == null ? "" : hc.getEn();
                        String prompt = promptService.build(style, colorName, colorEn);
                        String ref = referenceUrl(style, colorEn);
                        if (ref != null) {
                            prompt = "图1是人物原图，图2是目标发型参考。请把图1人物的头发换成图2中的发型，"
                                    + "图1人物的脸部、眉毛、五官、表情（笑容、嘴部开合、是否露齿、眼神）必须保持完全不变。\n\n" + prompt;
                        }
                        prompt = genderHint(task.getGender()) + "\n" + prompt;
                        String url = imageService.editHairColor(task.getPhotoUrl(), ref, prompt,
                                task.getId(), style.getId(), colorEn);
                        synchronized (PREVIEW_LOCK) {
                            attachColorVariant(task.getRecommendId(), colorId, styleId, url);
                        }
                        logCall(task, true);
                        log.info("发色预览完成 taskId={} style={} color={}", taskId, style.getName(), colorName);
                    } catch (Exception e) {
                        log.warn("发色预览失败 taskId={} style={} color={}: {}",
                                taskId, style.getName(), color.get("name"), e.getMessage());
                        logCall(task, false);
                    } finally {
                        PREVIEW_JOBS.remove(key);
                    }
                });
            }
        }
    }

    private boolean hasVariant(Recommendation row, String colorId, String styleId) {
        for (Map<String, Object> item : parseList(row.getColorsJson())) {
            if (!colorId.equals(str(item.get("colorId")))) {
                continue;
            }
            Object variants = item.get("variants");
            if (variants instanceof Map<?, ?> map) {
                Object img = map.get(styleId);
                return img != null && !String.valueOf(img).isBlank();
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void attachColorVariant(Long recommendId, String colorId, String styleId, String img) {
        Recommendation row = recommendationMapper.selectById(recommendId);
        if (row == null || row.getColorsJson() == null || row.getColorsJson().isBlank()) {
            return;
        }
        List<Map<String, Object>> list = parseList(row.getColorsJson());
        boolean changed = false;
        for (Map<String, Object> item : list) {
            if (!colorId.equals(str(item.get("colorId")))) {
                continue;
            }
            Map<String, Object> variants;
            Object exists = item.get("variants");
            if (exists instanceof Map<?, ?> map) {
                variants = (Map<String, Object>) map;
            } else {
                variants = new LinkedHashMap<>();
                item.put("variants", variants);
            }
            variants.put(styleId, img);
            if (item.get("img") == null || String.valueOf(item.get("img")).isBlank()) {
                item.put("img", img);
            }
            changed = true;
            break;
        }
        if (changed) {
            try {
                row.setColorsJson(objectMapper.writeValueAsString(list));
                recommendationMapper.updateById(row);
            } catch (Exception e) {
                log.debug("写回发色预览失败: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intOf(Object value, int def) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }

    /** 单款重试：只重跑失败的那一款，成功后回写结果 */
    @Async
    public void retry(Long taskId, String styleId) {
        GenerationTask task = generationService.getTask(taskId);
        if (task == null) {
            return;
        }
        Hairstyle s = hairstyleMapper.selectById(styleId);
        if (s == null) {
            return;
        }
        try {
            String url = generateOne(task, s, colorName(task.getColorId()), colorEn(task.getColorId()));
            generationService.patchStyle(taskId, styleId, url);
            logCall(task, true);
            // 只有第 1 款失败重试成功后才补发色预览，其余款式暂不预生成
            String firstStyleId = generationService.resolveStyleIds(task).stream().findFirst().orElse("");
            if (styleId.equals(firstStyleId)
                    && (task.getAutoGen() == null || task.getAutoGen() != 1)) {
                generateColorPreviews(taskId, List.of(styleId));
            }
        } catch (Exception e) {
            log.warn("重试失败 taskId={} styleId={}: {}", taskId, styleId, e.getMessage());
            generationService.patchStyle(taskId, styleId, null);
            logCall(task, false);
        }
    }

    /** 记录一次 AI 调用的成本流水，失败不影响主流程 */
    private void logCall(GenerationTask task, boolean ok) {
        try {
            AiConfig config = aiConfigService.get();
            AiCallLog logRow = new AiCallLog();
            logRow.setTaskId(task.getId());
            logRow.setUserId(task.getUserId());
            logRow.setProvider(config == null || config.getImageProvider() == null ? "" : config.getImageProvider());
            logRow.setModel(config == null || config.getImageModel() == null ? "" : config.getImageModel());
            logRow.setCostCents(ok ? costCentsPerCall : 0);
            logRow.setOk(ok);
            logRow.setCreatedAt(LocalDateTime.now());
            aiCallLogMapper.insert(logRow);
        } catch (Exception e) {
            log.debug("AI 调用流水写入失败: {}", e.getMessage());
        }
    }

    /** 性别锁定：用户选的男/女款优先级高于原图人物性别，避免生成相反性别的发型 */
    private static String genderHint(String gender) {
        if ("male".equals(gender)) {
            return "【性别锁定 · 男款】本次选用男款发型库，必须严格按图2的男款发型轮廓、长度、层次和刘海执行；"
                    + "即使图1人物是女性，也只把头发换成这款男款发型，不要保留长发、不要改成女性化卷发；"
                    + "图2只参考发型，不要参考图2人物的脸、五官和性别。";
        }
        return "【性别锁定 · 女款】本次选用女款发型库，必须严格按图2的女款发型轮廓、长度、层次和刘海执行；"
                + "不要出现男性推剪寸头、板寸等男款样式；图2只参考发型，不要参考图2人物的脸、五官和性别。";
    }

    private String generateOne(GenerationTask task, Hairstyle s, String colorName, String colorEn) throws Exception {
        boolean colorMode = task.getColorId() != null && !task.getColorId().isBlank();
        String prompt = promptService.build(s, colorName, colorEn);
        String ref = referenceUrl(s, colorEn);
        if (ref != null) {
            prompt = "图1是人物原图，图2是目标发型参考。请把图1人物的头发换成图2中的发型，"
                    + "图1人物的脸部、眉毛、五官、表情（笑容、嘴部开合、是否露齿、眼神）必须保持完全不变。\n\n" + prompt;
        }
        // 用户明确选择的男女款优先级最高：只换发型，不因原图人物性别而改成另一性别款式
        prompt = genderHint(task.getGender()) + "\n" + prompt;
        log.info("开始 AI 换发 taskId={} style={} colors={} ref={} prompt={}", task.getId(), s.getName(), colorMode, ref != null, prompt);
        String url = colorMode
                ? imageService.editHairColor(task.getPhotoUrl(), ref, prompt, task.getId(), s.getId(), colorEn)
                : imageService.editHair(task.getPhotoUrl(), ref, prompt, task.getId(), s.getId());
        log.info("AI 换发完成 taskId={} style={} url={}", task.getId(), s.getName(), url);
        return url;
    }

    private List<Hairstyle> resolveStyles(GenerationTask task) {
        List<String> ids = new ArrayList<>(Arrays.asList(task.getStyleIds().split(",")));
        ids.removeIf(String::isBlank);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
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

    private String colorName(String colorId) {
        if (colorId == null || colorId.isBlank()) {
            return "";
        }
        try {
            HairColor c = hairColorMapper.selectById(colorId);
            return c == null || c.getName() == null ? "" : c.getName();
        } catch (Exception e) {
            return "";
        }
    }

    private String colorEn(String colorId) {
        if (colorId == null || colorId.isBlank()) {
            return "";
        }
        try {
            HairColor c = hairColorMapper.selectById(colorId);
            return c == null || c.getEn() == null ? "" : c.getEn();
        } catch (Exception e) {
            return "";
        }
    }

    /** 用发型英文描述 + 发色生成参考肖像，作为 image2 传给编辑模型 */
    private String referenceUrl(Hairstyle s, String colorEn) {
        // 后台配置的示例照片优先：运营上传的正脸图比自动生成更准确
        if (s.getImgUrl() != null && !s.getImgUrl().isBlank()) {
            return s.getImgUrl();
        }
        if (s.getEn() == null || s.getEn().isBlank()) {
            return null;
        }
        String who = "male".equals(s.getGender()) ? "young asian man" : "young asian woman";
        // 未选发色时统一黑色，避免参考图自带蓝灰发色被"贴"到结果上
        String color = (colorEn == null || colorEn.isBlank()) ? "black hair" : colorEn + " hair";
        String p = who + ", " + s.getEn() + ", " + color
                + ", front facing portrait, looking straight at camera, head level and centered, "
                + "symmetrical frontal face, no side profile, no tilted head, "
                + "modern trendy hairstyle, natural hair texture, detailed hair strands, "
                + "natural window light, clean neutral background, photorealistic, 85mm lens, high detail";
        try {
            return "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt="
                    + URLEncoder.encode(p, StandardCharsets.UTF_8) + "&image_size=square";
        } catch (Exception e) {
            return null;
        }
    }
}
