package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.CouponTier;
import com.fajing.entity.Staff;
import com.fajing.entity.UserCoupon;
import com.fajing.mapper.CouponTierMapper;
import com.fajing.mapper.StaffMapper;
import com.fajing.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 店长后台：门店、券档位、员工、订单、核销、业绩 */
@Service
@RequiredArgsConstructor
public class StoreAdminService {

    private final JdbcTemplate jdbc;
    private final CouponTierMapper tierMapper;
    private final StaffMapper staffMapper;
    private final UserCouponMapper couponMapper;
    private final CouponService couponService;
    private final CommissionService commissionService;

    public Map<String, Object> profile(Long storeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.id, s.tenant_id AS tenantId, s.name, s.brand_name AS brandName, s.address, s.phone, "
                        + "s.business_hours AS businessHours, s.daily_capacity AS dailyCapacity, "
                        + "s.commission_rate AS commissionRate, s.kyc_status AS kycStatus, s.status, "
                        + "s.created_at AS createdAt, t.name AS tenantName "
                        + "FROM store s JOIN tenant t ON t.id = s.tenant_id WHERE s.id = ?", storeId);
        if (rows.isEmpty()) {
            throw new BizException("门店不存在");
        }
        return rows.get(0);
    }

    public Map<String, Object> updateProfile(Long storeId, Map<String, Object> body) {
        Map<String, Object> current = profile(storeId);
        String name = str(body.get("name"), str(current.get("name"), ""));
        String brandName = str(body.get("brandName"), str(current.get("brandName"), ""));
        String address = str(body.get("address"), str(current.get("address"), ""));
        String phone = str(body.get("phone"), str(current.get("phone"), ""));
        String businessHours = str(body.get("businessHours"), str(current.get("businessHours"), ""));
        int capacity = intOf(body.get("dailyCapacity"), intOf(current.get("dailyCapacity"), 20));
        if (name.isBlank()) {
            throw new BizException("门店名称不能为空");
        }
        if (capacity < 0 || capacity > 9999) {
            throw new BizException("每日接待量需在 0-9999 之间");
        }
        jdbc.update("UPDATE store SET name=?, brand_name=?, address=?, phone=?, business_hours=?, daily_capacity=? WHERE id=?",
                trim(name, 64), trim(brandName, 64), trim(address, 255), trim(phone, 20),
                trim(businessHours, 64), capacity, storeId);
        return profile(storeId);
    }

    public Map<String, Object> dashboard(Long storeId, int days) {
        int rangeDays = days <= 0 ? 7 : Math.min(days, 90);
        LocalDate start = LocalDate.now().minusDays(rangeDays - 1L);
        LocalDateTime startAt = start.atStartOfDay();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayRevenue", amount("SELECT IFNULL(SUM(amount),0) FROM orders "
                + "WHERE store_id=? AND biz_type='coupon' AND status='paid' AND paid_at>=CURDATE()", storeId));
        data.put("todayOrders", count("SELECT COUNT(*) FROM orders "
                + "WHERE store_id=? AND biz_type='coupon' AND status='paid' AND paid_at>=CURDATE()", storeId));
        data.put("todayVerified", count("SELECT COUNT(*) FROM user_coupon "
                + "WHERE store_id=? AND status='used' AND used_at>=CURDATE()", storeId));
        data.put("periodRevenue", amount("SELECT IFNULL(SUM(amount),0) FROM orders "
                + "WHERE store_id=? AND biz_type='coupon' AND status='paid' AND paid_at>=?", storeId, startAt));
        data.put("periodOrders", count("SELECT COUNT(*) FROM orders "
                + "WHERE store_id=? AND biz_type='coupon' AND status='paid' AND paid_at>=?", storeId, startAt));
        data.put("periodVerified", count("SELECT COUNT(*) FROM user_coupon "
                + "WHERE store_id=? AND status='used' AND used_at>=?", storeId, startAt));
        data.put("periodAiCalls", count("SELECT COUNT(*) FROM ai_call_log "
                + "WHERE store_id=? AND created_at>=?", storeId, startAt));
        data.put("commissionTotal", amount("SELECT IFNULL(SUM(commission_amount),0) FROM commission_ledger "
                + "WHERE store_id=? AND status<>'reversed'", storeId));
        data.put("commissionPending", amount("SELECT IFNULL(SUM(commission_amount),0) FROM commission_ledger "
                + "WHERE store_id=? AND status='available'", storeId));
        data.put("aiCallsToday", count("SELECT COUNT(*) FROM ai_call_log "
                + "WHERE store_id=? AND created_at>=CURDATE()", storeId));
        data.put("staffCount", count("SELECT COUNT(*) FROM staff WHERE store_id=? AND status=1", storeId));
        data.put("couponUnused", count("SELECT COUNT(*) FROM user_coupon "
                + "WHERE store_id=? AND status='unused'", storeId));
        data.put("rangeDays", rangeDays);
        data.put("rangeStart", start.toString());
        data.put("rangeEnd", LocalDate.now().toString());

        Map<String, BigDecimal> revenueByDay = dailyAmount(
                "SELECT DATE(paid_at) d, IFNULL(SUM(amount),0) v FROM orders "
                        + "WHERE store_id=? AND biz_type='coupon' AND status='paid' AND paid_at>=? "
                        + "GROUP BY DATE(paid_at)", storeId, startAt);
        Map<String, BigDecimal> ordersByDay = dailyAmount(
                "SELECT DATE(paid_at) d, COUNT(*) v FROM orders "
                        + "WHERE store_id=? AND biz_type='coupon' AND status='paid' AND paid_at>=? "
                        + "GROUP BY DATE(paid_at)", storeId, startAt);
        Map<String, BigDecimal> verifiedByDay = dailyAmount(
                "SELECT DATE(used_at) d, COUNT(*) v FROM user_coupon "
                        + "WHERE store_id=? AND status='used' AND used_at>=? GROUP BY DATE(used_at)", storeId, startAt);
        Map<String, BigDecimal> aiByDay = dailyAmount(
                "SELECT DATE(created_at) d, COUNT(*) v FROM ai_call_log "
                        + "WHERE store_id=? AND created_at>=? GROUP BY DATE(created_at)", storeId, startAt);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            String key = date.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key.substring(5));
            row.put("revenue", revenueByDay.getOrDefault(key, BigDecimal.ZERO));
            row.put("orders", ordersByDay.getOrDefault(key, BigDecimal.ZERO).intValue());
            row.put("verified", verifiedByDay.getOrDefault(key, BigDecimal.ZERO).intValue());
            row.put("aiCalls", aiByDay.getOrDefault(key, BigDecimal.ZERO).intValue());
            trend.add(row);
        }
        data.put("trend", trend);
        data.put("staffRanking", jdbc.queryForList(
                "SELECT s.id, s.name, "
                        + "(SELECT COUNT(*) FROM orders o WHERE o.staff_id=s.id AND o.biz_type='coupon' "
                        + "AND o.status='paid' AND o.paid_at>=?) AS orderCount, "
                        + "IFNULL((SELECT SUM(o.amount) FROM orders o WHERE o.staff_id=s.id AND o.biz_type='coupon' "
                        + "AND o.status='paid' AND o.paid_at>=?),0) AS orderAmount, "
                        + "(SELECT COUNT(*) FROM user_coupon c WHERE c.staff_id=s.id AND c.status='used' "
                        + "AND c.used_at>=?) AS verifiedCount, "
                        + "IFNULL((SELECT SUM(c.commission_amount) FROM user_coupon c WHERE c.staff_id=s.id "
                        + "AND c.status='used' AND c.used_at>=?),0) AS commissionAmount "
                        + "FROM staff s WHERE s.store_id=? AND s.status=1 "
                        + "ORDER BY orderAmount DESC, verifiedCount DESC, s.id LIMIT 10",
                startAt, startAt, startAt, startAt, storeId));
        data.put("couponStatus", jdbc.queryForList(
                "SELECT status, COUNT(*) AS count FROM user_coupon WHERE store_id=? GROUP BY status", storeId));
        return data;
    }

    public List<CouponTier> couponTiers(Long storeId) {
        return tierMapper.selectList(new QueryWrapper<CouponTier>()
                .eq("store_id", storeId)
                .orderByAsc("sort").orderByAsc("id"));
    }

    public CouponTier createCouponTier(Long storeId, CouponTier req) {
        validateTier(req);
        req.setId(null);
        req.setStoreId(storeId);
        req.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        req.setSort(req.getSort() == null ? 0 : req.getSort());
        tierMapper.insert(req);
        return tierMapper.selectById(req.getId());
    }

    public CouponTier updateCouponTier(Long storeId, CouponTier req) {
        if (req.getId() == null) {
            throw new BizException("档位 ID 不能为空");
        }
        CouponTier existed = tierMapper.selectById(req.getId());
        if (existed == null || !storeId.equals(existed.getStoreId())) {
            throw new BizException("档位不存在或不属于当前门店");
        }
        validateTier(req);
        req.setStoreId(storeId);
        tierMapper.updateById(req);
        return tierMapper.selectById(req.getId());
    }

    public void disableCouponTier(Long storeId, Long id) {
        CouponTier tier = tierMapper.selectById(id);
        if (tier == null || !storeId.equals(tier.getStoreId())) {
            throw new BizException("档位不存在或不属于当前门店");
        }
        tier.setStatus(0);
        tierMapper.updateById(tier);
    }

    public List<Map<String, Object>> staffList(Long storeId) {
        return jdbc.queryForList(
                "SELECT s.id, s.name, s.phone, s.role, s.invite_code AS inviteCode, s.staff_code AS staffCode, "
                        + "s.daily_limit AS dailyLimit, s.status, s.user_id AS userId, s.created_at AS createdAt, "
                        + "IFNULL((SELECT SUM(q.used) FROM staff_quota q WHERE q.staff_id=s.id AND q.quota_date=CURDATE()),0) AS todayUsed, "
                        + "(SELECT COUNT(*) FROM orders o WHERE o.staff_id=s.id AND o.biz_type='coupon' AND o.status='paid') AS orderCount, "
                        + "IFNULL((SELECT SUM(o.amount) FROM orders o WHERE o.staff_id=s.id AND o.biz_type='coupon' AND o.status='paid'),0) AS orderAmount, "
                        + "(SELECT COUNT(*) FROM user_coupon c WHERE c.staff_id=s.id AND c.status='used') AS verifiedCount, "
                        + "IFNULL((SELECT SUM(c.commission_amount) FROM user_coupon c WHERE c.staff_id=s.id AND c.status='used'),0) AS commissionAmount "
                        + "FROM staff s WHERE s.store_id=? ORDER BY s.id DESC", storeId);
    }

    public Map<String, Object> createStaff(Long storeId, Long tenantId, Map<String, Object> body) {
        String name = str(body.get("name"), "").trim();
        String phone = str(body.get("phone"), "").trim();
        int dailyLimit = intOf(body.get("dailyLimit"), 20);
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("请输入正确的手机号");
        }
        if (name.isBlank()) {
            name = "员工" + phone.substring(phone.length() - 4);
        }
        if (dailyLimit < 1 || dailyLimit > 200) {
            throw new BizException("每日额度需在 1-200 之间");
        }
        Staff staff = new Staff();
        staff.setTenantId(tenantId);
        staff.setStoreId(storeId);
        staff.setUserId(0L);
        staff.setName(trim(name, 32));
        staff.setPhone(phone);
        staff.setRole("EMPLOYEE");
        staff.setInviteCode(uniqueCode("invite_code"));
        staff.setStaffCode(uniqueCode("staff_code"));
        staff.setDailyLimit(dailyLimit);
        staff.setStatus(1);
        staff.setCreatedAt(LocalDateTime.now());
        staffMapper.insert(staff);
        return staffMap(staff.getId());
    }

    public Map<String, Object> updateStaff(Long storeId, Long id, Map<String, Object> body) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null || !storeId.equals(staff.getStoreId())) {
            throw new BizException("员工不存在或不属于当前门店");
        }
        if (body.containsKey("name")) {
            String name = str(body.get("name"), "").trim();
            if (name.isBlank()) {
                throw new BizException("员工姓名不能为空");
            }
            staff.setName(trim(name, 32));
        }
        if (body.containsKey("phone")) {
            String phone = str(body.get("phone"), "").trim();
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                throw new BizException("请输入正确的手机号");
            }
            staff.setPhone(phone);
        }
        if (body.containsKey("dailyLimit")) {
            int limit = intOf(body.get("dailyLimit"), staff.getDailyLimit());
            if (limit < 1 || limit > 200) {
                throw new BizException("每日额度需在 1-200 之间");
            }
            staff.setDailyLimit(limit);
        }
        if (body.containsKey("status")) {
            int status = intOf(body.get("status"), staff.getStatus());
            staff.setStatus(status == 0 ? 0 : 1);
        }
        staffMapper.updateById(staff);
        return staffMap(id);
    }

    /** 删除员工：同时清理额度与顾客归属，员工身份立即失效 */
    @Transactional
    public void deleteStaff(Long storeId, Long id) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null || !storeId.equals(staff.getStoreId())) {
            throw new BizException("员工不存在或不属于当前门店");
        }
        staffMapper.deleteById(id);
        jdbc.update("DELETE FROM staff_quota WHERE staff_id=?", id);
        jdbc.update("DELETE FROM staff_customer_binding WHERE staff_id=?", id);
    }

    public Map<String, Object> orders(Long storeId, String status, int page, int size) {
        boolean hasStatus = status != null && !status.isBlank();
        String from = "FROM orders o LEFT JOIN app_user u ON u.id=o.user_id "
                + "LEFT JOIN staff s ON s.id=o.staff_id "
                + "LEFT JOIN user_coupon c ON c.order_id=o.id "
                + "WHERE o.store_id=? AND o.biz_type='coupon' "
                + (hasStatus ? "AND o.status=? " : "");
        long total = hasStatus
                ? count("SELECT COUNT(*) " + from, storeId, status)
                : count("SELECT COUNT(*) " + from, storeId);
        String sql = "SELECT o.id, o.out_trade_no AS outTradeNo, o.amount, o.status AS orderStatus, "
                + "o.pay_channel AS payChannel, o.created_at AS createdAt, o.paid_at AS paidAt, "
                + "o.refund_status AS refundStatus, o.store_id AS storeId, o.staff_id AS staffId, "
                + "u.nickname AS customerName, s.name AS staffName, c.code AS couponCode, c.status AS couponStatus "
                + from + "ORDER BY o.id DESC LIMIT ?,?";
        List<Map<String, Object>> rows = hasStatus
                ? jdbc.queryForList(sql, storeId, status, (page - 1) * size, size)
                : jdbc.queryForList(sql, storeId, (page - 1) * size, size);
        return pageData(rows, total, page, size);
    }

    public Map<String, Object> couponInstances(Long storeId, String status, int page, int size) {
        boolean hasStatus = status != null && !status.isBlank();
        String from = "FROM user_coupon c LEFT JOIN app_user u ON u.id=c.user_id "
                + "LEFT JOIN staff s ON s.id=c.staff_id "
                + "WHERE c.store_id=? "
                + (hasStatus ? "AND c.status=? " : "");
        long total = hasStatus
                ? count("SELECT COUNT(*) " + from, storeId, status)
                : count("SELECT COUNT(*) " + from, storeId);
        String sql = "SELECT c.id, c.code, c.title, c.pay_amount AS payAmount, c.value_amount AS valueAmount, "
                + "c.commission_amount AS commissionAmount, c.settle_status AS settleStatus, c.status, "
                + "c.created_at AS createdAt, c.used_at AS usedAt, c.expire_at AS expireAt, "
                + "u.nickname AS customerName, s.name AS staffName "
                + from + "ORDER BY c.id DESC LIMIT ?,?";
        List<Map<String, Object>> rows = hasStatus
                ? jdbc.queryForList(sql, storeId, status, (page - 1) * size, size)
                : jdbc.queryForList(sql, storeId, (page - 1) * size, size);
        return pageData(rows, total, page, size);
    }

    private Map<String, Object> pageData(List<Map<String, Object>> rows, long total, int page, int size) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    @Transactional
    public Map<String, Object> verify(Long storeId, String code, Long adminId, Long staffId) {
        if (code == null || code.isBlank()) {
            throw new BizException("请输入核销码");
        }
        Long attributed = 0L;
        if (staffId != null && staffId > 0) {
            Staff staff = staffMapper.selectById(staffId);
            if (staff == null || !storeId.equals(staff.getStoreId())
                    || staff.getStatus() == null || staff.getStatus() != 1) {
                throw new BizException("归因员工不存在或已停用");
            }
            attributed = staffId;
        }
        UserCoupon coupon = couponMapper.selectOne(new QueryWrapper<UserCoupon>()
                .eq("code", code.trim()).last("limit 1"));
        if (coupon == null) {
            throw new BizException("核销码不存在");
        }
        if (!storeId.equals(coupon.getStoreId())) {
            throw new BizException("该券不属于本店，无法核销");
        }
        if (!"unused".equals(coupon.getStatus())) {
            throw new BizException("该券已使用、已退款或已过期");
        }
        if (coupon.getExpireAt() != null && coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            coupon.setStatus("expired");
            couponMapper.updateById(coupon);
            throw new BizException("该券已过期");
        }
        UpdateWrapper<UserCoupon> update = new UpdateWrapper<UserCoupon>()
                .eq("id", coupon.getId())
                .eq("store_id", storeId)
                .eq("status", "unused")
                .set("status", "used")
                .set("used_at", LocalDateTime.now())
                .set("verified_by", adminId == null ? 0L : adminId);
        if (attributed > 0) {
            update.set("staff_id", attributed);
        }
        int rows = couponMapper.update(null, update);
        if (rows == 0) {
            throw new BizException("券状态已变化，请刷新后重试");
        }
        coupon.setStatus("used");
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setVerifiedBy(adminId);
        if (attributed > 0) {
            coupon.setStaffId(attributed);
            jdbc.update("UPDATE orders SET staff_id=? WHERE id=? AND store_id=?",
                    attributed, coupon.getOrderId(), storeId);
        }
        commissionService.settle(coupon.getOrderId());
        coupon.setSettleStatus("settled");
        Map<String, Object> data = couponService.toMap(coupon);
        if (attributed > 0) {
            Staff staff = staffMapper.selectById(attributed);
            data.put("staffName", staff == null ? "" : staff.getName());
        }
        return data;
    }

    private Map<String, Object> staffMap(Long id) {
        for (Map<String, Object> row : staffListById(id)) {
            return row;
        }
        throw new BizException("员工不存在");
    }

    private List<Map<String, Object>> staffListById(Long id) {
        return jdbc.queryForList(
                "SELECT s.id, s.name, s.phone, s.role, s.invite_code AS inviteCode, s.staff_code AS staffCode, "
                        + "s.daily_limit AS dailyLimit, s.status, s.user_id AS userId, s.created_at AS createdAt, "
                        + "IFNULL((SELECT SUM(q.used) FROM staff_quota q WHERE q.staff_id=s.id AND q.quota_date=CURDATE()),0) AS todayUsed "
                        + "FROM staff s WHERE s.id=?", id);
    }

    private void validateTier(CouponTier req) {
        if (req.getPayAmount() == null || req.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("支付金额必须大于 0");
        }
        if (req.getValueAmount() == null || req.getValueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("抵扣金额必须大于 0");
        }
        req.setTitle(req.getTitle() == null ? "" : req.getTitle().trim());
        if (req.getTitle().isBlank()) {
            throw new BizException("券名称不能为空");
        }
        req.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
    }

    private String uniqueCode(String column) {
        for (int i = 0; i < 30; i++) {
            String code = randomCode(6);
            Long count = "invite_code".equals(column)
                    ? staffMapper.selectCount(new QueryWrapper<Staff>().eq("invite_code", code))
                    : staffMapper.selectCount(new QueryWrapper<Staff>().eq("staff_code", code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BizException("员工码生成失败，请重试");
    }

    private static String randomCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private BigDecimal amount(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 按日期聚合，key 为 yyyy-MM-dd */
    private Map<String, BigDecimal> dailyAmount(String sql, Object... args) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            Object date = row.get("d");
            Object value = row.get("v");
            if (date == null) {
                continue;
            }
            BigDecimal amount = value instanceof BigDecimal decimal ? decimal
                    : value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
            result.put(String.valueOf(date), amount);
        }
        return result;
    }

    private static String str(Object value, String def) {
        if (value == null) {
            return def;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? def : s;
    }

    private static int intOf(Object value, int def) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
