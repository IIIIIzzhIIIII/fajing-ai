package com.fajing.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.entity.Order;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.GenerationService;
import com.fajing.service.pay.WechatVirtualPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单维护：关闭长期未支付的悬挂单、确认虚拟支付退款终态。
 * 两者都是补偿动作，失败只记录日志，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMaintenanceTask {

    /** 未支付订单保留时长：超过后关闭，避免后台长期堆积 created 单据 */
    private static final int CLOSE_AFTER_HOURS = 2;

    /** 每轮最多确认的退款单数，避免一次拉取过多 */
    private static final int REFUND_CONFIRM_LIMIT = 50;

    private final OrderMapper orderMapper;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final GenerationService generationService;

    @Scheduled(cron = "0 10 * * * ?")
    public void closeStaleOrders() {
        try {
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
            log.warn("查询退款中订单失败：{}", e.getMessage());
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
                    orderMapper.update(null, new UpdateWrapper<Order>()
                            .eq("id", order.getId())
                            .eq("refund_status", "refunding")
                            .set("refund_status", "refunded"));
                    // 退款完成后收回解锁，与微信同步退款、支付宝同步退款保持同一口径
                    if (order.getRefundAmount() != null && order.getAmount() != null
                            && order.getRefundAmount().compareTo(order.getAmount()) == 0) {
                        generationService.revokeUnlocks(order.getUserId(), order.getTaskId(), order.getStyleIds());
                    }
                    log.info("虚拟支付退款已完成 orderId={}", order.getId());
                }
            } catch (Exception e) {
                log.debug("虚拟支付退款查单失败 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
