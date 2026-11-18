package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.SiteSetting;
import com.fajing.service.SiteSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前台 · 基础设置（公开只读，登录前也要能展示协议文案） */
@Tag(name = "基础设置", description = "前台文案与联系方式")
@RestController
@RequestMapping("/api/site-settings")
@RequiredArgsConstructor
public class SiteSettingController {

    private final SiteSettingService siteSettingService;

    @Operation(summary = "读取前台基础设置")
    @GetMapping
    public Result<SiteSetting> get() {
        return Result.ok(siteSettingService.get());
    }
}
