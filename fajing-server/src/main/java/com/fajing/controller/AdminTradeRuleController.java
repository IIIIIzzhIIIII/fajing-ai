package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.TradeRule;
import com.fajing.service.AdminLogService;
import com.fajing.service.TradeRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端 · 交易与售后规则 */
@Tag(name = "管理端 · 交易与售后规则")
@RestController
@RequestMapping("/api/admin/trade-rule")
@RequiredArgsConstructor
public class AdminTradeRuleController {

    private final TradeRuleService tradeRuleService;
    private final AdminLogService adminLogService;

    @Operation(summary = "读取售后规则")
    @GetMapping
    public Result<TradeRule> get() {
        return Result.ok(tradeRuleService.get());
    }

    @Operation(summary = "保存售后规则")
    @PutMapping
    public Result<TradeRule> save(@RequestBody TradeRule req, HttpServletRequest request) {
        Object adminId = request.getAttribute("adminId");
        TradeRule saved = tradeRuleService.save(req, adminId instanceof Long value ? value : 0L);
        adminLogService.log("SAVE_TRADE_RULE", "refundDays:" + saved.getRefundDays());
        return Result.ok(saved);
    }
}
