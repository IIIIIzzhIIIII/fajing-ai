package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AppUser;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Order;
import com.fajing.entity.UserRecord;
import com.fajing.entity.UserUnlock;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.UserRecordMapper;
import com.fajing.mapper.UserUnlockMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 · 用户管理
 */
@Tag(name = "管理端 · 用户", description = "用户列表与启用/停用")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserMapper userMapper;
    private final GenerationTaskMapper taskMapper;
    private final UserRecordMapper recordMapper;
    private final UserUnlockMapper unlockMapper;
    private final OrderMapper orderMapper;
    private final com.fajing.service.AdminLogService adminLogService;

    @Operation(summary = "用户列表（含生成/解锁/付费统计）")
    @GetMapping("/users")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        QueryWrapper<AppUser> wrapper = new QueryWrapper<AppUser>().orderByDesc("id");
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("nickname", kw).or().like("phone", kw));
        }
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        Long total = userMapper.selectCount(wrapper);
        wrapper.last("limit " + (p - 1) * s + "," + s);
        List<AppUser> users = userMapper.selectList(wrapper);
        List<Long> userIds = users.stream().map(AppUser::getId).toList();

        Map<Long, Long> taskCounts = userIds.isEmpty() ? Map.of()
                : toCountMap(taskMapper.selectMaps(new QueryWrapper<GenerationTask>()
                .select("user_id AS userId", "COUNT(*) AS cnt").in("user_id", userIds).groupBy("user_id")));
        Map<Long, Long> recordCounts = userIds.isEmpty() ? Map.of()
                : toCountMap(recordMapper.selectMaps(new QueryWrapper<UserRecord>()
                .select("user_id AS userId", "COUNT(*) AS cnt").in("user_id", userIds).groupBy("user_id")));
        Map<Long, Long> unlockCounts = userIds.isEmpty() ? Map.of()
                : toCountMap(unlockMapper.selectMaps(new QueryWrapper<UserUnlock>()
                .select("user_id AS userId", "COUNT(*) AS cnt").in("user_id", userIds).groupBy("user_id")));
        Map<Long, BigDecimal> paidSums = userIds.isEmpty() ? Map.of()
                : toSumMap(orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("user_id AS userId", "IFNULL(SUM(amount),0) AS total")
                .eq("status", "paid")
                .and(w -> w.isNull("refund_status").or().ne("refund_status", "refunded"))
                .in("user_id", userIds)
                .groupBy("user_id")));

        List<Map<String, Object>> rows = users.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("nickname", u.getNickname());
            map.put("avatar", u.getAvatar());
            map.put("gender", u.getGender());
            map.put("phone", u.getPhone());
            map.put("status", u.getStatus());
            map.put("registerAt", u.getRegisterAt());
            map.put("lastActiveAt", u.getLastActiveAt());
            map.put("taskCount", taskCounts.getOrDefault(u.getId(), 0L));
            map.put("recordCount", recordCounts.getOrDefault(u.getId(), 0L));
            map.put("unlockCount", unlockCounts.getOrDefault(u.getId(), 0L));
            map.put("paidAmount", paidSums.getOrDefault(u.getId(), BigDecimal.ZERO));
            return map;
        }).collect(java.util.stream.Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total == null ? 0 : total);
        data.put("page", p);
        data.put("size", s);
        return Result.ok(data);
    }

    @Operation(summary = "启用/停用用户")
    @PatchMapping("/users/{id}/status")
    public Result<AppUser> status(@PathVariable Long id, @RequestParam Integer status) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("用户状态不合法");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        adminLogService.log("USER_STATUS", id + ":" + user.getStatus());
        return Result.ok(user);
    }

    private Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("userId"));
            Long cnt = toLong(row.get("cnt"));
            if (userId != null) {
                map.put(userId, cnt == null ? 0L : cnt);
            }
        }
        return map;
    }

    private Map<Long, BigDecimal> toSumMap(List<Map<String, Object>> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("userId"));
            Object total = row.get("total");
            if (userId != null && total != null) {
                map.put(userId, new BigDecimal(String.valueOf(total)));
            }
        }
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
