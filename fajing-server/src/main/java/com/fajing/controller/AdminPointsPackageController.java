package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.PointsPackage;
import com.fajing.service.AdminLogService;
import com.fajing.service.PointsPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端 · 充值套餐 */
@Tag(name = "管理端 · 充值套餐")
@RestController
@RequestMapping("/api/admin/points-packages")
@RequiredArgsConstructor
public class AdminPointsPackageController {

    private final PointsPackageService pointsPackageService;
    private final AdminLogService adminLogService;

    @Operation(summary = "套餐列表（含下架）")
    @GetMapping
    public Result<List<PointsPackage>> list() {
        return Result.ok(pointsPackageService.listAll());
    }

    @Operation(summary = "新增套餐")
    @PostMapping
    public Result<PointsPackage> create(@RequestBody PointsPackage req) {
        PointsPackage saved = pointsPackageService.save(req);
        adminLogService.log("CREATE_POINTS_PACKAGE", "id:" + saved.getId() + " price:" + saved.getPrice());
        return Result.ok(saved);
    }

    @Operation(summary = "修改套餐")
    @PutMapping
    public Result<PointsPackage> update(@RequestBody PointsPackage req) {
        PointsPackage saved = pointsPackageService.save(req);
        adminLogService.log("UPDATE_POINTS_PACKAGE", "id:" + saved.getId() + " price:" + saved.getPrice());
        return Result.ok(saved);
    }

    @Operation(summary = "下架套餐")
    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        pointsPackageService.disable(id);
        adminLogService.log("DISABLE_POINTS_PACKAGE", "id:" + id);
        return Result.ok(null);
    }
}
