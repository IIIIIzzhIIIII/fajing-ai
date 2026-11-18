package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.PointsPackage;
import com.fajing.service.PointsPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台 · 充值套餐（公开只读，仅供展示；下单金额仍以服务端为准） */
@Tag(name = "充值套餐", description = "前台展示用的套餐档位")
@RestController
@RequestMapping("/api/points/packages")
@RequiredArgsConstructor
public class PointsPackageController {

    private final PointsPackageService pointsPackageService;

    @Operation(summary = "上架套餐列表")
    @GetMapping
    public Result<List<PointsPackage>> list() {
        return Result.ok(pointsPackageService.listEnabled());
    }
}
