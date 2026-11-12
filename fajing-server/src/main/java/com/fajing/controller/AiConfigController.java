package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.AiConfig;
import com.fajing.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 AI 设置：图像模型、文案模型、提示词均可在线修改
 * 由 AdminInterceptor 校验 X-Admin-Token
 */
@RestController
@RequestMapping("/api/admin/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService configService;

    @GetMapping
    public Result<AiConfig> get() {
        return Result.ok(configService.get());
    }

    @PutMapping
    public Result<AiConfig> save(@RequestBody AiConfig req) {
        return Result.ok(configService.save(req));
    }
}
