package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.AuthService;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 小程序：wx.login 的 code 换本系统 token */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        return Result.ok(authService.loginByCode(code));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me(UserContext.require()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
