package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.entity.AppUser;
import com.fajing.entity.Order;
import com.fajing.mapper.AiCallLogMapper;
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
 * 渠道标识来自前端埋点 payload.channel；订单优先按下单来源端归因，缺失时用最近访问渠道兜底。
 * 净实收 = 实付金额 - 已退款金额 - 大模型生图成本。
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
        CHANNEL_LABELS.put("mp-toutiao", "抖音小程序");
        CHANNEL_LABELS.put("mp-xhs", "小红书小程序");
        PAY_LABELS.put("wx", "微信支付");
        PAY_LABELS.put("wx-virtual", "微信虚拟支付");
        PAY_LABELS.put("alipay", "支付宝");
        PAY_LABELS.put("douyin", "抖音支付");
        PAY_LABELS.put("xhs", "小红书支付");
        PAY_LABELS.put("mock", "模拟支付");
    }

    private final EventLogMapper eventLogMapper;
    private final OrderMapper orderMapper;
    private final AppUserMapper userMapper;
    private final AiCallLogMapper aiCallLogMapper;

    public Map<String, Object> stats(Integer days) {
        int range = days == null ? 7 : Math.max(1, Math.min(90, days));
        LocalDateTime start = LocalDate.now().minusDays(range - 1L).atStartOfDay();

        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        CHANNEL_LABELS.forEach((code, label) -> rows.put(code, newRow(code, label)));

        // 埋点使用数据：只认标准渠道，历史脏数据（分享方式/支付方式误写进 channel）直接忽略
        for (Map<String, Object> item : eventLogMapper.statsByChannel(start)) {
            String channel = standardChannel(str(item.get("channel")));
            if (channel == null) {
                continue;
            }
            Map<String, Object> row = rows.get(channel);
            row.put("visitUsers", num(item.get("visitUsers")));
            row.put("viewUsers", num(item.get("viewUsers")));
            row.put("generateSuccess", num(item.get("generateSuccess")));
            row.put("saveImages", num(item.get("saveImages")));
            row.put("shareEvents", num(item.get("shareEvents")));
        }

        // 用户 → 最近渠道，订单缺少来源端时兜底归因
        Map<Long, String> userChannel = new LinkedHashMap<>();
        Map<Long, LocalDateTime> userChannelAt = new LinkedHashMap<>();
        for (Map<String, Object> item : eventLogMapper.userChannels(start)) {
            Long userId = longOf(item.get("userId"));
            String channel = standardChannel(str(item.get("channel")));
            if (userId == null || channel == null) {
                continue;
            }
            LocalDateTime at = timeOf(item.get("lastAt"));
            if (at == null || userChannelAt.get(userId) == null || at.isAfter(userChannelAt.get(userId))) {
                userChannelAt.put(userId, at);
                userChannel.put(userId, channel);
            }
        }

        List<Order> paidOrders = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("status", "paid")
                .apply("COALESCE(paid_at, created_at) >= {0}", start));
        List<AppUser> newUsers = userMapper.selectList(new QueryWrapper<AppUser>()
                .ge("register_at", start).select("id", "openid"));
        Map<Long, BigDecimal> aiCostByUser = new LinkedHashMap<>();
        for (Map<String, Object> item : aiCallLogMapper.costByUser(start, LocalDateTime.now())) {
            Long userId = longOf(item.get("userId"));
            if (userId != null) {
                aiCostByUser.merge(userId, money(item.get("costCents")), BigDecimal::add);
            }
        }

        java.util.Set<Long> userIds = new java.util.LinkedHashSet<>();
        paidOrders.forEach(order -> userIds.add(order.getUserId()));
        newUsers.forEach(user -> userIds.add(user.getId()));
        userIds.addAll(aiCostByUser.keySet());
        Map<Long, String> accountChannel = accountChannels(userIds);

        // 已支付订单：净实收 = 金额 - 已退款金额，最后再扣生图成本
        Map<String, Map<String, Object>> payRows = new LinkedHashMap<>();
        Map<String, java.util.Set<Long>> payingUsers = new LinkedHashMap<>();
        for (Order order : paidOrders) {
            String channel = resolveChannel(order.getPlatform(), order.getUserId(), userChannel, accountChannel);
            Map<String, Object> row = rows.get(channel);
            row.put("paidOrders", num(row.get("paidOrders")) + 1);
            row.put("netRevenue", money(row.get("netRevenue")).add(netAmount(order)));
            payingUsers.computeIfAbsent(channel, code -> new java.util.LinkedHashSet<>()).add(order.getUserId());

            String payChannel = order.getPayChannel() == null ? "" : order.getPayChannel().trim();
            // 历史订单没有支付方式（未记录）时不展示，支付渠道只保留真实支付方式
            if (!PAY_LABELS.containsKey(payChannel)) {
                continue;
            }
            Map<String, Object> payRow = payRows.computeIfAbsent(payChannel,
                    code -> payRow(code, PAY_LABELS.get(code)));
            payRow.put("orders", num(payRow.get("orders")) + 1);
            payRow.put("amount", money(payRow.get("amount")).add(netAmount(order)));
        }

        // 新客：统计周期内注册的用户，按渠道归因
        for (AppUser user : newUsers) {
            String channel = resolveChannel(null, user.getId(), userChannel, accountChannel);
            Map<String, Object> row = rows.get(channel);
            row.put("newUsers", num(row.get("newUsers")) + 1);
        }

        // 大模型生图成本：按用户归属到渠道，从净实收中扣除
        for (Map.Entry<Long, BigDecimal> entry : aiCostByUser.entrySet()) {
            String channel = resolveChannel(null, entry.getKey(), userChannel, accountChannel);
            BigDecimal cost = entry.getValue().divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            Map<String, Object> row = rows.get(channel);
            row.put("aiCost", money(row.get("aiCost")).add(cost));
            row.put("netRevenue", money(row.get("netRevenue")).subtract(cost));
        }

        for (Map<String, Object> row : rows.values()) {
            int visits = (int) num(row.get("visitUsers"));
            int payers = payingUsers.getOrDefault(str(row.get("channel")), java.util.Set.of()).size();
            row.put("payingUsers", payers);
            row.put("payRate", visits == 0 ? 0 : Math.round(payers * 1000.0 / visits) / 10.0);
            row.put("aiCost", money(row.get("aiCost")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
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
        BigDecimal aiCost = BigDecimal.ZERO;
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
            aiCost = aiCost.add(money(row.get("aiCost")));
        }
        totals.put("visitUsers", visits);
        totals.put("viewUsers", viewUsers);
        totals.put("generateSuccess", generateSuccess);
        totals.put("saveImages", saveImages);
        totals.put("shareEvents", shareEvents);
        totals.put("paidOrders", paidOrdersCount);
        totals.put("newUsers", newUserCount);
        totals.put("payingUsers", allPayingUsers.size());
        totals.put("aiCost", aiCost.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
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
        row.put("aiCost", "0.00");
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

    private static String standardChannel(String channel) {
        return CHANNEL_LABELS.containsKey(channel) ? channel : null;
    }

    /** 账号兜底渠道：H5 游客（openid 以 guest: 开头或为空）归 H5，其余按微信小程序 */
    private Map<Long, String> accountChannels(java.util.Set<Long> userIds) {
        Map<Long, String> map = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        List<AppUser> users = userMapper.selectList(new QueryWrapper<AppUser>()
                .select("id", "openid").in("id", userIds));
        for (AppUser user : users) {
            String openid = user.getOpenid() == null ? "" : user.getOpenid();
            map.put(user.getId(), openid.startsWith("guest:") || openid.isBlank() ? "h5" : "mp-weixin");
        }
        return map;
    }

    /** 订单来源端优先；缺失时用最近一次访问渠道，再退回账号类型 */
    private static String resolveChannel(String platform, Long userId,
                                         Map<Long, String> userChannel, Map<Long, String> accountChannel) {
        String channel = standardChannel(platform);
        if (channel != null) {
            return channel;
        }
        channel = userChannel.get(userId);
        if (channel != null) {
            return channel;
        }
        return accountChannel.getOrDefault(userId, "h5");
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
