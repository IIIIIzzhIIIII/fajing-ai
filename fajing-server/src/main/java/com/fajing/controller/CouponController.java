package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.CouponTier;
import com.fajing.entity.UserCoupon;
import com.fajing.service.CouponService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "团购券", description = "周边门店团购券档位、券包与核销")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "团购券档位（50 抵 69 等）")
    @GetMapping("/tiers")
    public Result<List<Map<String, Object>>> tiers() {
        List<CouponTier> tiers = couponService.listTiers();
        return Result.ok(tiers.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("title", t.getTitle());
            map.put("payAmount", t.getPayAmount());
            map.put("valueAmount", t.getValueAmount());
            map.put("description", t.getDescription());
            return map;
        }).collect(Collectors.toList()));
    }

    @Operation(summary = "我的券包")
    @GetMapping("/my")
    public Result<Map<String, Object>> my() {
        List<UserCoupon> list = couponService.myCoupons();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list.stream().map(couponService::toMap).collect(Collectors.toList()));
        data.put("count", couponService.unusedCount(UserContext.require()));
        return Result.ok(data);
    }

    @Operation(summary = "团购券自助退款")
    @PostMapping("/{id}/refund")
    public Result<Map<String, Object>> refund(@PathVariable Long id) {
        return Result.ok(couponService.refund(UserContext.require(), id));
    }

    @Operation(summary = "核销团购券（门店/管理端）")
    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@RequestBody Map<String, Object> body) {
        String code = body.get("code") == null ? "" : String.valueOf(body.get("code"));
        UserCoupon coupon = couponService.redeem(code);
        if (coupon == null) {
            throw new BizException("核销码无效、已使用或已过期");
        }
        return Result.ok(couponService.toMap(coupon));
    }
}
