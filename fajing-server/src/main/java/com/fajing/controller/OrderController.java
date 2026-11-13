package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AppUser;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Order;
import com.fajing.entity.PayConfig;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.GenerationService;
import com.fajing.service.PayConfigService;
import com.fajing.service.pay.AlipayService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.util.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单：按渠道下单 → 渠道回调验签 → 查询。
 * 支付成功的唯一准入口径是渠道异步通知（dev 的 mock 开关除外）；解锁只在这里写入。
 */
@Tag(name = "订单", description = "创建订单、支付回调与查询；解锁以 user_unlock 为准")
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final GenerationTaskMapper taskMapper;
    private final AppUserMapper userMapper;
    private final GenerationService generationService;
    private final PayConfigService payConfigService;
    private final WechatPayService wechatPayService;
    private final AlipayService alipayService;

    /** mock 支付仅在非生产环境开启；生产必须接真实支付 */
    @Value("${pay.mock-enabled:true}")
    private boolean mockPayEnabled;

    @Operation(summary = "创建解锁订单")
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

        PayConfig cfg = payConfigService.get();
        boolean wxReady = payConfigService.wxReady(cfg);
        boolean alipayReady = payConfigService.alipayReady(cfg);
        String channel = body.get("channel") == null ? "" : String.valueOf(body.get("channel")).trim();
        if (channel.isBlank()) {
            if (wxReady) {
                channel = "wx";
            } else if (alipayReady) {
                channel = "alipay";
            }
        }

        boolean mock = mockPayEnabled && !wxReady && !alipayReady && !"wx".equals(channel) && !"alipay".equals(channel);

        Order order = new Order();
        order.setUserId(userId);
        order.setTaskId(taskId);
        order.setPlan(plan);
        order.setAmount(amount);
        order.setStyleIds(String.join(",", styleIds));
        order.setOutTradeNo(generateTradeNo());
        order.setPayChannel(mock ? "mock" : channel);
        order.setRefundStatus("none");
        order.setRefundAmount(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(mock ? "paid" : "created");
        if (mock) {
            order.setPaidAt(LocalDateTime.now());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount);
        data.put("mock", mock);
        data.put("paid", mock);

        if (mock) {
            orderMapper.insert(order);
            unlockAll(userId, styleIds, taskId);
            data.put("orderId", order.getId());
            data.put("outTradeNo", order.getOutTradeNo());
            return Result.ok(data);
        }

        if ("wx".equals(channel)) {
            if (!wxReady) {
                throw new BizException("微信支付参数未配置完整，请在管理端「支付配置」补全");
            }
            AppUser user = userMapper.selectById(userId);
            orderMapper.insert(order);
            try {
                String prepayId = wechatPayService.createJsapi(
                        order.getOutTradeNo(), amount, "发镜 AI 解锁高清图", user == null ? "" : user.getOpenid());
                order.setPrepayId(prepayId);
                orderMapper.updateById(order);
                data.put("orderId", order.getId());
                data.put("outTradeNo", order.getOutTradeNo());
                data.put("payParams", wechatPayService.payParams(prepayId));
            } catch (BizException e) {
                order.setStatus("closed");
                orderMapper.updateById(order);
                throw e;
            }
            return Result.ok(data);
        }

        if ("alipay".equals(channel)) {
            if (!alipayReady) {
                throw new BizException("支付宝参数未配置完整，请在管理端「支付配置」补全");
            }
            orderMapper.insert(order);
            String form = alipayService.buildWapForm(order.getOutTradeNo(), amount, "发镜 AI 解锁高清图");
            data.put("orderId", order.getId());
            data.put("outTradeNo", order.getOutTradeNo());
            data.put("payForm", form);
            return Result.ok(data);
        }

        throw new BizException("不支持的支付渠道：" + channel);
    }

    /** 兼容老前端：等同 mock 下单；生产环境关闭 */
    @Operation(summary = "兼容接口：mock 下单（生产不可用）")
    @PostMapping("/mock")
    public Result<Map<String, Object>> mockPay(@RequestBody Map<String, Object> body) {
        if (!mockPayEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口不存在");
        }
        body.put("channel", "mock");
        return create(body);
    }

    /** 微信支付回调：验签 → 解密 → 校验金额 → 幂等置 paid */
    @Operation(summary = "微信支付回调")
    @PostMapping("/notify/wx")
    public ResponseEntity<Map<String, String>> wxNotify(@RequestHeader Map<String, String> headers,
                                                        @RequestBody(required = false) String body) {
        Map<String, String> resp = new LinkedHashMap<>();
        try {
            JsonNode data = wechatPayService.verifyNotify(headers, body == null ? "" : body);
            String tradeState = data.path("trade_state").asText("");
            if (!tradeState.isBlank() && !"SUCCESS".equals(tradeState)) {
                throw new BizException("微信交易状态非成功：" + tradeState);
            }
            String outTradeNo = data.path("out_trade_no").asText("");
            int totalCents = data.path("amount").path("total").asInt(-1);
            Order order = findByTradeNo(outTradeNo);
            if (order == null) {
                throw new BizException("订单不存在：" + outTradeNo);
            }
            int expectCents = order.getAmount().multiply(BigDecimal.valueOf(100)).intValueExact();
            if (totalCents != expectCents) {
                throw new BizException("微信回调金额不匹配");
            }
            markPaid(order, data.path("transaction_id").asText(""));
            resp.put("code", "SUCCESS");
            resp.put("message", "成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.warn("微信回调处理失败：{}", e.getMessage());
            resp.put("code", "FAIL");
            resp.put("message", e.getMessage() == null ? "处理失败" : e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    /** 支付宝异步通知：RSA2 验签 → 校验 app_id/金额 → 幂等置 paid，返回纯文本 success */
    @Operation(summary = "支付宝异步通知")
    @PostMapping("/notify/alipay")
    public ResponseEntity<String> alipayNotify(@RequestParam Map<String, String> params) {
        try {
            if (!alipayService.verifyNotify(params)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            PayConfig cfg = payConfigService.get();
            String appId = params.get("app_id");
            if (appId == null || !appId.equals(cfg.getAlipayAppId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            String tradeStatus = params.getOrDefault("trade_status", "");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            String outTradeNo = params.getOrDefault("out_trade_no", "");
            Order order = findByTradeNo(outTradeNo);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            BigDecimal notifyAmount = new BigDecimal(params.getOrDefault("total_amount", "0"));
            if (notifyAmount.compareTo(order.getAmount()) != 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            markPaid(order, params.getOrDefault("trade_no", ""));
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.warn("支付宝回调处理失败：{}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
        }
    }

    /** 旧回调路径已废弃，避免被误用绕过验签 */
    @Operation(summary = "旧回调（已废弃）")
    @PostMapping("/notify")
    public void legacyNotify() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口已废弃，请使用渠道回调地址");
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
        data.put("paid", "paid".equals(order.getStatus()));
        data.put("amount", order.getAmount());
        data.put("styleIds", splitIds(order.getStyleIds()));
        data.put("payChannel", order.getPayChannel());
        data.put("outTradeNo", order.getOutTradeNo());
        data.put("paidAt", order.getPaidAt());
        data.put("refundStatus", order.getRefundStatus());
        data.put("refundAmount", order.getRefundAmount());
        return Result.ok(data);
    }

    /* ---------------- 内部实现 ---------------- */

    private Order findByTradeNo(String outTradeNo) {
        if (outTradeNo == null || outTradeNo.isBlank()) {
            return null;
        }
        return orderMapper.selectOne(new QueryWrapper<Order>().eq("out_trade_no", outTradeNo).last("limit 1"));
    }

    /** 幂等：已支付订单只补流水号，不重复解锁 */
    private void markPaid(Order order, String transactionId) {
        if ("paid".equals(order.getStatus())) {
            return;
        }
        order.setStatus("paid");
        order.setTransactionId(transactionId == null ? "" : transactionId);
        order.setPaidAt(LocalDateTime.now());
        order.setRefundStatus("none");
        orderMapper.updateById(order);
        unlockAll(order.getUserId(), splitIds(order.getStyleIds()), order.getTaskId());
    }

    private void unlockAll(Long userId, List<String> styleIds, Long taskId) {
        for (String id : styleIds) {
            generationService.unlock(userId, id, taskId);
        }
    }

    private String generateTradeNo() {
        return "FJ" + LocalDateTime.now().format(NO_FORMAT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
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
