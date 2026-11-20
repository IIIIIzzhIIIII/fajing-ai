package com.fajing.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.AdminUser;
import com.fajing.entity.Store;
import com.fajing.entity.Tenant;
import com.fajing.mapper.AdminUserMapper;
import com.fajing.mapper.StoreMapper;
import com.fajing.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 首次启动初始化管理员：
 * 1. 平台总管理员 admin（密码来自 ADMIN_INIT_PASSWORD）
 * 2. 演示店长 store1（密码来自 DEMO_STORE_PASSWORD，可通过 DEMO_STORE_ENABLED=false 关闭）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;
    private final TenantMapper tenantMapper;
    private final StoreMapper storeMapper;

    @Value("${admin.init-password:}")
    private String initPassword;

    @Value("${demo.store-enabled:true}")
    private boolean demoStoreEnabled;

    @Value("${demo.store-username:store1}")
    private String demoStoreUsername;

    @Value("${demo.store-password:store123}")
    private String demoStorePassword;

    @Override
    public void run(ApplicationArguments args) {
        seedPlatformAdmin();
        seedDemoStoreManager();
    }

    private void seedPlatformAdmin() {
        try {
            Long exists = adminUserMapper.selectCount(new QueryWrapper<AdminUser>()
                    .eq("username", "admin"));
            if (exists != null && exists > 0) {
                return;
            }
            if (initPassword == null || initPassword.isBlank()) {
                log.warn("admin_user 为空且未配置 ADMIN_INIT_PASSWORD，跳过平台管理员初始化");
                return;
            }
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            admin.setPasswordHash(new BCryptPasswordEncoder().encode(initPassword));
            admin.setRole("PLATFORM_ADMIN");
            admin.setDisplayName("平台管理员");
            admin.setTenantId(0L);
            admin.setStoreId(0L);
            admin.setStatus(1);
            admin.setCreatedAt(LocalDateTime.now());
            adminUserMapper.insert(admin);
            log.info("已初始化平台管理员账号 admin");
        } catch (Exception e) {
            log.warn("平台管理员初始化失败：{}", e.getMessage());
        }
    }

    private void seedDemoStoreManager() {
        if (!demoStoreEnabled) {
            return;
        }
        try {
            String username = demoStoreUsername == null || demoStoreUsername.isBlank()
                    ? "store1" : demoStoreUsername.trim();
            Long exists = adminUserMapper.selectCount(new QueryWrapper<AdminUser>()
                    .eq("username", username));
            if (exists != null && exists > 0) {
                return;
            }
            Tenant tenant = tenantMapper.selectById(1L);
            if (tenant == null) {
                tenant = new Tenant();
                tenant.setName("发镜示范商户");
                tenant.setContactName("张店长");
                tenant.setContactPhone("13800000001");
                tenant.setStatus(1);
                tenant.setCreatedAt(LocalDateTime.now());
                tenantMapper.insert(tenant);
            }
            Store store = storeMapper.selectById(1L);
            if (store == null) {
                store = new Store();
                store.setTenantId(tenant.getId());
                store.setName("发镜示范店");
                store.setBrandName("发镜 AI 示范店");
                store.setAddress("上海市静安区示例路 88 号");
                store.setPhone("021-88886666");
                store.setBusinessHours("10:00-21:00");
                store.setDailyCapacity(20);
                store.setCommissionRate(new BigDecimal("10.00"));
                store.setKycStatus("approved");
                store.setStatus(1);
                store.setCreatedAt(LocalDateTime.now());
                storeMapper.insert(store);
            }
            AdminUser manager = new AdminUser();
            manager.setUsername(username);
            manager.setPasswordHash(new BCryptPasswordEncoder().encode(
                    demoStorePassword == null || demoStorePassword.isBlank() ? "store123" : demoStorePassword));
            manager.setRole("STORE_MANAGER");
            manager.setDisplayName("示范店长");
            manager.setTenantId(tenant.getId());
            manager.setStoreId(store.getId());
            manager.setStatus(1);
            manager.setCreatedAt(LocalDateTime.now());
            adminUserMapper.insert(manager);
            log.info("已初始化演示店长账号 {}（租户 {} / 门店 {}）", username, tenant.getId(), store.getId());
        } catch (Exception e) {
            log.warn("演示店长初始化失败：{}", e.getMessage());
        }
    }
}
