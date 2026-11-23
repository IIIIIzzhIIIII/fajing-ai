package com.fajing.config;

import com.fajing.interceptor.AdminInterceptor;
import com.fajing.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/login/mock", "/api/auth/login/guest",
                        "/api/auth/logout", "/api/admin/**",
                        // 前台文案与套餐档位属于公开只读配置，登录前也要能拿到
                        "/api/site-settings", "/api/points/packages",
                        // 支付渠道服务端回调不带用户 token，需放行（回调内部自行验签）
                        "/api/order/notify", "/api/order/notify/**", "/api/pay/**");
    }
}
