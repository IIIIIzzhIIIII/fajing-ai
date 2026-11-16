package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.DashboardOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 管理端 · 数据看板（经营总览） */
@Tag(name = "管理端 · 数据看板")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardOverviewService dashboardOverviewService;

    @Operation(summary = "经营总览：KPI、趋势、渠道收入、待办与热门发型")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false, defaultValue = "all") String platform) {
        return Result.ok(dashboardOverviewService.overview(days, start, end, platform));
    }
}
