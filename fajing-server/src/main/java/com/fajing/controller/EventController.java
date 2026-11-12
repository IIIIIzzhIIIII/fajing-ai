package com.fajing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.Result;
import com.fajing.entity.EventLog;
import com.fajing.mapper.EventLogMapper;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 前端埋点批量上报：失败静默，不影响业务
 */
@Slf4j
@Tag(name = "埋点", description = "前端关键行为批量上报")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private static final int MAX_BATCH = 50;

    private final EventLogMapper eventLogMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "批量上报埋点事件")
    @PostMapping("/event")
    public Result<Void> report(@RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("events");
        if (!(raw instanceof List<?> events)) {
            return Result.ok();
        }
        Long userId = UserContext.require();
        int count = 0;
        for (Object item : events) {
            if (count >= MAX_BATCH) {
                break;
            }
            if (!(item instanceof Map<?, ?> event)) {
                continue;
            }
            Object name = event.get("event");
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            try {
                EventLog row = new EventLog();
                row.setUserId(userId);
                String eventName = String.valueOf(name);
                row.setEvent(eventName.length() > 48 ? eventName.substring(0, 48) : eventName);
                Object payload = event.get("payload");
                row.setPayloadJson(payload == null ? null : objectMapper.writeValueAsString(payload));
                row.setCreatedAt(LocalDateTime.now());
                eventLogMapper.insert(row);
                count++;
            } catch (Exception e) {
                log.debug("埋点写入失败: {}", e.getMessage());
            }
        }
        return Result.ok();
    }
}
