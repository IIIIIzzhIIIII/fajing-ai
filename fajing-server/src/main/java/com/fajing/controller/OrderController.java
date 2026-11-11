package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.Order;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟支付：不接真实支付渠道
 * TODO: 接入微信支付
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderMapper orderMapper;
    private final GenerationService generationService;

    @PostMapping("/order/mock")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> mockPay(@RequestBody Map<String, Object> body) {
        String plan = body.get("plan") == null ? "all" : String.valueOf(body.get("plan"));
        Object rawIds = body.get("styleIds");
        List<String> styleIds = new ArrayList<>();
        if (rawIds instanceof List<?> list) {
            for (Object o : list) {
                styleIds.add(String.valueOf(o));
            }
        }
        if (styleIds.isEmpty()) {
            throw new BizException("缺少解锁内容");
        }
        if (styleIds.size() > 4) {
            throw new BizException("解锁数量不正确");
        }
        Object taskIdRaw = body.get("taskId");
        Long taskId = 0L;
        if (taskIdRaw != null) {
            try {
                taskId = Long.parseLong(String.valueOf(taskIdRaw));
            } catch (NumberFormatException ignored) {
                taskId = 0L;
            }
        }

        BigDecimal amount = "one".equals(plan)
                ? new BigDecimal("6.00")
                : new BigDecimal(styleIds.size() * 3).setScale(2);

        Order order = new Order();
        order.setUserId(GenerationService.USER_ID);
        order.setTaskId(taskId);
        order.setPlan(plan);
        order.setAmount(amount);
        order.setStatus("paid");
        order.setStyleIds(String.join(",", styleIds));
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        for (String id : styleIds) {
            generationService.unlock(id, taskId);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paid", true);
        data.put("orderId", order.getId());
        data.put("amount", amount);
        data.put("styleIds", styleIds);
        return Result.ok(data);
    }
}
