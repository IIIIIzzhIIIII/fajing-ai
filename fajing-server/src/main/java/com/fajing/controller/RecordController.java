package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.Result;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.UserRecord;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.UserRecordMapper;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
    private final GenerationTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records() {
        List<UserRecord> list = recordMapper.selectList(new QueryWrapper<UserRecord>()
                .eq("user_id", UserContext.require())
                .orderByDesc("created_at")
                .last("limit 30"));
        return Result.ok(list.stream().map(this::toMap).collect(Collectors.toList()));
    }

    /** 删除单条试发记录 */
    @DeleteMapping("/records/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserRecord record = recordMapper.selectById(id);
        if (record != null && Long.valueOf(UserContext.require()).equals(record.getUserId())) {
            recordMapper.deleteById(id);
        }
        return Result.ok();
    }

    /** 清空当前用户的试发记录，用于从下一次生成开始重新记录 */
    @DeleteMapping("/records")
    public Result<Void> clear() {
        recordMapper.delete(new QueryWrapper<UserRecord>().eq("user_id", UserContext.require()));
        return Result.ok();
    }

    private Map<String, Object> toMap(UserRecord r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("taskId", r.getTaskId());
        map.put("title", r.getTitle());
        map.put("before", r.getBeforeUrl());
        map.put("cover", r.getCoverUrl());
        map.put("styleIds", splitIds(r.getStyleIds()));
        map.put("createdAt", r.getCreatedAt());
        map.put("items", itemsOf(r));
        return map;
    }

    private List<String> splitIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(ids.split(",")).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    /** user_record 只存了款式 ID，真正的 AI 出图在任务结果的 result_json 里，这里补全给记录页展示 */
    private List<Map<String, Object>> itemsOf(UserRecord r) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (r.getTaskId() == null) {
            return items;
        }
        GenerationTask task = taskMapper.selectById(r.getTaskId());
        if (task == null || task.getResultJson() == null || task.getResultJson().isBlank()) {
            return items;
        }
        try {
            Map<?, ?> result = objectMapper.readValue(task.getResultJson(), Map.class);
            Object styles = result.get("styles");
            if (styles instanceof List) {
                for (Object o : (List<?>) styles) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> src = (Map<?, ?>) o;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("styleId", src.get("styleId"));
                    item.put("name", src.get("name"));
                    item.put("img", src.get("img"));
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
            // 历史任务结果解析失败时返回空列表，前端会展示为无图记录
        }
        return items;
    }
}
