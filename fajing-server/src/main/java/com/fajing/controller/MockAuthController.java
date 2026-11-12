package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本地/H5 演示登录，仅 auth.mock-enabled=true 时存在；生产环境该路由直接 404
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.mock-enabled", havingValue = "true", matchIfMissing = false)
public class MockAuthController {

    private final AuthService authService;

    @PostMapping("/login/mock")
    public Result<Map<String, Object>> mockLogin() {
        return Result.ok(authService.mockLogin());
    }
}
