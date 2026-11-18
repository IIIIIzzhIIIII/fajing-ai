package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.CouponTier;
import com.fajing.mapper.CouponTierMapper;
import com.fajing.service.AdminLogService;
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

import java.math.BigDecimal;
import java.util.List;

/** 管理端 · 团购券档位 */
@Tag(name = "管理端 · 团购券档位")
@RestController
@RequestMapping("/api/admin/coupon-tiers")
@RequiredArgsConstructor
public class AdminCouponTierController {

    private final CouponTierMapper couponTierMapper;
    private final AdminLogService adminLogService;

    @Operation(summary = "档位列表（含下架）")
    @GetMapping
    public Result<List<CouponTier>> list() {
        return Result.ok(couponTierMapper.selectList(new QueryWrapper<CouponTier>()
                .orderByAsc("sort").orderByAsc("id")));
    }

    @Operation(summary = "新增档位")
    @PostMapping
    public Result<CouponTier> create(@RequestBody CouponTier req) {
        validate(req);
        req.setId(null);
        couponTierMapper.insert(req);
        adminLogService.log("CREATE_COUPON_TIER", "id:" + req.getId() + " pay:" + req.getPayAmount());
        return Result.ok(couponTierMapper.selectById(req.getId()));
    }

    @Operation(summary = "修改档位")
    @PutMapping
    public Result<CouponTier> update(@RequestBody CouponTier req) {
        if (req.getId() == null || couponTierMapper.selectById(req.getId()) == null) {
            throw new BizException("档位不存在");
        }
        validate(req);
        couponTierMapper.updateById(req);
        adminLogService.log("UPDATE_COUPON_TIER", "id:" + req.getId() + " pay:" + req.getPayAmount());
        return Result.ok(couponTierMapper.selectById(req.getId()));
    }

    @Operation(summary = "下架档位")
    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        CouponTier tier = couponTierMapper.selectById(id);
        if (tier == null) {
            throw new BizException("档位不存在");
        }
        tier.setStatus(0);
        couponTierMapper.updateById(tier);
        adminLogService.log("DISABLE_COUPON_TIER", "id:" + id);
        return Result.ok(null);
    }

    /** 下架不物理删除，历史订单仍能按 tierId 回查 */
    private void validate(CouponTier req) {
        if (req.getPayAmount() == null || req.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("支付金额必须大于 0");
        }
        if (req.getValueAmount() == null || req.getValueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("抵扣金额必须大于 0");
        }
        req.setTitle(req.getTitle() == null ? "" : req.getTitle().trim());
        req.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
        req.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        req.setSort(req.getSort() == null ? 0 : req.getSort());
    }
}
