package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.UserQuota;
import com.fajing.entity.UserUnlock;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.UserUnlockMapper;
import com.fajing.service.GenerationProcessor;
import com.fajing.service.GenerationService;
import com.fajing.service.PointsService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "解锁", description = "魅力值解锁单款发型，以及已解锁款式查询")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UnlockController {

    private final UserUnlockMapper unlockMapper;
    private final GenerationTaskMapper taskMapper;
    private final GenerationService generationService;
    private final GenerationProcessor generationProcessor;
    private final PointsService pointsService;

    /** 解锁按「任务 + 款式」维度返回：同一款发型在新的一批生成里仍需重新解锁 */
    @Operation(summary = "我的解锁列表")
    @GetMapping("/unlocks")
    public Result<List<Map<String, Object>>> unlocks() {
        List<UserUnlock> list = unlockMapper.selectList(new QueryWrapper<UserUnlock>()
                .eq("user_id", UserContext.require()));
        if (list == null) {
            throw new BizException("查询解锁记录失败");
        }
        return Result.ok(list.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("taskId", u.getTaskId());
            map.put("styleId", u.getStyleId());
            return map;
        }).collect(Collectors.toList()));
    }

    /**
     * 用魅力值解锁一款发型：每款 10 点；VIP / VVIP 不限量。
     * 解锁成功后立即为该款补生成图片。
     */
    @Operation(summary = "魅力值解锁单款发型")
    @PostMapping("/unlock")
    public Result<Map<String, Object>> unlock(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.require();
        Long taskId = longOf(body.get("taskId"));
        String styleId = stringOf(body.get("styleId"));
        if (taskId == null || taskId <= 0 || styleId.isBlank()) {
            throw new BizException("缺少要解锁的款式");
        }
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || !Long.valueOf(userId).equals(task.getUserId())) {
            throw new BizException("任务不存在");
        }
        List<String> ids = generationService.resolveStyleIds(task);
        if (!ids.contains(styleId)) {
            throw new BizException("该款式不属于此任务");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        boolean already = generationService.isUnlocked(userId, taskId, styleId);
        if (already) {
            data.put("already", true);
        } else {
            boolean freeSlot = task.getFreeQuota() != null && task.getFreeQuota() == 1
                    && ids.indexOf(styleId) > -1 && ids.indexOf(styleId) < 3;
            if (!freeSlot) {
                // 优先消耗广告获得的免费解锁次数，其次扣 10 点魅力值；余额不足会抛出业务异常
                pointsService.consumeUnlock(userId);
            }
            generationService.unlock(userId, styleId, taskId);
            generationProcessor.generatePaidStyles(taskId, List.of(styleId));
        }
        data.putAll(pointsService.toMap(pointsService.get(userId)));
        data.put("taskId", taskId);
        data.put("styleId", styleId);
        return Result.ok(data);
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

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
