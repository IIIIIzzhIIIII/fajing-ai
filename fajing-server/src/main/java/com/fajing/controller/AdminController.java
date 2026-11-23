package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AdminLog;
import com.fajing.entity.AiCallLog;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Order;
import com.fajing.mapper.AdminLogMapper;
import com.fajing.mapper.AiCallLogMapper;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.AdminLogService;
import com.fajing.service.ChannelStatsService;
import com.fajing.service.CouponService;
import com.fajing.service.OrderSettlementService;
import com.fajing.service.ReconciliationService;
import com.fajing.service.TradeRuleService;
import com.fajing.service.pay.AlipayService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.service.pay.WechatVirtualPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理端接口（由 AdminInterceptor 校验 X-Admin-Token）
 * TODO: 接入 RuoYi 权限体系
 */
@Tag(name = "管理端 · 内容与订单", description = "发型管理、帖子/评论审核、举报处理与订单查看")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final HairstyleMapper hairstyleMapper;
    private final OrderMapper orderMapper;
    private final AdminLogService adminLogService;
    private final GenerationTaskMapper taskMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AdminLogMapper adminLogMapper;
    private final WechatPayService wechatPayService;
    private final AlipayService alipayService;
    private final ChannelStatsService channelStatsService;
    private final TradeRuleService tradeRuleService;
    private final CouponService couponService;
    private final OrderSettlementService settlementService;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final ReconciliationService reconciliationService;

    @Operation(summary = "订单退款（微信 / 支付宝）")
    @PostMapping("/orders/{id}/refund")
    public Result<Map<String, Object>> refundOrder(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!"paid".equals(order.getStatus())) {
            throw new BizException("订单未支付，无法退款");
        }
        if ("refunded".equals(order.getRefundStatus())) {
            throw new BizException("该订单已退款");
        }
        // 魅力值是即时消耗型资产，充值后不支持退款
        if ("points".equals(order.getBizType())) {
            throw new BizException("魅力值充值订单不支持退款");
        }
        // 团购券已核销说明用户已到店消费，不能再退
        if ("coupon".equals(order.getBizType()) && couponService.hasUsedCoupon(order.getId())) {
            throw new BizException("团购券已核销，无法退款");
        }
        Integer refundDays = tradeRuleService.get().getRefundDays();
        if (refundDays != null && refundDays > 0 && order.getPaidAt() != null
                && order.getPaidAt().plusDays(refundDays).isBefore(LocalDateTime.now())) {
            throw new BizException("已超过售后期限（" + refundDays + " 天），无法退款");
        }
        BigDecimal amount;
        try {
            amount = body != null && body.get("amount") != null
                    ? new BigDecimal(String.valueOf(body.get("amount"))) : order.getAmount();
        } catch (NumberFormatException e) {
            throw new BizException("退款金额格式不正确");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(order.getAmount()) > 0) {
            throw new BizException("退款金额不合法");
        }
        String reason = body == null || body.get("reason") == null
                ? "管理员退款" : String.valueOf(body.get("reason"));
        String channel = order.getPayChannel() == null ? "" : order.getPayChannel();
        // 以退款状态做条件更新，避免并发点击或重复提交造成重复退款
        int locked = orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", id)
                .and(w -> w.eq("refund_status", "none").or().isNull("refund_status"))
                .set("refund_status", "refunding"));
        if (locked == 0) {
            throw new BizException("该订单正在退款或已退款");
        }
        boolean asyncRefund = false;
        try {
            switch (channel) {
                case "wx" -> {
                    WechatPayService.RefundResult result = wechatPayService.refund(order.getOutTradeNo(),
                            "RF" + order.getOutTradeNo(), amount, order.getAmount(), reason);
                    // 银行卡等渠道微信异步处理，最终结果由退款查单任务确认
                    asyncRefund = !"SUCCESS".equalsIgnoreCase(result.status());
                }
                case "wx-virtual" -> {
                    wechatVirtualPayService.refund(order.getUserId(), order.getOutTradeNo(), amount, reason);
                    asyncRefund = true;
                }
                case "alipay" -> alipayService.refund(order.getOutTradeNo(), amount, reason);
                case "mock" -> {
                    // 本地模拟订单无需调用渠道
                }
                default -> throw new BizException("未知支付渠道，无法退款");
            }
        } catch (RuntimeException e) {
            // 渠道退款失败时回滚为可退款，便于人工重试
            orderMapper.update(null, new UpdateWrapper<Order>()
                    .eq("id", id)
                    .eq("refund_status", "refunding")
                    .set("refund_status", "none"));
            throw e;
        }
        // 异步退款保持 refunding，最终结果以退款查单任务为准
        String refundStatus = asyncRefund ? "refunding" : "refunded";
        order.setRefundStatus(refundStatus);
        order.setRefundAmount(amount);
        orderMapper.updateById(order);
        // 全额退款且已确认到账时回收权益；异步退款等查单确认后再回收
        if ("refunded".equals(refundStatus) && amount.compareTo(order.getAmount()) == 0) {
            settlementService.revokeBenefits(order);
        }
        adminLogService.log("ORDER_REFUND", "order:" + id + ",amount:" + amount);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", id);
        data.put("refundStatus", refundStatus);
        data.put("refundAmount", amount);
        return Result.ok(data);
    }

    @Operation(summary = "操作日志")
    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        Long total = adminLogMapper.selectCount(null);
        List<AdminLog> rows = adminLogMapper.selectList(new QueryWrapper<AdminLog>()
                .orderByDesc("created_at").last("limit " + (p - 1) * s + "," + s));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total == null ? 0 : total);
        data.put("page", p);
        data.put("size", s);
        return Result.ok(data);
    }

    @Operation(summary = "按日对账汇总（默认前一日）")
    @GetMapping("/reconciliation")
    public Result<Map<String, Object>> reconciliation(
            @RequestParam(required = false) String date) {
        LocalDate day;
        if (date == null || date.isBlank()) {
            day = LocalDate.now().minusDays(1);
        } else {
            try {
                day = LocalDate.parse(date);
            } catch (RuntimeException e) {
                throw new BizException("日期格式不正确，应为 yyyy-MM-dd");
            }
        }
        return Result.ok(reconciliationService.summary(day));
    }

    @Operation(summary = "今日运营数据（生成量、成本、成功率、付费转化）")
    @GetMapping("/stats/today")
    public Result<Map<String, Object>> todayStats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long generateCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .ge("created_at", start));
        Long successCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .eq("status", "success").ge("created_at", start));
        Long failedCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .eq("status", "failed").ge("created_at", start));
        Long aiCalls = aiCallLogMapper.selectCount(new QueryWrapper<AiCallLog>()
                .ge("created_at", start));
        long costCents = sumOf(aiCallLogMapper.selectMaps(new QueryWrapper<AiCallLog>()
                .select("IFNULL(SUM(cost_cents),0) AS total").ge("created_at", start)));
        // 统计口径剔除已全额退款订单，避免「退款单也算付费」
        Long paidOrders = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("status", "paid").ge("created_at", start)
                .and(w -> w.isNull("refund_status").or().ne("refund_status", "refunded")));
        long payingUsers = sumOf(orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("COUNT(DISTINCT user_id) AS total")
                .eq("status", "paid").ge("created_at", start)
                .and(w -> w.isNull("refund_status").or().ne("refund_status", "refunded"))));
        long generateUsers = sumOf(taskMapper.selectMaps(new QueryWrapper<GenerationTask>()
                .select("COUNT(DISTINCT user_id) AS total").ge("created_at", start)));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generateCount", generateCount == null ? 0 : generateCount);
        data.put("successCount", successCount == null ? 0 : successCount);
        data.put("failedCount", failedCount == null ? 0 : failedCount);
        data.put("successRate", rate(successCount, generateCount));
        data.put("aiCalls", aiCalls == null ? 0 : aiCalls);
        data.put("costCents", costCents);
        data.put("costYuan", String.format("%.2f", costCents / 100.0));
        data.put("paidOrders", paidOrders == null ? 0 : paidOrders);
        data.put("payingUsers", payingUsers);
        data.put("generateUsers", generateUsers);
        data.put("conversionRate", generateUsers == 0 ? 0 : Math.round(payingUsers * 1000.0 / generateUsers) / 10.0);
        return Result.ok(data);
    }

    @Operation(summary = "渠道与传播数据（按小程序 / H5 聚合）")
    @GetMapping("/stats/channels")
    public Result<Map<String, Object>> channelStats(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return Result.ok(channelStatsService.stats(days));
    }

    private long sumOf(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0).get("total");
        if (total == null) {
            return 0L;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(total)).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double rate(Long part, Long all) {
        if (part == null || all == null || all == 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / all) / 10.0;
    }

    @Operation(summary = "发型列表（管理端）")
    @GetMapping("/hairstyles")
    public Result<List<Hairstyle>> hairstyles(@RequestParam(required = false) String gender) {
        QueryWrapper<Hairstyle> wrapper = new QueryWrapper<Hairstyle>().orderByAsc("sort");
        if (gender != null && !gender.isBlank()) {
            wrapper.eq("gender", gender);
        }
        return Result.ok(hairstyleMapper.selectList(wrapper));
    }

    @Operation(summary = "新增发型")
    @PostMapping("/hairstyles")
    public Result<Hairstyle> create(@RequestBody Hairstyle hairstyle) {
        if (hairstyle.getName() == null || hairstyle.getName().isBlank()) {
            throw new BizException("发型名称不能为空");
        }
        if (hairstyle.getEn() == null || hairstyle.getEn().isBlank()) {
            throw new BizException("英文提示词不能为空");
        }
        if (hairstyle.getId() == null || hairstyle.getId().isBlank()) {
            hairstyle.setId((("male".equals(hairstyle.getGender()) ? "m" : "f")
                    + UUID.randomUUID().toString().substring(0, 6)));
        }
        if (hairstyle.getStatus() == null) {
            hairstyle.setStatus(1);
        }
        if (hairstyle.getSort() == null || hairstyle.getSort() == 0) {
            hairstyle.setSort(99);
        }
        hairstyle.setCreatedAt(LocalDateTime.now());
        hairstyleMapper.insert(hairstyle);
        adminLogService.log("CREATE_HAIRSTYLE", hairstyle.getId());
        return Result.ok(hairstyle);
    }

    @Operation(summary = "编辑发型")
    @PutMapping("/hairstyles/{id}")
    public Result<Hairstyle> update(@PathVariable String id, @RequestBody Hairstyle hairstyle) {
        Hairstyle exists = hairstyleMapper.selectById(id);
        if (exists == null) {
            throw new BizException("发型不存在");
        }
        hairstyle.setId(id);
        hairstyle.setCreatedAt(exists.getCreatedAt());
        hairstyleMapper.updateById(hairstyle);
        adminLogService.log("UPDATE_HAIRSTYLE", id);
        return Result.ok(hairstyleMapper.selectById(id));
    }

    @Operation(summary = "上架/下架发型")
    @PatchMapping("/hairstyles/{id}/status")
    public Result<Hairstyle> status(@PathVariable String id, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态值不合法");
        }
        Hairstyle exists = hairstyleMapper.selectById(id);
        if (exists == null) {
            throw new BizException("发型不存在");
        }
        exists.setStatus(status);
        hairstyleMapper.updateById(exists);
        adminLogService.log("HAIRSTYLE_STATUS", id + ":" + exists.getStatus());
        return Result.ok(exists);
    }

    @Operation(summary = "订单列表")
    @GetMapping("/orders")
    public Result<Map<String, Object>> orders(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : Math.max(1, Math.min(100, size));
        Long total = orderMapper.selectCount(null);
        List<Order> rows = orderMapper.selectList(new QueryWrapper<Order>()
                .orderByDesc("created_at").last("limit " + (p - 1) * s + "," + s));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total == null ? 0 : total);
        data.put("page", p);
        data.put("size", s);
        return Result.ok(data);
    }
}
