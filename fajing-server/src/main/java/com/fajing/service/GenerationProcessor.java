package com.fajing.service;

import com.fajing.entity.AiCallLog;
import com.fajing.entity.AiConfig;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.HairColor;
import com.fajing.entity.Hairstyle;
import com.fajing.mapper.AiCallLogMapper;
import com.fajing.mapper.HairColorMapper;
import com.fajing.mapper.HairstyleMapper;
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
    private final QuotaService quotaService;
    private final AiCallLogMapper aiCallLogMapper;

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

        List<Hairstyle> styles = resolveStyles(task);
        if (styles.isEmpty()) {
            generationService.finish(taskId, Map.of(), Set.of());
            return;
        }
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
                        images.put(s.getId(), generateOne(task, s, colorName, colorEn));
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

        generationService.finish(taskId, images, failed);

        // 整批全部失败：自动退还 1 次额度并记流水
        if (!styles.isEmpty() && failed.size() == styles.size()) {
            quotaService.refund(task.getUserId(), taskId, "生成全部失败");
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

    private String generateOne(GenerationTask task, Hairstyle s, String colorName, String colorEn) throws Exception {
        String prompt = promptService.build(s, colorName);
        String ref = referenceUrl(s, colorEn);
        if (ref != null) {
            prompt = "图1是人物原图，图2是目标发型参考。请把图1人物的头发换成图2中的发型，"
                    + "图1人物的脸部、眉毛、五官、表情（笑容、嘴部开合、是否露齿、眼神）必须保持完全不变。\n\n" + prompt;
        }
        log.info("开始 AI 换发 taskId={} style={} ref={} prompt={}", task.getId(), s.getName(), ref != null, prompt);
        String url = imageService.editHair(task.getPhotoUrl(), ref, prompt, task.getId(), s.getId());
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
        return list.size() > 4 ? new ArrayList<>(list.subList(0, 4)) : list;
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
