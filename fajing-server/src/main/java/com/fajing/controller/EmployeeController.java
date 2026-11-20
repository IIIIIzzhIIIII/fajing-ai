package com.fajing.controller;

import com.fajing.common.Result;
import com.fajing.service.EmployeeService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 员工工作台：绑定员工身份、免费代客试发、顾客领取结果 */
@Tag(name = "员工工作台")
@RestController
@RequestMapping("/api/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "员工身份信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(employeeService.me(UserContext.require()));
    }

    @Operation(summary = "员工绑定（邀请码 + 手机号）")
    @PostMapping("/bind")
    public Result<Map<String, Object>> bind(@RequestBody Map<String, String> body) {
        String inviteCode = body == null ? null : body.get("inviteCode");
        String phone = body == null ? null : body.get("phone");
        return Result.ok(employeeService.bind(UserContext.require(), inviteCode, phone));
    }

    @Operation(summary = "员工免费试发")
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        return Result.ok(employeeService.generate(UserContext.require(), body));
    }

    @Operation(summary = "查询员工试发任务")
    @GetMapping("/task/{taskId}")
    public Result<Map<String, Object>> task(@PathVariable Long taskId) {
        return Result.ok(employeeService.task(UserContext.require(), taskId));
    }

    @Operation(summary = "顾客扫码领取试发结果")
    @PostMapping("/claim")
    public Result<Map<String, Object>> claim(@RequestBody Map<String, Object> body) {
        Long taskId = body.get("taskId") instanceof Number n
                ? n.longValue() : Long.parseLong(String.valueOf(body.get("taskId")));
        String token = body.get("token") == null ? "" : String.valueOf(body.get("token"));
        return Result.ok(employeeService.claim(UserContext.require(), taskId, token));
    }

    @Operation(summary = "员工今日额度")
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota() {
        return Result.ok(employeeService.quota(UserContext.require()));
    }
}
