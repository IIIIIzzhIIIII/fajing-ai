package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.AdminUser;
import com.fajing.entity.Store;
import com.fajing.entity.Tenant;
import com.fajing.mapper.AdminUserMapper;
import com.fajing.mapper.StoreMapper;
import com.fajing.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 平台总后台：租户/门店开通、KYC、佣金规则、平台统计 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final JdbcTemplate jdbc;
    private final TenantMapper tenantMapper;
    private final StoreMapper storeMapper;
    private final AdminUserMapper adminUserMapper;

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantCount", count("SELECT COUNT(*) FROM tenant"));
        data.put("storeCount", count("SELECT COUNT(*) FROM store"));
        data.put("approvedStoreCount", count("SELECT COUNT(*) FROM store WHERE kyc_status='approved' AND status=1"));
        data.put("paidOrders", count("SELECT COUNT(*) FROM orders "
                + "WHERE biz_type='coupon' AND status='paid' AND store_id>0"));
        data.put("gmv", amount("SELECT IFNULL(SUM(amount),0) FROM orders "
                + "WHERE biz_type='coupon' AND status='paid' AND store_id>0"));
        data.put("commissionTotal", amount("SELECT IFNULL(SUM(commission_amount),0) FROM commission_ledger WHERE status<>'reversed'"));
        data.put("commissionPending", amount("SELECT IFNULL(SUM(commission_amount),0) FROM commission_ledger WHERE status='available'"));
        data.put("aiCallsToday", count("SELECT COUNT(*) FROM ai_call_log WHERE created_at>=CURDATE()"));
        data.put("aiCostToday", amount("SELECT IFNULL(SUM(cost_cents),0)/100.0 FROM ai_call_log WHERE created_at>=CURDATE()"));
        data.put("employeeCount", count("SELECT COUNT(*) FROM staff WHERE status=1"));
        data.put("recentTenants", jdbc.queryForList(
                "SELECT id, name, contact_name AS contactName, contact_phone AS contactPhone, status, "
                        + "created_at AS createdAt FROM tenant ORDER BY id DESC LIMIT 8"));
        List<Map<String, Object>> recentStores = stores();
        data.put("recentStores", recentStores.subList(0, Math.min(8, recentStores.size())));
        return data;
    }

    public List<Map<String, Object>> tenants() {
        List<Map<String, Object>> tenants = jdbc.queryForList(
                "SELECT id, name, contact_name AS contactName, contact_phone AS contactPhone, status, "
                        + "created_at AS createdAt FROM tenant ORDER BY id DESC");
        for (Map<String, Object> tenant : tenants) {
            Object id = tenant.get("id");
            tenant.put("stores", jdbc.queryForList(
                    "SELECT id, name, address, kyc_status AS kycStatus, status, commission_rate AS commissionRate "
                            + "FROM store WHERE tenant_id=? ORDER BY id", id));
            List<Map<String, Object>> managers = jdbc.queryForList(
                    "SELECT username, display_name AS displayName, status FROM admin_user "
                            + "WHERE tenant_id=? AND role='STORE_MANAGER' ORDER BY id", id);
            tenant.put("manager", managers.isEmpty() ? null : managers.get(0));
            tenant.put("storeCount", count("SELECT COUNT(*) FROM store WHERE tenant_id=?", id));
            tenant.put("orderCount", count("SELECT COUNT(*) FROM orders o JOIN store s ON s.id=o.store_id "
                    + "WHERE s.tenant_id=? AND o.biz_type='coupon' AND o.status='paid'", id));
            tenant.put("gmv", amount("SELECT IFNULL(SUM(o.amount),0) FROM orders o JOIN store s ON s.id=o.store_id "
                    + "WHERE s.tenant_id=? AND o.biz_type='coupon' AND o.status='paid'", id));
            tenant.put("commission", amount("SELECT IFNULL(SUM(cl.commission_amount),0) FROM commission_ledger cl "
                    + "JOIN store s ON s.id=cl.store_id WHERE s.tenant_id=? AND cl.status<>'reversed'", id));
        }
        return tenants;
    }

    @Transactional
    public Map<String, Object> createTenant(Map<String, Object> body) {
        String tenantName = str(body.get("tenantName"), "").trim();
        String contactName = str(body.get("contactName"), "").trim();
        String contactPhone = str(body.get("contactPhone"), "").trim();
        String storeName = str(body.get("storeName"), "").trim();
        String storeAddress = str(body.get("storeAddress"), "").trim();
        String storePhone = str(body.get("storePhone"), "").trim();
        String businessHours = str(body.get("businessHours"), "10:00-21:00").trim();
        int dailyCapacity = intOf(body.get("dailyCapacity"), 20);
        BigDecimal commissionRate = decimalOf(body.get("commissionRate"), new BigDecimal("10.00"));
        String username = str(body.get("managerUsername"), "").trim();
        String password = str(body.get("managerPassword"), "").trim();
        String managerName = str(body.get("managerName"), "店长").trim();

        if (tenantName.isBlank()) {
            throw new BizException("请输入商户名称");
        }
        if (storeName.isBlank()) {
            throw new BizException("请输入门店名称");
        }
        if (!username.matches("^[A-Za-z0-9_]{4,32}$")) {
            throw new BizException("店长账号需为 4-32 位字母、数字或下划线");
        }
        if (password.length() < 6) {
            throw new BizException("店长密码至少 6 位");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) < 0
                || commissionRate.compareTo(new BigDecimal("50")) > 0) {
            throw new BizException("佣金比例需在 0-50% 之间");
        }
        if (dailyCapacity < 0 || dailyCapacity > 9999) {
            throw new BizException("每日接待量需在 0-9999 之间");
        }
        Long usernameExists = adminUserMapper.selectCount(new QueryWrapper<AdminUser>()
                .eq("username", username));
        if (usernameExists != null && usernameExists > 0) {
            throw new BizException("店长账号已存在");
        }

        Tenant tenant = new Tenant();
        tenant.setName(trim(tenantName, 64));
        tenant.setContactName(trim(contactName, 32));
        tenant.setContactPhone(trim(contactPhone, 20));
        tenant.setStatus(1);
        tenant.setCreatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);

        Store store = new Store();
        store.setTenantId(tenant.getId());
        store.setName(trim(storeName, 64));
        store.setBrandName(trim(tenantName, 64));
        store.setAddress(trim(storeAddress, 255));
        store.setPhone(trim(storePhone, 20));
        store.setBusinessHours(trim(businessHours, 64));
        store.setDailyCapacity(dailyCapacity);
        store.setCommissionRate(commissionRate);
        store.setKycStatus("pending");
        store.setStatus(1);
        store.setCreatedAt(LocalDateTime.now());
        storeMapper.insert(store);

        AdminUser manager = new AdminUser();
        manager.setUsername(username);
        manager.setPasswordHash(new BCryptPasswordEncoder().encode(password));
        manager.setRole("STORE_MANAGER");
        manager.setTenantId(tenant.getId());
        manager.setStoreId(store.getId());
        manager.setDisplayName(trim(managerName, 32));
        manager.setStatus(1);
        manager.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(manager);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenant.getId());
        data.put("storeId", store.getId());
        data.put("managerUsername", username);
        data.put("managerPassword", password);
        return data;
    }

    public List<Map<String, Object>> stores() {
        return jdbc.queryForList(
                "SELECT s.id, s.tenant_id AS tenantId, t.name AS tenantName, s.name, s.address, s.phone, "
                        + "s.business_hours AS businessHours, s.daily_capacity AS dailyCapacity, "
                        + "s.kyc_status AS kycStatus, s.status, s.commission_rate AS commissionRate, "
                        + "s.created_at AS createdAt, "
                        + "(SELECT COUNT(*) FROM staff st WHERE st.store_id=s.id AND st.status=1) AS staffCount, "
                        + "(SELECT COUNT(*) FROM orders o WHERE o.store_id=s.id AND o.biz_type='coupon' AND o.status='paid') AS orderCount, "
                        + "IFNULL((SELECT SUM(o.amount) FROM orders o WHERE o.store_id=s.id AND o.biz_type='coupon' AND o.status='paid'),0) AS gmv, "
                        + "IFNULL((SELECT SUM(cl.commission_amount) FROM commission_ledger cl WHERE cl.store_id=s.id AND cl.status<>'reversed'),0) AS commission, "
                        + "(SELECT COUNT(*) FROM ai_call_log a WHERE a.store_id=s.id) AS aiCalls "
                        + "FROM store s JOIN tenant t ON t.id=s.tenant_id ORDER BY s.id DESC");
    }

    public void updateKyc(Long storeId, String status) {
        if (!"pending".equals(status) && !"approved".equals(status) && !"rejected".equals(status)) {
            throw new BizException("KYC 状态不合法");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException("门店不存在");
        }
        store.setKycStatus(status);
        storeMapper.updateById(store);
    }

    public void updateCommissionRate(Long storeId, BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(new BigDecimal("50")) > 0) {
            throw new BizException("佣金比例需在 0-50% 之间");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException("门店不存在");
        }
        store.setCommissionRate(rate);
        storeMapper.updateById(store);
    }

    public void updateTenantStatus(Long tenantId, int status) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("商户不存在");
        }
        int next = status == 0 ? 0 : 1;
        tenant.setStatus(next);
        tenantMapper.updateById(tenant);
        storeMapper.update(null, new UpdateWrapper<Store>()
                .eq("tenant_id", tenantId)
                .set("status", next));
        adminUserMapper.update(null, new UpdateWrapper<AdminUser>()
                .eq("tenant_id", tenantId)
                .eq("role", "STORE_MANAGER")
                .set("status", next));
    }

    public Map<String, Object> commissions(String status, int page, int size) {
        boolean hasStatus = status != null && !status.isBlank();
        String from = "FROM commission_ledger cl "
                + "JOIN store s ON s.id=cl.store_id JOIN tenant t ON t.id=cl.tenant_id "
                + "LEFT JOIN staff st ON st.id=cl.staff_id LEFT JOIN orders o ON o.id=cl.order_id ";
        String where = hasStatus ? "WHERE cl.status=? " : "";
        long total = hasStatus
                ? count("SELECT COUNT(*) " + from + where, status)
                : count("SELECT COUNT(*) " + from + where);
        String sql = "SELECT cl.id, cl.order_id AS orderId, cl.order_amount AS orderAmount, "
                + "cl.commission_rate AS commissionRate, cl.commission_amount AS commissionAmount, "
                + "cl.status, cl.created_at AS createdAt, cl.settled_at AS settledAt, cl.reversed_at AS reversedAt, "
                + "s.name AS storeName, t.name AS tenantName, st.name AS staffName, o.out_trade_no AS outTradeNo "
                + from + where + "ORDER BY cl.id DESC LIMIT ?,?";
        List<Map<String, Object>> rows = hasStatus
                ? jdbc.queryForList(sql, status, (page - 1) * size, size)
                : jdbc.queryForList(sql, (page - 1) * size, size);
        return pageData(rows, total, page, size);
    }

    public Map<String, Object> platformOrders(int page, int size) {
        String from = "FROM orders o JOIN store s ON s.id=o.store_id JOIN tenant t ON t.id=s.tenant_id "
                + "LEFT JOIN staff st ON st.id=o.staff_id LEFT JOIN user_coupon c ON c.order_id=o.id "
                + "WHERE o.biz_type='coupon' ";
        long total = count("SELECT COUNT(*) " + from);
        String sql = "SELECT o.id, o.out_trade_no AS outTradeNo, o.amount, o.status AS orderStatus, "
                + "o.pay_channel AS payChannel, o.created_at AS createdAt, o.paid_at AS paidAt, "
                + "o.refund_status AS refundStatus, s.name AS storeName, t.name AS tenantName, "
                + "st.name AS staffName, c.code AS couponCode, c.status AS couponStatus "
                + from + "ORDER BY o.id DESC LIMIT ?,?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, (page - 1) * size, size);
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

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private BigDecimal amount(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
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

    private static BigDecimal decimalOf(Object value, BigDecimal def) {
        if (value == null || String.valueOf(value).isBlank()) {
            return def;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
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
