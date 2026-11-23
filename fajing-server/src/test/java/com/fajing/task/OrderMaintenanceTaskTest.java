package com.fajing.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.entity.Order;
import com.fajing.entity.PayConfig;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.OrderSettlementService;
import com.fajing.service.PayConfigService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.service.pay.WechatVirtualPayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单维护任务：未支付挂单关闭、微信 / 虚拟支付退款终态确认。
 * 覆盖退款成功回收权益、部分退款不回收、退款失败回滚、查单异常跳过。
 */
@ExtendWith(MockitoExtension.class)
class OrderMaintenanceTaskTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private PayConfigService payConfigService;
    @Mock
    private WechatPayService wechatPayService;
    @Mock
    private WechatVirtualPayService wechatVirtualPayService;
    @Mock
    private OrderSettlementService settlementService;

    @InjectMocks
    private OrderMaintenanceTask task;

    @Test
    void wxRefundSuccessMarksRefundedAndRevokesFullRefund() throws Exception {
        Order order = refundingOrder("wx", "10.00", "10.00");
        mockWxReady();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(wechatPayService.queryRefund("RF" + order.getOutTradeNo()))
                .thenReturn(json("{\"status\":\"SUCCESS\"}"));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        task.confirmWxRefunds();

        assertThat(capturedUpdate().getParamNameValuePairs().values()).contains("refunded");
        verify(settlementService).revokeBenefits(order);
    }

    @Test
    void wxPartialRefundDoesNotRevokeBenefits() throws Exception {
        Order order = refundingOrder("wx", "10.00", "4.00");
        mockWxReady();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(wechatPayService.queryRefund("RF" + order.getOutTradeNo()))
                .thenReturn(json("{\"status\":\"SUCCESS\"}"));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        task.confirmWxRefunds();

        verify(settlementService, never()).revokeBenefits(any());
    }

    @Test
    void wxRefundClosedRollsBackToRefundable() throws Exception {
        Order order = refundingOrder("wx", "10.00", "10.00");
        mockWxReady();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(wechatPayService.queryRefund("RF" + order.getOutTradeNo()))
                .thenReturn(json("{\"status\":\"CLOSED\"}"));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        task.confirmWxRefunds();

        assertThat(capturedUpdate().getParamNameValuePairs().values()).contains("none");
        verify(settlementService, never()).revokeBenefits(any());
    }

    @Test
    void wxRefundQueryFailureIsSkipped() {
        Order order = refundingOrder("wx", "10.00", "10.00");
        mockWxReady();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(wechatPayService.queryRefund(any()))
                .thenThrow(new RuntimeException("timeout"));

        assertThatCode(task::confirmWxRefunds).doesNotThrowAnyException();
        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void wxNotConfiguredSkipsRefundConfirmation() {
        when(payConfigService.get()).thenReturn(new PayConfig());
        when(payConfigService.wxReady(any())).thenReturn(false);

        task.confirmWxRefunds();

        verify(orderMapper, never()).selectList(any());
    }

    @Test
    void virtualRefundCompletedRevokesFullRefund() {
        Order order = refundingOrder("wx-virtual", "10.00", "10.00");
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(wechatVirtualPayService.queryOrder(order.getUserId(), order.getOutTradeNo()))
                .thenReturn(virtualOrder(1000, 0));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        task.confirmVirtualRefunds();

        verify(settlementService).revokeBenefits(order);
    }

    @Test
    void virtualPartialRefundDoesNotRevokeBenefits() {
        Order order = refundingOrder("wx-virtual", "10.00", "4.00");
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        // 已退 400 分，达到申请的 400 分 → 退款完成，但非全额退款
        when(wechatVirtualPayService.queryOrder(order.getUserId(), order.getOutTradeNo()))
                .thenReturn(virtualOrder(1000, 600));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        task.confirmVirtualRefunds();

        verify(orderMapper).update(isNull(), any());
        verify(settlementService, never()).revokeBenefits(any());
    }

    @Test
    void unfinishedVirtualRefundKeepsRefunding() {
        Order order = refundingOrder("wx-virtual", "10.00", "10.00");
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        // 已退 200 分，未达到申请的 1000 分
        when(wechatVirtualPayService.queryOrder(order.getUserId(), order.getOutTradeNo()))
                .thenReturn(virtualOrder(1000, 800));

        task.confirmVirtualRefunds();

        verify(orderMapper, never()).update(isNull(), any());
        verify(settlementService, never()).revokeBenefits(any());
    }

    @Test
    void staleCreatedOrderIsClosedAtChannelAndLocally() {
        Order order = createdOrder("wx", "FJ-STALE");
        mockWxReady();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        task.closeStaleOrders();

        verify(wechatPayService).close("FJ-STALE");
        assertThat(capturedUpdate().getParamNameValuePairs().values()).contains("closed");
    }

    @Test
    void channelCloseFailureDoesNotBlockLocalClose() {
        Order order = createdOrder("wx", "FJ-STALE");
        mockWxReady();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("close fail")).when(wechatPayService).close("FJ-STALE");

        assertThatCode(task::closeStaleOrders).doesNotThrowAnyException();

        verify(orderMapper).update(isNull(), any());
    }

    @Test
    void staleOrderQueryFailureIsSwallowed() {
        when(orderMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(task::closeStaleOrders).doesNotThrowAnyException();
        verify(orderMapper, never()).update(isNull(), any());
    }

    private void mockWxReady() {
        when(payConfigService.get()).thenReturn(new PayConfig());
        when(payConfigService.wxReady(any())).thenReturn(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private UpdateWrapper<Order> capturedUpdate() {
        ArgumentCaptor<Wrapper<Order>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        return (UpdateWrapper<Order>) captor.getValue();
    }

    private static JsonNode json(String raw) throws Exception {
        return JSON.readTree(raw);
    }

    private static WechatVirtualPayService.VirtualOrder virtualOrder(int paidFee, int leftFee) {
        WechatVirtualPayService.VirtualOrder snapshot = new WechatVirtualPayService.VirtualOrder();
        snapshot.paid = true;
        snapshot.paidFee = paidFee;
        snapshot.leftFee = leftFee;
        snapshot.wxOrderId = "WX1";
        return snapshot;
    }

    private static Order refundingOrder(String channel, String amount, String refundAmount) {
        Order order = baseOrder(channel, amount);
        order.setStatus("paid");
        order.setPaidAt(java.time.LocalDateTime.now());
        order.setRefundStatus("refunding");
        order.setRefundAmount(new BigDecimal(refundAmount));
        return order;
    }

    private static Order createdOrder(String channel, String outTradeNo) {
        Order order = baseOrder(channel, "10.00");
        order.setOutTradeNo(outTradeNo);
        order.setStatus("created");
        order.setCreatedAt(java.time.LocalDateTime.now().minusHours(3));
        return order;
    }

    private static Order baseOrder(String channel, String amount) {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(7L);
        order.setOutTradeNo("FJ1");
        order.setPayChannel(channel);
        order.setAmount(new BigDecimal(amount));
        order.setBizType("vip");
        return order;
    }
}
