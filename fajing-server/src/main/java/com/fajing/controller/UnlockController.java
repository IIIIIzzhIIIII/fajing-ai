package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.UserUnlock;
import com.fajing.mapper.UserUnlockMapper;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "解锁", description = "当前用户的已解锁款式（按任务+款式维度）")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UnlockController {

    private final UserUnlockMapper unlockMapper;

    /** 解锁按「任务 + 款式」维度返回：同一款发型在新的一批生成里仍需重新解锁 */
    @Operation(summary = "我的解锁列表")
    @GetMapping("/unlocks")
    public Result<List<Map<String, Object>>> unlocks() {
        List<UserUnlock> list = unlockMapper.selectList(new QueryWrapper<UserUnlock>()
                .eq("user_id", UserContext.require()));
        if (list == null) {
            throw new BizException("查询解锁记录失败");
        }
        return Result.ok(list.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("taskId", u.getTaskId());
            map.put("styleId", u.getStyleId());
            return map;
        }).collect(Collectors.toList()));
    }
}
