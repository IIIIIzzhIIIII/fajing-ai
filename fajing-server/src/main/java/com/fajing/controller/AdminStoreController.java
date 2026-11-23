package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.entity.CouponTier;
import com.fajing.service.StoreAdminService;
import com.fajing.util.AdminContext;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 店长后台：只允许 STORE_MANAGER 访问（平台管理员可通过 X-Store-Id 查看） */
@Tag(name = "店长后台")
@RestController
@RequestMapping("/api/admin/store")
@RequiredArgsConstructor
public class AdminStoreController {

    private final StoreAdminService storeAdminService;

    @Operation(summary = "门店资料")
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(storeAdminService.profile(AdminContext.requireStoreId()));
    }

    @Operation(summary = "修改门店资料")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        return Result.ok(storeAdminService.updateProfile(AdminContext.requireStoreId(), body));
    }

    @Operation(summary = "店长数据看板")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(storeAdminService.dashboard(AdminContext.requireStoreId(), days));
    }

    @Operation(summary = "门店券档位")
    @GetMapping("/coupons")
    public Result<List<CouponTier>> coupons() {
        return Result.ok(storeAdminService.couponTiers(AdminContext.requireStoreId()));
    }

    @Operation(summary = "新增券档位")
    @PostMapping("/coupons")
    public Result<CouponTier> createCoupon(@RequestBody CouponTier body) {
        return Result.ok(storeAdminService.createCouponTier(AdminContext.requireStoreId(), body));
    }

    @Operation(summary = "修改券档位")
    @PutMapping("/coupons")
    public Result<CouponTier> updateCoupon(@RequestBody CouponTier body) {
        return Result.ok(storeAdminService.updateCouponTier(AdminContext.requireStoreId(), body));
    }

    @Operation(summary = "下架券档位")
    @DeleteMapping("/coupons/{id}")
    public Result<Void> disableCoupon(@PathVariable Long id) {
        storeAdminService.disableCouponTier(AdminContext.requireStoreId(), id);
        return Result.ok(null);
    }

    @Operation(summary = "员工列表与业绩")
    @GetMapping("/staff")
    public Result<List<Map<String, Object>>> staff() {
        return Result.ok(storeAdminService.staffList(AdminContext.requireStoreId()));
    }

    @Operation(summary = "新增员工")
    @PostMapping("/staff")
    public Result<Map<String, Object>> createStaff(@RequestBody Map<String, Object> body) {
        Long storeId = AdminContext.requireStoreId();
        Map<String, Object> store = storeAdminService.profile(storeId);
        Long tenantId = ((Number) store.get("tenantId")).longValue();
        return Result.ok(storeAdminService.createStaff(storeId, tenantId, body));
    }

    @Operation(summary = "修改员工")
    @PutMapping("/staff/{id}")
    public Result<Map<String, Object>> updateStaff(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return Result.ok(storeAdminService.updateStaff(AdminContext.requireStoreId(), id, body));
    }

    @Operation(summary = "删除员工")
    @DeleteMapping("/staff/{id}")
    public Result<Void> deleteStaff(@PathVariable Long id) {
        storeAdminService.deleteStaff(AdminContext.requireStoreId(), id);
        return Result.ok(null);
    }

    @Operation(summary = "门店订单")
    @GetMapping("/orders")
    public Result<Map<String, Object>> orders(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        return Result.ok(storeAdminService.orders(AdminContext.requireStoreId(), status, p, s));
    }

    @Operation(summary = "门店券实例")
    @GetMapping("/coupon-instances")
    public Result<Map<String, Object>> couponInstances(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        return Result.ok(storeAdminService.couponInstances(AdminContext.requireStoreId(), status, p, s));
    }

    @Operation(summary = "核销券（可指定归因员工）")
    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        String code = body.get("code") == null ? "" : String.valueOf(body.get("code"));
        Long staffId = body.get("staffId") instanceof Number n ? n.longValue() : null;
        return Result.ok(storeAdminService.verify(
                AdminContext.requireStoreId(), code, AdminContext.adminId(), staffId));
    }
}
