package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.UserCoupon;
import com.fajing.mapper.UserCouponMapper;
import com.fajing.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理端团购券", description = "门店核销与券列表")
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final UserCouponMapper couponMapper;
    private final CouponService couponService;

    @Operation(summary = "团购券列表")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        boolean hasStatus = status != null && !status.isBlank();
        QueryWrapper<UserCoupon> countWrapper = new QueryWrapper<>();
        if (hasStatus) {
            countWrapper.eq("status", status);
        }
        Long total = couponMapper.selectCount(countWrapper);
        QueryWrapper<UserCoupon> wrapper = new QueryWrapper<UserCoupon>()
                .orderByDesc("id").last("limit " + (p - 1) * s + "," + s);
        if (hasStatus) {
            wrapper.eq("status", status);
        }
        List<UserCoupon> list = couponMapper.selectList(wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list.stream().map(couponService::toMap).collect(Collectors.toList()));
        data.put("total", total == null ? 0 : total);
        data.put("page", p);
        data.put("size", s);
        return Result.ok(data);
    }

    @Operation(summary = "门店核销核销码")
    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@RequestBody Map<String, Object> body) {
        String code = body.get("code") == null ? "" : String.valueOf(body.get("code")).trim();
        UserCoupon coupon = couponService.redeem(code);
        if (coupon == null) {
            throw new BizException("核销码无效、已使用或已过期");
        }
        return Result.ok(couponService.toMap(coupon));
    }
}
