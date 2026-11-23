package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.AiConfig;
import com.fajing.service.AdminLogService;
import com.fajing.service.AiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "管理端 · AI 配置", description = "图像/文案模型与提示词配置（密钥读取时脱敏）")
@RestController
@RequestMapping("/api/admin/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService configService;
    private final AdminLogService adminLogService;

    @Operation(summary = "读取 AI 配置（密钥已脱敏）")
    @GetMapping
    public Result<AiConfig> get() {
        return Result.ok(configService.masked(configService.get()));
    }

    @Operation(summary = "保存 AI 配置")
    @PutMapping
    public Result<AiConfig> save(@RequestBody AiConfig req) {
        AiConfig saved = configService.save(req);
        // 记录变更审计：只记模型与提供方，不记录密钥
        adminLogService.log("SAVE_AI_CONFIG",
                saved.getImageProvider() + "/" + saved.getImageModel() + "," + saved.getTextModel());
        return Result.ok(configService.masked(saved));
    }
}
