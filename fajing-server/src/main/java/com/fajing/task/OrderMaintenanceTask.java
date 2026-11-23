package com.fajing.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.entity.Order;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.OrderSettlementService;
import com.fajing.service.PayConfigService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.service.pay.WechatVirtualPayService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单维护：关闭长期未支付的悬挂单、确认微信 / 虚拟支付退款终态。
 * 都属于补偿动作，失败只记录日志，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMaintenanceTask {

    /** 未支付订单保留时长：超过后关闭，避免后台长期堆积 created 单据 */
    private static final int CLOSE_AFTER_HOURS = 2;

    /** 每轮最多处理的退款单数，避免一次拉取过多 */
    private static final int REFUND_CONFIRM_LIMIT = 50;

    private final OrderMapper orderMapper;
    private final PayConfigService payConfigService;
    private final WechatPayService wechatPayService;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final OrderSettlementService settlementService;

    @Scheduled(cron = "0 10 * * * ?")
    public void closeStaleOrders() {
        try {
            List<Order> stale = orderMapper.selectList(new QueryWrapper<Order>()
                    .eq("status", "created")
                    .lt("created_at", LocalDateTime.now().minusHours(CLOSE_AFTER_HOURS))
                    .orderByAsc("id")
                    .last("limit 100"));
            boolean wxReady = payConfigService.wxReady(payConfigService.get());
            for (Order order : stale) {
                // JSAPI 订单先通知微信关单；虚拟支付没有关单接口，只做本地关闭
                if (wxReady && "wx".equals(order.getPayChannel())) {
                    try {
                        wechatPayService.close(order.getOutTradeNo());
                    } catch (Exception e) {
                        log.debug("微信关单失败 orderId={}: {}", order.getId(), e.getMessage());
                    }
                }
            }
            int closed = orderMapper.update(null, new UpdateWrapper<Order>()
                    .eq("status", "created")
                    .lt("created_at", LocalDateTime.now().minusHours(CLOSE_AFTER_HOURS))
                    .set("status", "closed"));
            if (closed > 0) {
                log.info("关闭超时未支付订单 {} 笔", closed);
            }
        } catch (Exception e) {
            log.warn("关闭超时未支付订单失败：{}", e.getMessage());
        }
    }

    /** 虚拟支付退款是异步任务，没有回调，只能定期查单确认是否退完 */
    @Scheduled(cron = "0 */10 * * * ?")
    public void confirmVirtualRefunds() {
        List<Order> orders;
        try {
            orders = orderMapper.selectList(new QueryWrapper<Order>()
                    .eq("pay_channel", "wx-virtual")
                    .eq("refund_status", "refunding")
                    .orderByAsc("id")
                    .last("limit " + REFUND_CONFIRM_LIMIT));
        } catch (Exception e) {
            log.warn("查询虚拟支付退款中订单失败：{}", e.getMessage());
            return;
        }
        for (Order order : orders) {
            try {
                WechatVirtualPayService.VirtualOrder snapshot =
                        wechatVirtualPayService.queryOrder(order.getUserId(), order.getOutTradeNo());
                // left_fee 归零代表全部退完；部分退款则以「已退金额达到申请金额」判定完成
                int refundCents = order.getRefundAmount() == null ? 0
                        : order.getRefundAmount().multiply(BigDecimal.valueOf(100)).intValueExact();
                boolean refunded = snapshot.leftFee <= 0
                        || (refundCents > 0 && snapshot.paidFee - snapshot.leftFee >= refundCents);
                if (refunded) {
                    int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                            .eq("id", order.getId())
                            .eq("refund_status", "refunding")
                            .set("refund_status", "refunded"));
                    if (rows > 0 && isFullRefund(order)) {
                        settlementService.revokeBenefits(order);
                    }
                    log.info("虚拟支付退款已完成 orderId={}", order.getId());
                }
            } catch (Exception e) {
                log.debug("虚拟支付退款查单失败 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
    }

    /** JSAPI 退款可能是异步的（银行卡等），定期查退款单确认终态 */
    @Scheduled(cron = "30 */10 * * * ?")
    public void confirmWxRefunds() {
        if (!payConfigService.wxReady(payConfigService.get())) {
            return;
        }
        List<Order> orders;
        try {
            orders = orderMapper.selectList(new QueryWrapper<Order>()
                    .eq("pay_channel", "wx")
                    .eq("refund_status", "refunding")
                    .orderByAsc("id")
                    .last("limit " + REFUND_CONFIRM_LIMIT));
        } catch (Exception e) {
            log.warn("查询微信退款中订单失败：{}", e.getMessage());
            return;
        }
        for (Order order : orders) {
            try {
                JsonNode refund = wechatPayService.queryRefund("RF" + order.getOutTradeNo());
                String status = refund.path("status").asText("");
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                            .eq("id", order.getId())
                            .eq("refund_status", "refunding")
                            .set("refund_status", "refunded"));
                    if (rows > 0 && isFullRefund(order)) {
                        settlementService.revokeBenefits(order);
                    }
                    log.info("微信退款已完成 orderId={}", order.getId());
                } else if ("CLOSED".equalsIgnoreCase(status) || "ABNORMAL".equalsIgnoreCase(status)) {
                    // 退款失败：回滚为可退款，便于人工重试
                    orderMapper.update(null, new UpdateWrapper<Order>()
                            .eq("id", order.getId())
                            .eq("refund_status", "refunding")
                            .set("refund_status", "none"));
                    log.warn("微信退款失败，已回滚为可退款 orderId={} status={}", order.getId(), status);
                }
            } catch (Exception e) {
                log.debug("微信退款查单失败 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private static boolean isFullRefund(Order order) {
        return order.getRefundAmount() != null && order.getAmount() != null
                && order.getRefundAmount().compareTo(order.getAmount()) == 0;
    }
}
