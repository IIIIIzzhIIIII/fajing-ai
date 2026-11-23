package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.Order;
import com.fajing.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付对账汇总：按渠道统计当日支付 / 退款 / 净收入，并列出异常单数量。
 * 渠道账单仍需在微信 / 支付宝 / 抖音后台下载后人工比对。
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final OrderMapper orderMapper;

    public Map<String, Object> summary(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>()
                .ge("created_at", start)
                .lt("created_at", end));

        Map<String, Channel> channels = new LinkedHashMap<>();
        for (Order order : orders) {
            String channel = order.getPayChannel() == null || order.getPayChannel().isBlank()
                    ? "unknown" : order.getPayChannel();
            Channel item = channels.computeIfAbsent(channel, k -> new Channel());
            if ("paid".equals(order.getStatus())) {
                item.paidOrders++;
                item.paidAmount = item.paidAmount.add(money(order.getAmount()));
            }
            if ("refunded".equals(order.getRefundStatus())) {
                item.refundedOrders++;
                item.refundAmount = item.refundAmount.add(money(order.getRefundAmount()));
            }
        }

        Channel total = new Channel();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Channel> entry : channels.entrySet()) {
            Channel c = entry.getValue();
            rows.add(c.toMap(entry.getKey()));
            total.paidOrders += c.paidOrders;
            total.paidAmount = total.paidAmount.add(c.paidAmount);
            total.refundedOrders += c.refundedOrders;
            total.refundAmount = total.refundAmount.add(c.refundAmount);
        }

        Map<String, Object> anomalies = new LinkedHashMap<>();
        anomalies.put("paidWithoutPaidAt", count(new QueryWrapper<Order>()
                .eq("status", "paid").isNull("paid_at")
                .ge("created_at", start).lt("created_at", end)));
        anomalies.put("refundedWithoutAmount", count(new QueryWrapper<Order>()
                .eq("refund_status", "refunded")
                .and(w -> w.isNull("refund_amount").or().le("refund_amount", BigDecimal.ZERO))
                .ge("created_at", start).lt("created_at", end)));
        anomalies.put("nonPositiveAmount", count(new QueryWrapper<Order>()
                .le("amount", BigDecimal.ZERO)
                .ge("created_at", start).lt("created_at", end)));
        anomalies.put("staleRefunding", count(new QueryWrapper<Order>()
                .eq("refund_status", "refunding")
                .lt("created_at", LocalDateTime.now().minusHours(24))));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("channels", rows);
        data.put("total", total.toMap("total"));
        data.put("anomalies", anomalies);
        return data;
    }

    private long count(QueryWrapper<Order> wrapper) {
        Long value = orderMapper.selectCount(wrapper);
        return value == null ? 0L : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static class Channel {
        long paidOrders;
        BigDecimal paidAmount = BigDecimal.ZERO;
        long refundedOrders;
        BigDecimal refundAmount = BigDecimal.ZERO;

        Map<String, Object> toMap(String channel) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("channel", channel);
            map.put("paidOrders", paidOrders);
            map.put("paidAmount", paidAmount);
            map.put("refundedOrders", refundedOrders);
            map.put("refundAmount", refundAmount);
            map.put("netAmount", paidAmount.subtract(refundAmount));
            return map;
        }
    }
}
