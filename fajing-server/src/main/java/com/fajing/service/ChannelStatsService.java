package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.AppUser;
import com.fajing.entity.Order;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.EventLogMapper;
import com.fajing.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道与传播：按小程序 / H5 渠道聚合访问、成图、传播、付费与新客数据。
 * 渠道标识来自前端埋点 payload.channel；订单按用户最近一次渠道归因。
 */
@Service
@RequiredArgsConstructor
public class ChannelStatsService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> CHANNEL_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> PAY_LABELS = new LinkedHashMap<>();

    static {
        CHANNEL_LABELS.put("mp-weixin", "微信小程序");
        CHANNEL_LABELS.put("h5", "H5 网页");
        CHANNEL_LABELS.put("unknown", "未标记");
        PAY_LABELS.put("wx", "微信支付");
        PAY_LABELS.put("alipay", "支付宝");
        PAY_LABELS.put("mock", "模拟支付");
    }

    private final EventLogMapper eventLogMapper;
    private final OrderMapper orderMapper;
    private final AppUserMapper userMapper;

    public Map<String, Object> stats(Integer days) {
        int range = days == null ? 7 : Math.max(1, Math.min(90, days));
        LocalDateTime start = LocalDate.now().minusDays(range - 1L).atStartOfDay();

        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        CHANNEL_LABELS.forEach((code, label) -> rows.put(code, newRow(code, label)));

        // 埋点使用数据
        for (Map<String, Object> item : eventLogMapper.statsByChannel(start)) {
            Map<String, Object> row = rows.computeIfAbsent(str(item.get("channel")),
                    code -> newRow(code, labelOf(code)));
            row.put("visitUsers", num(item.get("visitUsers")));
            row.put("viewUsers", num(item.get("viewUsers")));
            row.put("generateSuccess", num(item.get("generateSuccess")));
            row.put("saveImages", num(item.get("saveImages")));
            row.put("shareEvents", num(item.get("shareEvents")));
        }

        // 用户 → 最近渠道，用于订单和新客归因
        Map<Long, String> userChannel = new LinkedHashMap<>();
        Map<Long, LocalDateTime> userChannelAt = new LinkedHashMap<>();
        for (Map<String, Object> item : eventLogMapper.userChannels(start)) {
            Long userId = longOf(item.get("userId"));
            if (userId == null) {
                continue;
            }
            LocalDateTime at = timeOf(item.get("lastAt"));
            if (at == null || userChannelAt.get(userId) == null || at.isAfter(userChannelAt.get(userId))) {
                userChannelAt.put(userId, at);
                userChannel.put(userId, str(item.get("channel")));
            }
        }

        // 已支付订单：净实收 = 金额 - 已退款金额
        List<Order> paidOrders = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("status", "paid")
                .apply("COALESCE(paid_at, created_at) >= {0}", start));
        Map<String, Map<String, Object>> payRows = new LinkedHashMap<>();
        Map<String, java.util.Set<Long>> payingUsers = new LinkedHashMap<>();
        for (Order order : paidOrders) {
            String channel = userChannel.getOrDefault(order.getUserId(), "unknown");
            Map<String, Object> row = rows.computeIfAbsent(channel, code -> newRow(code, labelOf(code)));
            row.put("paidOrders", num(row.get("paidOrders")) + 1);
            row.put("netRevenue", money(row.get("netRevenue")).add(netAmount(order)));
            payingUsers.computeIfAbsent(channel, code -> new java.util.LinkedHashSet<>()).add(order.getUserId());

            String payChannel = order.getPayChannel() == null || order.getPayChannel().isBlank()
                    ? "other" : order.getPayChannel();
            Map<String, Object> payRow = payRows.computeIfAbsent(payChannel,
                    code -> payRow(code, PAY_LABELS.getOrDefault(code, "未记录")));
            payRow.put("orders", num(payRow.get("orders")) + 1);
            payRow.put("amount", money(payRow.get("amount")).add(netAmount(order)));
        }

        // 新客：统计周期内注册的用户，按渠道归因
        List<AppUser> newUsers = userMapper.selectList(new QueryWrapper<AppUser>()
                .ge("register_at", start).select("id", "register_at"));
        for (AppUser user : newUsers) {
            String channel = userChannel.getOrDefault(user.getId(), "unknown");
            Map<String, Object> row = rows.computeIfAbsent(channel, code -> newRow(code, labelOf(code)));
            row.put("newUsers", num(row.get("newUsers")) + 1);
        }

        for (Map<String, Object> row : rows.values()) {
            int visits = (int) num(row.get("visitUsers"));
            int payers = payingUsers.getOrDefault(str(row.get("channel")), java.util.Set.of()).size();
            row.put("payingUsers", payers);
            row.put("payRate", visits == 0 ? 0 : Math.round(payers * 1000.0 / visits) / 10.0);
            row.put("netRevenue", money(row.get("netRevenue")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        }

        Map<String, Object> totals = newRow("all", "全部渠道");
        int visits = 0;
        int viewUsers = 0;
        int generateSuccess = 0;
        int saveImages = 0;
        int shareEvents = 0;
        int paidOrdersCount = 0;
        int newUserCount = 0;
        BigDecimal netRevenue = BigDecimal.ZERO;
        java.util.Set<Long> allPayingUsers = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : rows.values()) {
            visits += (int) num(row.get("visitUsers"));
            viewUsers += (int) num(row.get("viewUsers"));
            generateSuccess += (int) num(row.get("generateSuccess"));
            saveImages += (int) num(row.get("saveImages"));
            shareEvents += (int) num(row.get("shareEvents"));
            paidOrdersCount += (int) num(row.get("paidOrders"));
            newUserCount += (int) num(row.get("newUsers"));
            allPayingUsers.addAll(payingUsers.getOrDefault(str(row.get("channel")), java.util.Set.of()));
            netRevenue = netRevenue.add(money(row.get("netRevenue")));
        }
        totals.put("visitUsers", visits);
        totals.put("viewUsers", viewUsers);
        totals.put("generateSuccess", generateSuccess);
        totals.put("saveImages", saveImages);
        totals.put("shareEvents", shareEvents);
        totals.put("paidOrders", paidOrdersCount);
        totals.put("newUsers", newUserCount);
        totals.put("payingUsers", allPayingUsers.size());
        totals.put("netRevenue", netRevenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        totals.put("payRate", visits == 0 ? 0 : Math.round(allPayingUsers.size() * 1000.0 / visits) / 10.0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", range);
        data.put("startAt", start.format(TIME_FORMAT));
        data.put("totals", totals);
        data.put("channels", new ArrayList<>(rows.values()));
        data.put("payChannels", new ArrayList<>(payRows.values()));
        return data;
    }

    private static Map<String, Object> newRow(String channel, String label) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("channel", channel);
        row.put("label", label);
        row.put("visitUsers", 0);
        row.put("viewUsers", 0);
        row.put("generateSuccess", 0);
        row.put("saveImages", 0);
        row.put("shareEvents", 0);
        row.put("paidOrders", 0);
        row.put("newUsers", 0);
        row.put("netRevenue", "0.00");
        row.put("payRate", 0);
        return row;
    }

    private static Map<String, Object> payRow(String channel, String label) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("channel", channel);
        row.put("label", label);
        row.put("orders", 0);
        row.put("amount", BigDecimal.ZERO);
        return row;
    }

    private static String labelOf(String channel) {
        return CHANNEL_LABELS.getOrDefault(channel, channel == null || channel.isBlank() ? "未标记" : channel);
    }

    private static BigDecimal netAmount(Order order) {
        BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        BigDecimal refund = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        return amount.subtract(refund);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long num(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime timeOf(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = value == null ? "" : String.valueOf(value);
        if (text.length() >= 19) {
            try {
                return LocalDateTime.parse(text.substring(0, 19).replace('T', ' '),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal money(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
