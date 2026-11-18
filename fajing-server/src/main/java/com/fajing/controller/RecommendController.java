package com.fajing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Recommendation;
import com.fajing.entity.UserQuota;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.service.ColorUnlockService;
import com.fajing.service.GenerationProcessor;
import com.fajing.service.GenerationService;
import com.fajing.service.PointsService;
import com.fajing.service.RecommendService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发型 / 发色排名推荐：
 * 1. 一次视觉分析拿到脸型与肤色，服务端按标签规则排序并压分到 88-99
 * 2. 排名前 3 款由生成流程预出图，第 4 款起用魅力值解锁
 * 3. 发色变体按需生成并写回缓存，避免重复调用 AI
 */
@Tag(name = "推荐", description = "脸型分析、发型排名与发色排名")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final GenerationService generationService;
    private final GenerationProcessor generationProcessor;
    private final GenerationTaskMapper taskMapper;
    private final PointsService pointsService;
    private final ColorUnlockService colorUnlockService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "分析照片并返回发型/发色排名")
    @PostMapping("/recommend")
    public Result<Map<String, Object>> recommend(@RequestBody Map<String, Object> body) {
        String photoUrl = str(body.get("photoUrl"));
        if (photoUrl.isBlank()) {
            throw new BizException("请先上传照片");
        }
        if (!photoUrl.startsWith("http")) {
            throw new BizException("照片未上传成功，请重新选择照片");
        }
        String gender = str(body.get("gender"));
        return Result.ok(recommendService.analyze(photoUrl, gender));
    }

    @Operation(summary = "读取已缓存的推荐结果")
    @GetMapping("/recommend/{id}")
    public Result<Recommendation> get(@PathVariable Long id) {
        Recommendation row = recommendService.get(id);
        if (row == null) {
            throw new BizException("推荐结果不存在或已过期");
        }
        return Result.ok(row);
    }

    @Operation(summary = "发色列表（含各发型的预览图）")
    @GetMapping("/recommend/{id}/colors")
    public Result<List<Map<String, Object>>> colors(@PathVariable Long id) {
        return Result.ok(recommendService.colorsOf(id));
    }

    /**
     * 发色页实时状态：免费生成次数、魅力值余额、已解锁发色 / 发型、各发色预览组合。
     * 前端每次进入发色页与解锁后都以此为准，避免本地缓存与解锁状态不一致。
     */
    @Operation(summary = "发色页实时状态：免费次数 / 魅力值 / 已解锁内容")
    @GetMapping("/recommend/{id}/color-state")
    public Result<Map<String, Object>> colorState(@PathVariable Long id,
                                                  @RequestParam(value = "taskId", required = false) Long taskId) {
        Long userId = UserContext.require();
        Recommendation row = recommendService.get(id);
        if (row == null) {
            throw new BizException("推荐结果不存在或已过期");
        }
        List<Map<String, Object>> colors = recommendService.colorsOf(id);
        Set<String> unlockedColors = colorUnlockService.unlockedColors(userId);
        UserQuota quota = pointsService.get(userId);
        boolean unlimited = pointsService.unlimited(quota);
        int freeBatches = quota.getFreeBatches() == null ? 0 : quota.getFreeBatches();
        boolean freeAvailable = freeBatches > 0;

        for (Map<String, Object> color : colors) {
            String colorId = str(color.get("colorId"));
            int rank = intOf(color.get("rank"), 99);
            boolean free = freeAvailable && rank <= RecommendService.FREE_TOP;
            color.put("free", free);
            color.put("unlocked", unlockedColors.contains(colorId));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recommendId", id);
        data.put("freeAvailable", freeAvailable);
        data.put("freeTop", RecommendService.FREE_TOP);
        data.put("unlimited", unlimited);
        data.put("quota", pointsService.toMap(quota));
        data.put("colors", colors);
        data.put("unlockedStyleIds", taskId == null ? List.of()
                : generationService.unlockedStyleIds(userId, taskId));
        return Result.ok(data);
    }

    /**
     * 解锁一种发色：免费次数用完后，所有发色都需要解锁。
     * 用户可选择消耗 1 次免费解锁次数（payMethod=credit）或 10 点魅力值（payMethod=points）。
     */
    @Operation(summary = "解锁一种发色（免费解锁次数 / 魅力值）")
    @PostMapping("/recommend/color/unlock")
    public Result<Map<String, Object>> unlockColor(@RequestBody Map<String, Object> body) {
        Long recommendId = longOf(body.get("recommendId"));
        String colorId = str(body.get("colorId"));
        String payMethod = str(body.get("payMethod"));
        if (recommendId == null || recommendId <= 0 || colorId.isBlank()) {
            throw new BizException("缺少发色参数");
        }
        Recommendation row = recommendService.get(recommendId);
        if (row == null) {
            throw new BizException("推荐结果不存在或已过期");
        }
        Map<String, Object> color = findItem(row.getColorsJson(), "colorId", colorId);
        if (color == null) {
            throw new BizException("该发色不在推荐列表中");
        }
        int rank = intOf(color.get("rank"), 99);
        return Result.ok(colorUnlockService.unlockColor(UserContext.require(), colorId, rank, payMethod));
    }

    /**
     * 选择发色 + 已解锁发型：
     * 1. 仅在用户已解锁的发型上生成发色，不再自动拿排名第一的发型套色
     * 2. 同「发色 + 发型」组合已生成过直接复用，不消耗 AI
     * 3. 免费次数可用时前 3 种基础发色免费；其余发色必须先解锁（见 /recommend/color/unlock）
     */
    @Operation(summary = "选择发色并生成发色变体")
    @PostMapping("/recommend/color")
    public Result<Map<String, Object>> pickColor(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.require();
        Long recommendId = longOf(body.get("recommendId"));
        String colorId = str(body.get("colorId"));
        String styleId = str(body.get("styleId"));
        Long taskId = longOf(body.get("taskId"));
        if (recommendId == null || recommendId <= 0 || colorId.isBlank()) {
            throw new BizException("缺少发色参数");
        }
        if (styleId.isBlank() || taskId == null || taskId <= 0) {
            throw new BizException("请先选择一款已解锁的发型");
        }
        Recommendation row = recommendService.get(recommendId);
        if (row == null) {
            throw new BizException("推荐结果不存在或已过期");
        }
        Map<String, Object> color = findItem(row.getColorsJson(), "colorId", colorId);
        if (color == null) {
            throw new BizException("该发色不在推荐列表中");
        }
        Map<String, Object> style = findItem(row.getStylesJson(), "styleId", styleId);
        if (style == null) {
            throw new BizException("该发型不在本次推荐中");
        }
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || !Long.valueOf(userId).equals(task.getUserId())
                || !generationService.resolveStyleIds(task).contains(styleId)) {
            throw new BizException("该发型不属于当前任务");
        }
        // 发色只能应用在已解锁的发型上，避免绕过发型解锁
        if (!generationService.isUnlocked(userId, taskId, styleId)) {
            throw new BizException("请先解锁这款发型，再选择发色");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("color", color);
        data.put("style", style);
        // 同款「发色 + 发型」已生成过，直接复用图片
        Object cached = colorVariant(color, styleId);
        if (cached != null && !String.valueOf(cached).isBlank()) {
            data.put("cached", true);
            data.put("img", cached);
            data.put("points", pointsService.toMap(pointsService.get(userId)).get("points"));
            return Result.ok(data);
        }
        int rank = intOf(color.get("rank"), 99);
        UserQuota quota = pointsService.get(userId);
        boolean unlimited = pointsService.unlimited(quota);
        int freeBatches = quota.getFreeBatches() == null ? 0 : quota.getFreeBatches();
        // 免费次数可用时前 3 种基础发色免解锁；其余发色必须先解锁（免费次数或魅力值）
        boolean free = rank <= RecommendService.FREE_TOP && freeBatches > 0;
        boolean unlocked = colorUnlockService.isUnlocked(userId, colorId);
        if (!free && !unlimited && !unlocked) {
            throw new BizException("请先解锁该发色");
        }
        var created = generationService.createColor(row.getPhotoUrl(), row.getGender(), styleId, colorId, recommendId);
        generationProcessor.process(created.getId());
        data.put("cached", false);
        data.put("taskId", created.getId());
        data.put("free", free);
        data.put("unlocked", unlocked || unlimited);
        data.put("points", pointsService.toMap(pointsService.get(userId)).get("points"));
        return Result.ok(data);
    }

    /** 读取发色缓存：按「发型 + 发色」维度复用，避免同款组合重复调用 AI */
    @SuppressWarnings("unchecked")
    private Object colorVariant(Map<String, Object> color, String styleId) {
        Object variants = color.get("variants");
        if (variants instanceof Map<?, ?> map) {
            Object img = ((Map<String, Object>) map).get(styleId);
            if (img != null && !String.valueOf(img).isBlank()) {
                return img;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findItem(String json, String key, String value) {
        for (Map<String, Object> item : listOf(json)) {
            if (value.equals(str(item.get(key)))) {
                return item;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Long longOf(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
}
