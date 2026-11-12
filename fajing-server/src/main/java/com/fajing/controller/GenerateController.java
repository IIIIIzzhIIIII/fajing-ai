package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.dto.GenerateRequest;
import com.fajing.entity.GenerationTask;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.service.GenerationProcessor;
import com.fajing.service.GenerationService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "生成", description = "创建换发任务并轮询生成结果")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GenerateController {

    private final GenerationService generationService;
    private final GenerationProcessor processor;
    private final GenerationTaskMapper taskMapper;

    @Operation(summary = "创建换发任务")
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@Valid @RequestBody GenerateRequest req) {
        GenerationTask task = generationService.create(
                req.getPhotoUrl(), req.getGender(), req.getMode(), req.getStyleIds(), req.getColorId());
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
}
