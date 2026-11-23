package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.Order;
import com.fajing.entity.TradeRule;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.AdminLogService;
import com.fajing.service.CouponService;
import com.fajing.service.OrderSettlementService;
import com.fajing.service.TradeRuleService;
import com.fajing.service.pay.WechatPayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款状态机（管理端）：可退前置校验 → 抢占 refunding → 渠道退款 → 置 refunded / 回滚 none。
 * 覆盖不可退场景、并发抢占失败、渠道异常回滚、全额退款回收权益、部分退款与异步退款。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerRefundTest {

    private static final Long ORDER_ID = 1L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CouponService couponService;
    @Mock
    private TradeRuleService tradeRuleService;
    @Mock
    private OrderSettlementService settlementService;
    @Mock
    private AdminLogService adminLogService;
    @Mock
    private WechatPayService wechatPayService;

    @InjectMocks
    private AdminController controller;

    @Test
    void unpaidOrderCannotBeRefunded() {
        Order order = paidOrder();
        order.setStatus("created");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未支付");
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void alreadyRefundedOrderCannotBeRefundedAgain() {
        Order order = paidOrder();
        order.setRefundStatus("refunded");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已退款");
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void pointsOrderIsNotRefundable() {
        Order order = paidOrder();
        order.setBizType("points");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("魅力值充值订单不支持退款");
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void usedCouponOrderIsNotRefundable() {
        Order order = paidOrder();
        order.setBizType("coupon");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(couponService.hasUsedCoupon(ORDER_ID)).thenReturn(true);

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("团购券已核销");
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void refundAfterTradeRuleWindowIsRejected() {
        Order order = paidOrder();
        order.setBizType("vip");
        order.setPaidAt(LocalDateTime.now().minusDays(8));
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(7));

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过售后期限");
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void refundAmountAboveOrderAmountIsRejected() {
        Order order = paidOrder();
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(0));

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, Map.of("amount", "20.00")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("退款金额不合法");
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void concurrentRefundLockFailureIsRejected() {
        Order order = paidOrder();
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(7));
        // 抢占 refunding 失败：说明已被其他请求或任务处理
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("正在退款或已退款");
        verify(orderMapper, times(1)).update(isNull(), any());
    }

    @Test
    void channelFailureRollsBackToRefundable() {
        Order order = paidOrder();
        order.setPayChannel("wx");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(7));
        when(orderMapper.update(isNull(), any())).thenReturn(1, 0);
        when(wechatPayService.refund(anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new BizException("微信退款失败"));

        assertThatThrownBy(() -> controller.refundOrder(ORDER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("微信退款失败");
        // 第一次抢占 refunding，第二次回滚为 none
        verify(orderMapper, times(2)).update(isNull(), any());
        verify(settlementService, never()).revokeBenefits(any());
        verify(adminLogService, never()).log(anyString(), anyString());
    }

    @Test
    void syncFullRefundMarksRefundedAndRevokesBenefits() {
        Order order = paidOrder();
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(7));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        Result<Map<String, Object>> result = controller.refundOrder(ORDER_ID, null);

        assertThat(result.getData()).containsEntry("refundStatus", "refunded");
        assertThat(order.getRefundStatus()).isEqualTo("refunded");
        assertThat(order.getRefundAmount()).isEqualByComparingTo("10.00");
        verify(orderMapper).updateById(order);
        verify(settlementService).revokeBenefits(order);
        verify(adminLogService).log(eq("ORDER_REFUND"), contains("order:1"));
    }

    @Test
    void partialRefundDoesNotRevokeBenefits() {
        Order order = paidOrder();
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(7));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        Result<Map<String, Object>> result = controller.refundOrder(ORDER_ID, Map.of("amount", "4.00"));

        assertThat(result.getData()).containsEntry("refundStatus", "refunded");
        assertThat(order.getRefundAmount()).isEqualByComparingTo("4.00");
        verify(settlementService, never()).revokeBenefits(any());
    }

    @Test
    void asyncChannelRefundStaysRefundingUntilTaskConfirms() {
        Order order = paidOrder();
        order.setPayChannel("wx");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(tradeRuleService.get()).thenReturn(rule(7));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(wechatPayService.refund(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new WechatPayService.RefundResult("R1", "RF-FJ1", "PROCESSING"));

        Result<Map<String, Object>> result = controller.refundOrder(ORDER_ID, null);

        assertThat(result.getData()).containsEntry("refundStatus", "refunding");
        assertThat(order.getRefundStatus()).isEqualTo("refunding");
        verify(orderMapper).updateById(order);
        // 异步退款到账前不能回收权益
        verify(settlementService, never()).revokeBenefits(any());
    }

    private static Order paidOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUserId(7L);
        order.setBizType("vip");
        order.setAmount(new BigDecimal("10.00"));
        order.setOutTradeNo("FJ1");
        order.setStatus("paid");
        order.setRefundStatus("none");
        order.setPayChannel("mock");
        order.setPaidAt(LocalDateTime.now());
        return order;
    }

    private static TradeRule rule(int refundDays) {
        TradeRule rule = new TradeRule();
        rule.setId(1L);
        rule.setRefundDays(refundDays);
        return rule;
    }
}
