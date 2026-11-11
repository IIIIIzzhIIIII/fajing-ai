package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.UserUnlock;
import com.fajing.mapper.UserUnlockMapper;
import com.fajing.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UnlockController {

    private final UserUnlockMapper unlockMapper;

    @GetMapping("/unlocks")
    public Result<List<String>> unlocks() {
        List<UserUnlock> list = unlockMapper.selectList(new QueryWrapper<UserUnlock>()
                .eq("user_id", GenerationService.USER_ID));
        if (list == null) {
            throw new BizException("查询解锁记录失败");
        }
        return Result.ok(list.stream().map(UserUnlock::getStyleId).collect(Collectors.toList()));
    }
}
