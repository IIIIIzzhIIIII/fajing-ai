package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "额度与分享", description = "免费额度查询、消耗与分享奖励")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @Operation(summary = "查询我的额度")
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota() {
        return Result.ok(quotaService.toMap(quotaService.get()));
    }

    @Operation(summary = "消耗一次额度")
    @PostMapping("/quota/consume")
    public Result<Map<String, Object>> consume(@RequestBody Map<String, String> body) {
        return Result.ok(quotaService.toMap(quotaService.consume(body.get("type"))));
    }

    /** 分享奖励：每账号终身仅 1 次 */
    @Operation(summary = "分享奖励（每账号终身 1 次）")
    @PostMapping("/share/reward")
    public Result<Map<String, Object>> shareReward() {
        return Result.ok(quotaService.toMap(quotaService.consume("share")));
    }
}
