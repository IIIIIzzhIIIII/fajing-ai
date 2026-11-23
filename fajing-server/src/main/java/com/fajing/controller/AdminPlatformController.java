package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.PlatformAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 平台总后台：租户/门店开通、KYC、佣金规则、平台统计 */
@Tag(name = "平台总后台")
@RestController
@RequestMapping("/api/admin/platform")
@RequiredArgsConstructor
public class AdminPlatformController {

    private final PlatformAdminService platformAdminService;

    @Operation(summary = "平台总览")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(platformAdminService.dashboard());
    }

    @Operation(summary = "租户列表")
    @GetMapping("/tenants")
    public Result<List<Map<String, Object>>> tenants() {
        return Result.ok(platformAdminService.tenants());
    }

    @Operation(summary = "开通租户与店长账号")
    @PostMapping("/tenants")
    public Result<Map<String, Object>> createTenant(@RequestBody Map<String, Object> body) {
        return Result.ok(platformAdminService.createTenant(body));
    }

    @Operation(summary = "启用/停用租户")
    @PutMapping("/tenants/{id}/status")
    public Result<Void> tenantStatus(@PathVariable Long id, @RequestParam Integer status) {
        platformAdminService.updateTenantStatus(id, status);
        return Result.ok(null);
    }

    @Operation(summary = "门店列表")
    @GetMapping("/stores")
    public Result<List<Map<String, Object>>> stores() {
        return Result.ok(platformAdminService.stores());
    }

    @Operation(summary = "门店 KYC 审核")
    @PutMapping("/stores/{id}/kyc")
    public Result<Void> kyc(@PathVariable Long id, @RequestParam String status) {
        platformAdminService.updateKyc(id, status);
        return Result.ok(null);
    }

    @Operation(summary = "设置门店佣金比例")
    @PutMapping("/stores/{id}/commission")
    public Result<Void> commission(@PathVariable Long id, @RequestParam BigDecimal rate) {
        platformAdminService.updateCommissionRate(id, rate);
        return Result.ok(null);
    }

    @Operation(summary = "佣金台账")
    @GetMapping("/commissions")
    public Result<Map<String, Object>> commissions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        return Result.ok(platformAdminService.commissions(status, p, s));
    }

    @Operation(summary = "平台订单")
    @GetMapping("/orders")
    public Result<Map<String, Object>> orders(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        return Result.ok(platformAdminService.platformOrders(p, s));
    }
}
