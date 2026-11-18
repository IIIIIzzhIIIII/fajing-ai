package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.SiteSetting;
import com.fajing.service.AdminLogService;
import com.fajing.service.SiteSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端 · 基础设置（前台文案） */
@Tag(name = "管理端 · 基础设置")
@RestController
@RequestMapping("/api/admin/site-setting")
@RequiredArgsConstructor
public class AdminSiteSettingController {

    private final SiteSettingService siteSettingService;
    private final AdminLogService adminLogService;

    @Operation(summary = "读取基础设置")
    @GetMapping
    public Result<SiteSetting> get() {
        return Result.ok(siteSettingService.get());
    }

    @Operation(summary = "保存基础设置")
    @PutMapping
    public Result<SiteSetting> save(@RequestBody SiteSetting req, HttpServletRequest request) {
        Object adminId = request.getAttribute("adminId");
        SiteSetting saved = siteSettingService.save(req, adminId instanceof Long value ? value : 0L);
        adminLogService.log("SAVE_SITE_SETTING", "heroTitle:" + saved.getHeroTitleMain() + saved.getHeroTitleAccent());
        return Result.ok(saved);
    }
}
