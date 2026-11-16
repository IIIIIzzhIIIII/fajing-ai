package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.dto.GenerateRequest;
import com.fajing.entity.GenerationTask;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.service.GenerationProcessor;
import com.fajing.service.GenerationService;
import com.fajing.service.QuotaService;
import com.fajing.service.RateLimitService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "生成", description = "创建换发任务并轮询生成结果")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GenerateController {

    private final GenerationService generationService;
    private final GenerationProcessor processor;
    private final GenerationTaskMapper taskMapper;
    private final RateLimitService rateLimitService;
    private final QuotaService quotaService;

    /** 单用户每日生成次数上限 */
    @Value("${quota.daily-max:5}")
    private int dailyMax;

    @Operation(summary = "创建换发任务")
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@Valid @RequestBody GenerateRequest req) {
        Long userId = UserContext.require();
        rateLimitService.checkGenerate(userId);
        Long todayCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .eq("user_id", userId)
                .ge("created_at", LocalDate.now().atStartOfDay()));
        if (todayCount != null && todayCount >= dailyMax) {
            throw new BizException("今日生成次数已达上限（" + dailyMax + " 次），明天再来试试吧");
        }
        // 额度以服务端为准：免费/分享/广告额度统一从同一个池子扣减，避免绕过前端重复生成
        quotaService.consume("free");
        GenerationTask task;
        try {
            task = generationService.create(
                    req.getPhotoUrl(), req.getGender(), req.getMode(), req.getStyleIds(), req.getColorId());
        } catch (RuntimeException e) {
            // 参数校验失败等场景没有真正发起生成，立即退还本次额度
            quotaService.refund(userId, null, "创建任务失败");
            throw e;
        }
        processor.process(task.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        return Result.ok(data);
    }

    @Operation(summary = "查询任务状态与结果（仅本人任务）")
    @GetMapping("/generate/{taskId}")
    public Result<Map<String, Object>> task(@PathVariable Long taskId) {
        GenerationTask task = taskMapper.selectById(taskId);
        // 不是自己的任务统一按不存在处理，避免探测他人任务
        if (task == null || !Long.valueOf(UserContext.require()).equals(task.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return Result.ok(generationService.toMap(task));
    }

    @Operation(summary = "重试失败的一款发型（仅失败款允许重试，避免对成功款无限免费调用 AI）")
    @PostMapping("/generate/{taskId}/retry")
    public Result<Map<String, Object>> retry(@PathVariable Long taskId,
                                             @RequestBody(required = false) Map<String, String> body) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || !Long.valueOf(UserContext.require()).equals(task.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        if ("pending".equals(task.getStatus()) || (task.getResultJson() == null || task.getResultJson().isBlank())) {
            throw new BizException("任务尚未完成，无法重试");
        }
        String styleId = body == null ? null : body.get("styleId");
        if (styleId == null || styleId.isBlank()) {
            throw new BizException("缺少要重试的款式");
        }
        String taskStyleIds = task.getStyleIds();
        if (taskStyleIds == null || !java.util.Arrays.asList(taskStyleIds.split(",")).contains(styleId)) {
            throw new BizException("该款式不属于此任务");
        }
        // 只有失败款允许重试，避免绕过额度系统对成功款无限免费调用 AI
        ensureRetryable(task, styleId);
        processor.retry(taskId, styleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retried", true);
        data.put("styleId", styleId);
        return Result.ok(data);
    }

    /** 检查指定款式是否处于失败状态，成功款禁止重试 */
    @SuppressWarnings("unchecked")
    private void ensureRetryable(GenerationTask task, String styleId) {
        Object parsed = generationService.toMap(task).get("result");
        if (!(parsed instanceof Map)) {
            throw new BizException("任务结果异常，无法重试");
        }
        Object styles = ((Map<String, Object>) parsed).get("styles");
        if (!(styles instanceof List<?> list)) {
            throw new BizException("任务结果异常，无法重试");
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> item)) continue;
            if (!styleId.equals(String.valueOf(item.get("styleId")))) continue;
            if (Boolean.TRUE.equals(item.get("pending"))) {
                throw new BizException("该款式需先解锁，支付成功后会自动生成");
            }
            boolean failed = Boolean.TRUE.equals(item.get("failed"));
            Object img = item.get("img");
            boolean hasImg = img != null && !String.valueOf(img).isBlank();
            if (!failed && hasImg) {
                throw new BizException("该款式已生成成功，无需重试");
            }
            return;
        }
        throw new BizException("该款式不在任务结果中，无法重试");
    }
}
