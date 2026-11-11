package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.Result;
import com.fajing.entity.UserRecord;
import com.fajing.mapper.UserRecordMapper;
import com.fajing.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecordController {

    private final UserRecordMapper recordMapper;

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records() {
        List<UserRecord> list = recordMapper.selectList(new QueryWrapper<UserRecord>()
                .eq("user_id", GenerationService.USER_ID)
                .orderByDesc("created_at")
                .last("limit 30"));
        return Result.ok(list.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("taskId", r.getTaskId());
            map.put("title", r.getTitle());
            map.put("before", r.getBeforeUrl());
            map.put("cover", r.getCoverUrl());
            map.put("styleIds", Arrays.stream(r.getStyleIds().split(","))
                    .filter(s -> !s.isBlank()).collect(Collectors.toList()));
            map.put("createdAt", r.getCreatedAt());
            return map;
        }).collect(Collectors.toList()));
    }
}
