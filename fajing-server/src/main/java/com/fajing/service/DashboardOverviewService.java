package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Order;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.UserUnlockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板 · 经营总览：KPI、周期趋势、渠道收入构成、运营待办与热门发型。
 * 订单口径按创建日统计，净实收 = 实付金额 - 已退款金额。
 */
@Service
@RequiredArgsConstructor
public class DashboardOverviewService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");

    private static final Map<String, String> CHANNEL_LABELS = new LinkedHashMap<>();

    static {
        CHANNEL_LABELS.put("mp-weixin", "微信小程序");
        CHANNEL_LABELS.put("h5", "公众号 H5");
        CHANNEL_LABELS.put("mp-toutiao", "抖音小程序");
        CHANNEL_LABELS.put("mp-xhs", "小红书小程序");
        CHANNEL_LABELS.put("unknown", "未标记");
    }

    private final OrderMapper orderMapper;
    private final GenerationTaskMapper taskMapper;
    private final UserUnlockMapper unlockMapper;

    public Map<String, Object> overview(Integer days, LocalDate start, LocalDate end, String platform) {
        LocalDate endDate = end != null ? end : LocalDate.now();
        LocalDate startDate = start != null ? start : endDate.minusDays((days == null ? 7 : Math.max(1, Math.min(365, days))) - 1L);
        if (startDate.isAfter(endDate)) {
            startDate = endDate;
        }
        // 自定义区间同样限制在一年内，避免超大范围查询拖垮数据库
        LocalDate earliest = endDate.minusDays(365);
        if (startDate.isBefore(earliest)) {
            startDate = earliest;
        }
        int span = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevEnd = startDate.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(span - 1L);
        String channelFilter = normalizeFilter(platform);

        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>()
                .ge("created_at", prevStart.atStartOfDay())
                .lt("created_at", endDate.plusDays(1).atStartOfDay()));

        Map<LocalDate, Daily> daily = new LinkedHashMap<>();
        Map<String, ChannelStat> channelStats = new LinkedHashMap<>();
        for (Order order : orders) {
            if (!matchChannel(order, channelFilter)) {
                continue;
            }
            LocalDate day = order.getCreatedAt() == null ? null : order.getCreatedAt().toLocalDate();
            if (day == null) {
                continue;
            }
            Daily item = daily.computeIfAbsent(day, key -> new Daily());
            String status = order.getStatus() == null ? "" : order.getStatus();
            if ("paid".equals(status)) {
                BigDecimal amount = money(order.getAmount());
                BigDecimal refund = money(order.getRefundAmount());
                item.paidOrders++;
                item.paidAmount = item.paidAmount.add(amount);
                item.netRevenue = item.netRevenue.add(amount.subtract(refund));
                if ("refunded".equals(order.getRefundStatus())) {
                    item.refundedOrders++;
                }
                if (!day.isBefore(startDate) && !day.isAfter(endDate)) {
                    ChannelStat channel = channelStats.computeIfAbsent(platformCode(order),
                            key -> new ChannelStat());
                    channel.paidOrders++;
                    channel.netRevenue = channel.netRevenue.add(amount.subtract(refund));
                }
            } else if ("created".equals(status) || "pending".equals(status)) {
                item.pendingOrders++;
            } else if ("closed".equals(status) || "failed".equals(status)) {
                item.failedOrders++;
            }
        }

        Daily current = sumRange(daily, startDate, endDate);
        Daily previous = sumRange(daily, prevStart, prevEnd);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("netRevenue", kpi(moneyText(current.netRevenue), moneyText(previous.netRevenue), true));
        kpis.put("paidOrders", kpi(current.paidOrders, previous.paidOrders, false));
        kpis.put("pendingOrders", kpi(current.pendingOrders, previous.pendingOrders, false));
        kpis.put("failedPayments", kpi(current.failedOrders, previous.failedOrders, false));
        kpis.put("paidAmount", kpi(moneyText(current.paidAmount), moneyText(previous.paidAmount), true));

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("current", trendRows(daily, startDate, endDate));
        trend.put("previous", trendRows(daily, prevStart, prevEnd));

        List<Map<String, Object>> channels = new ArrayList<>();
        for (Map.Entry<String, String> entry : CHANNEL_LABELS.entrySet()) {
            ChannelStat stat = channelStats.getOrDefault(entry.getKey(), new ChannelStat());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", entry.getKey());
            row.put("label", entry.getValue());
            row.put("paidOrders", stat.paidOrders);
            row.put("netRevenue", moneyText(stat.netRevenue));
            channels.add(row);
        }

        Long failedTasks = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .eq("status", "failed")
                .ge("created_at", startDate.atStartOfDay())
                .lt("created_at", endDate.plusDays(1).atStartOfDay()));

        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todo("failedTasks", "生成异常", "查看失败原因并重试交付",
                failedTasks == null ? 0 : failedTasks, "", ""));
        todos.add(todo("pendingOrders", "待支付订单", "有订单等待用户完成支付",
                current.pendingOrders, "/orders", "查看订单"));
        todos.add(todo("refundedOrders", "退款订单", "已退款订单可在订单管理核对",
                current.refundedOrders, "/orders", "查看订单"));

        List<Map<String, Object>> hotStyles = new ArrayList<>();
        for (Map<String, Object> row : unlockMapper.hotStyles(startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("styleId", text(row.get("styleId")));
            item.put("name", text(row.get("name")));
            item.put("count", num(row.get("unlocks")));
            hotStyles.add(item);
        }

        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", startDate.toString());
        range.put("end", endDate.toString());
        range.put("prevStart", prevStart.toString());
        range.put("prevEnd", prevEnd.toString());
        range.put("days", span);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", range);
        data.put("platform", channelFilter == null ? "all" : channelFilter);
        data.put("kpis", kpis);
        data.put("trend", trend);
        data.put("channels", channels);
        data.put("todos", todos);
        data.put("hotStyles", hotStyles);
        return data;
    }

    /* ---------------- 内部实现 ---------------- */

    private static final class Daily {
        private long paidOrders;
        private long pendingOrders;
        private long failedOrders;
        private long refundedOrders;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private BigDecimal netRevenue = BigDecimal.ZERO;
    }

    private static final class ChannelStat {
        private long paidOrders;
        private BigDecimal netRevenue = BigDecimal.ZERO;
    }

    private static Daily sumRange(Map<LocalDate, Daily> daily, LocalDate start, LocalDate end) {
        Daily total = new Daily();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            Daily item = daily.get(cursor);
            if (item != null) {
                total.paidOrders += item.paidOrders;
                total.pendingOrders += item.pendingOrders;
                total.failedOrders += item.failedOrders;
                total.refundedOrders += item.refundedOrders;
                total.paidAmount = total.paidAmount.add(item.paidAmount);
                total.netRevenue = total.netRevenue.add(item.netRevenue);
            }
            cursor = cursor.plusDays(1);
        }
        return total;
    }

    private static List<Map<String, Object>> trendRows(Map<LocalDate, Daily> daily, LocalDate start, LocalDate end) {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            Daily item = daily.getOrDefault(cursor, new Daily());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", cursor.format(DAY));
            row.put("netRevenue", moneyText(item.netRevenue));
            row.put("paidOrders", item.paidOrders);
            rows.add(row);
            cursor = cursor.plusDays(1);
        }
        return rows;
    }

    private static boolean matchChannel(Order order, String filter) {
        if (filter == null) {
            return true;
        }
        return filter.equals(platformCode(order));
    }

    private static String platformCode(Order order) {
        return order.getPlatform() == null || order.getPlatform().isBlank()
                ? "unknown" : order.getPlatform();
    }

    private static String normalizeFilter(String platform) {
        if (platform == null || platform.isBlank() || "all".equals(platform)) {
            return null;
        }
        return platform;
    }

    private static Map<String, Object> kpi(Object value, Object prev, boolean money) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put("prev", prev);
        row.put("delta", delta(value, prev));
        row.put("money", money);
        return row;
    }

    private static Double delta(Object value, Object prev) {
        BigDecimal current = decimal(value);
        BigDecimal before = decimal(prev);
        if (before.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(before)
                .multiply(BigDecimal.valueOf(100))
                .divide(before, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Map<String, Object> todo(String code, String label, String desc,
                                            long count, String link, String linkText) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("label", label);
        row.put("desc", desc);
        row.put("count", count);
        row.put("link", link);
        row.put("linkText", linkText);
        return row;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String moneyText(BigDecimal value) {
        return money(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static long num(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
