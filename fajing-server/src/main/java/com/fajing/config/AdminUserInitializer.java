package com.fajing.config;

import com.fajing.entity.AdminUser;
import com.fajing.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 首次启动初始化管理员：账号 admin，密码来自环境变量 ADMIN_INIT_PASSWORD，BCrypt 入库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;

    @Value("${admin.init-password:}")
    private String initPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = adminUserMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            if (initPassword == null || initPassword.isBlank()) {
                log.warn("admin_user 为空且未配置 ADMIN_INIT_PASSWORD，跳过管理员初始化");
                return;
            }
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            admin.setPasswordHash(new BCryptPasswordEncoder().encode(initPassword));
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setCreatedAt(LocalDateTime.now());
            adminUserMapper.insert(admin);
            log.info("已初始化管理员账号 admin（密码来自 ADMIN_INIT_PASSWORD）");
        } catch (Exception e) {
            log.warn("管理员初始化失败：{}", e.getMessage());
        }
    }
}
