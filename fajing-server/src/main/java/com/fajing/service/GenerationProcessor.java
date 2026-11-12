package com.fajing.service;

import com.fajing.entity.GenerationTask;
import com.fajing.entity.HairColor;
import com.fajing.entity.Hairstyle;
import com.fajing.mapper.HairColorMapper;
import com.fajing.mapper.HairstyleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 真实 AI 出图流程：
 * 1. 读出任务与所选发型
 * 2. 为每一款生成编辑指令（模板 / 文案模型增强）
 * 3. 调用图像模型并行换发（最多 2 并发，避免触发限流）
 * 4. 任一失败回退占位图，保证任务能完成
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

    @Async
    public void process(Long taskId) {
        GenerationTask task = generationService.getTask(taskId);
        if (task == null) {
            log.warn("生成任务不存在 taskId={}", taskId);
            return;
        }

        List<Hairstyle> styles = resolveStyles(task);
        if (styles.isEmpty()) {
            generationService.finish(taskId, Map.of());
            return;
        }

        // 进度条：真实调用期间缓慢推进到 90%，完成后由 finish 置 100%
        AtomicInteger progress = new AtomicInteger(5);
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
        ticker.scheduleAtFixedRate(() -> {
            int v = Math.min(90, progress.addAndGet(3));
            generationService.updateProgress(taskId, v);
        }, 1500, 1500, TimeUnit.MILLISECONDS);

        Map<String, String> images = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(2, styles.size())));
        try {
            String colorName = colorName(task.getColorId());
            String colorEn = colorEn(task.getColorId());
            List<Future<?>> futures = new ArrayList<>();
            for (Hairstyle s : styles) {
                futures.add(pool.submit(() -> {
                    try {
                        String prompt = promptService.build(s, colorName);
                        String ref = referenceUrl(s, colorEn);
                        if (ref != null) {
                            prompt = "图1是人物原图，图2是目标发型参考。请把图1人物的头发换成图2中的发型，"
                                    + "图1人物的脸部、眉毛、五官、表情（笑容、嘴部开合、是否露齿、眼神）必须保持完全不变。\n\n" + prompt;
                        }
                        log.info("开始 AI 换发 taskId={} style={} ref={} prompt={}", taskId, s.getName(), ref != null, prompt);
                        String url = imageService.editHair(task.getPhotoUrl(), ref, prompt, taskId, s.getId());
                        images.put(s.getId(), url);
                        log.info("AI 换发完成 taskId={} style={} url={}", taskId, s.getName(), url);
                    } catch (Exception e) {
                        log.warn("AI 换发失败，使用占位图 taskId={} style={}: {}", taskId, s.getName(), e.getMessage());
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
            ticker.shutdownNow();
        }

        generationService.finish(taskId, images);
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
