package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.CouponTier;
import com.fajing.entity.Store;
import com.fajing.mapper.CouponTierMapper;
import com.fajing.mapper.StoreMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 顾客端门店页：附近门店、门店详情 */
@Tag(name = "顾客端 · 门店与员工码")
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StorePublicController {

    private final JdbcTemplate jdbc;
    private final StoreMapper storeMapper;
    private final CouponTierMapper tierMapper;

    @Operation(summary = "附近合作门店")
    @GetMapping("/nearby")
    public Result<List<Map<String, Object>>> nearby() {
        return Result.ok(jdbc.queryForList(
                "SELECT s.id, s.name, s.address, s.phone, s.business_hours AS businessHours, "
                        + "(SELECT COUNT(*) FROM coupon_tier ct WHERE ct.store_id=s.id AND ct.status=1) AS couponCount, "
                        + "(SELECT MIN(ct.pay_amount) FROM coupon_tier ct WHERE ct.store_id=s.id AND ct.status=1) AS minPayAmount "
                        + "FROM store s WHERE s.status=1 AND s.kyc_status='approved' ORDER BY s.id LIMIT 30"));
    }

    @Operation(summary = "门店详情与券档位")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Store store = storeMapper.selectById(id);
        if (store == null || store.getStatus() == null || store.getStatus() != 1) {
            throw new BizException("门店不存在或已停用");
        }
        List<CouponTier> tiers = tierMapper.selectList(new QueryWrapper<CouponTier>()
                .eq("store_id", id).eq("status", 1).orderByAsc("sort").orderByAsc("id"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", store.getId());
        data.put("name", store.getName());
        data.put("brandName", store.getBrandName());
        data.put("address", store.getAddress());
        data.put("phone", store.getPhone());
        data.put("businessHours", store.getBusinessHours());
        data.put("dailyCapacity", store.getDailyCapacity());
        data.put("tiers", tiers.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("title", t.getTitle());
            map.put("payAmount", t.getPayAmount());
            map.put("valueAmount", t.getValueAmount());
            map.put("description", t.getDescription());
            return map;
        }).collect(Collectors.toList()));
        return Result.ok(data);
    }
}
