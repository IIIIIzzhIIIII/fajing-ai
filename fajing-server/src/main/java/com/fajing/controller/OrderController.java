package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Order;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.GenerationService;
import com.fajing.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单：创建 → 支付回调 → 查询 → 退款骨架
 * 未配置微信支付商户信息时走 mock 分支（创建即视为支付成功），便于本地联调。
 * 解锁判定的唯一依据在服务端 user_unlock 表。
 */
@Tag(name = "订单", description = "创建订单、支付回调与查询；解锁以 user_unlock 为准")
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderMapper orderMapper;
    private final GenerationTaskMapper taskMapper;
    private final GenerationService generationService;

    /** mock 支付仅在非生产环境开启；生产必须接真实微信支付 */
    @Value("${pay.mock-enabled:true}")
    private boolean mockPayEnabled;

    @Value("${pay.wxpay-enabled:false}")
    private boolean wxpayEnabled;

    /** 创建订单；wxpay 未配置时直接 mock 支付成功并写解锁 */
    @Operation(summary = "创建解锁订单（未接微信支付时走 mock 并直接解锁）")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        List<String> styleIds = styleIdsOf(body);
        Long taskId = taskIdOf(body);
        String plan = body.get("plan") == null ? "all" : String.valueOf(body.get("plan"));
        Long userId = UserContext.require();

        if (taskId > 0) {
            GenerationTask task = taskMapper.selectById(taskId);
            if (task == null || !Long.valueOf(userId).equals(task.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
            }
        }

        BigDecimal amount = "one".equals(plan)
                ? new BigDecimal("6.00")
                : new BigDecimal(styleIds.size() * 3).setScale(2);

        Order order = new Order();
        order.setUserId(userId);
        order.setTaskId(taskId);
        order.setPlan(plan);
        order.setAmount(amount);
        order.setStyleIds(String.join(",", styleIds));
        order.setCreatedAt(LocalDateTime.now());

        boolean mock = !wxpayEnabled && mockPayEnabled;
        order.setStatus(mock ? "paid" : "created");
        orderMapper.insert(order);

        if (mock) {
            unlockAll(userId, styleIds, taskId);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("amount", amount);
        data.put("mock", mock);
        data.put("paid", mock);
        data.put("payParams", null);
        return Result.ok(data);
    }

    /** 兼容老前端：等同 create 的 mock 分支；生产环境关闭 */
    @Operation(summary = "兼容接口：等同创建订单（生产不可用）")
    @PostMapping("/mock")
    public Result<Map<String, Object>> mockPay(@RequestBody Map<String, Object> body) {
        if (!mockPayEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口不存在");
        }
        return create(body);
    }

    /** 微信支付回调骨架：验签待接入，收到成功才置 paid 并写解锁 */
    @Operation(summary = "微信支付回调（骨架，验签待接入）")
    @PostMapping("/notify")
    public Map<String, String> notify(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, String> resp = new LinkedHashMap<>();
        try {
            // TODO 接入微信支付验签：校验 header 证书签名后解析 resource
            Long orderId = body == null || body.get("orderId") == null
                    ? null : Long.parseLong(String.valueOf(body.get("orderId")));
            Order order = orderId == null ? null : orderMapper.selectById(orderId);
            if (order != null && !"paid".equals(order.getStatus())) {
                order.setStatus("paid");
                orderMapper.updateById(order);
                unlockAll(order.getUserId(), splitIds(order.getStyleIds()), order.getTaskId());
            }
            resp.put("code", "SUCCESS");
            resp.put("message", "OK");
        } catch (Exception e) {
            log.warn("支付回调处理失败：{}", e.getMessage());
            resp.put("code", "FAIL");
            resp.put("message", "处理失败");
        }
        return resp;
    }

    @Operation(summary = "查询订单状态")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> status(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        Long userId = UserContext.require();
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("status", order.getStatus());
        data.put("amount", order.getAmount());
        data.put("styleIds", splitIds(order.getStyleIds()));
        return Result.ok(data);
    }

    /** 退款：骨架，未接入微信支付 */
    @Operation(summary = "申请退款（未接入）")
    @PostMapping("/refund")
    public Result<Void> refund() {
        throw new BizException("退款功能未接入");
    }

    private void unlockAll(Long userId, List<String> styleIds, Long taskId) {
        for (String id : styleIds) {
            generationService.unlock(userId, id, taskId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> styleIdsOf(Map<String, Object> body) {
        Object raw = body.get("styleIds");
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object o : items) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    list.add(String.valueOf(o));
                }
            }
        }
        if (list.isEmpty()) {
            throw new BizException("缺少解锁内容");
        }
        if (list.size() > 4) {
            throw new BizException("解锁数量不正确");
        }
        return list;
    }

    private Long taskIdOf(Map<String, Object> body) {
        Object raw = body.get("taskId");
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private List<String> splitIds(String ids) {
        List<String> list = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return list;
        }
        for (String s : ids.split(",")) {
            if (!s.isBlank()) {
                list.add(s);
            }
        }
        return list;
    }
}
