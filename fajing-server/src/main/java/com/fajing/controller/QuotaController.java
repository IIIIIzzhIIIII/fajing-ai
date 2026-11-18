package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.CouponService;
import com.fajing.service.PointsService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "魅力值与广告", description = "魅力值余额、免费批次与激励广告奖励")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuotaController {

    private final PointsService pointsService;
    private final CouponService couponService;

    @Operation(summary = "查询我的魅力值与免费批次")
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota() {
        Long userId = UserContext.require();
        Map<String, Object> data = pointsService.toMap(pointsService.get(userId));
        data.put("couponCount", couponService.unusedCount(userId));
        return Result.ok(data);
    }

    @Operation(summary = "分享奖励：每账号限 1 次，奖励 10 点魅力值")
    @PostMapping("/share/reward")
    public Result<Map<String, Object>> shareReward() {
        return Result.ok(pointsService.claimShareReward());
    }

    @Operation(summary = "激励广告奖励：完整观看一次 +10 魅力值，每日上限 5 次")
    @PostMapping("/ad/reward")
    public Result<Map<String, Object>> adReward() {
        return Result.ok(pointsService.rewardAd());
    }
}
