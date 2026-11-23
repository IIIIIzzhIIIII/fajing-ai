package com.fajing.service;

import com.fajing.entity.Order;
import com.fajing.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 支付结算状态机：首次入账、已支付幂等、历史解锁补偿、发货分发、退款权益回收。
 */
@ExtendWith(MockitoExtension.class)
class OrderSettlementServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long TASK_ID = 3L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private PointsService pointsService;
    @Mock
    private CouponService couponService;
    @Mock
    private GenerationService generationService;
    @Mock
    private GenerationProcessor generationProcessor;

    @InjectMocks
    private OrderSettlementService service;

    @Test
    void createdOrderIsMarkedPaidAndPointsGranted() {
        Order order = order("points");
        order.setStatus("created");
        order.setPoints(100);

        service.markPaid(order, "TX-1");

        assertThat(order.getStatus()).isEqualTo("paid");
        assertThat(order.getTransactionId()).isEqualTo("TX-1");
        assertThat(order.getRefundStatus()).isEqualTo("none");
        assertThat(order.getPaidAt()).isNotNull();
        verify(orderMapper).updateById(order);
        verify(pointsService).grantPoints(USER_ID, 100);
    }

    @Test
    void alreadyPaidOrderIsIdempotent() {
        Order order = order("points");
        order.setStatus("paid");
        order.setPoints(100);

        service.markPaid(order, "TX-2");

        verify(orderMapper, never()).updateById(any());
        verify(pointsService, never()).grantPoints(anyLong(), anyInt());
        verify(couponService, never()).issue(any());
    }

    @Test
    void alreadyPaidUnlockOrderReplaysUnlock() {
        Order order = order("unlock");
        order.setStatus("paid");

        service.markPaid(order, "TX-3");

        verify(generationService).unlock(USER_ID, "f1", TASK_ID);
        verify(generationService).unlock(USER_ID, "f2", TASK_ID);
        verify(orderMapper, never()).updateById(any());
    }

    @Test
    void vipWithoutDaysDefaultsToThirty() {
        Order order = order("vip");
        order.setDays(null);

        service.fulfill(order);

        verify(pointsService).grantVip(USER_ID, 30);
    }

    @Test
    void couponOrderIssuesCoupon() {
        Order order = order("coupon");

        service.fulfill(order);

        verify(couponService).issue(order);
    }

    @Test
    void vvipOrderGrantsPermanentMembership() {
        Order order = order("vvip");

        service.fulfill(order);

        verify(pointsService).grantVvip(USER_ID);
    }

    @Test
    void legacyUnlockOrderUnlocksAndGeneratesPaidStyles() {
        Order order = order("unlock");

        service.fulfill(order);

        verify(generationService).unlock(USER_ID, "f1", TASK_ID);
        verify(generationService).unlock(USER_ID, "f2", TASK_ID);
        verify(generationProcessor).generatePaidStyles(eq(TASK_ID), any());
    }

    @Test
    void couponRefundRevokesUnlocksAndCoupon() {
        Order order = order("coupon");

        service.revokeBenefits(order);

        verify(generationService).revokeUnlocks(USER_ID, TASK_ID, "f1,f2");
        verify(couponService).markRefundedByOrder(order.getId());
        verify(pointsService, never()).revokeVip(anyLong(), anyInt());
    }

    @Test
    void vipRefundRollsBackDays() {
        Order order = order("vip");
        order.setDays(30);

        service.revokeBenefits(order);

        verify(pointsService).revokeVip(USER_ID, 30);
        verify(pointsService, never()).revokeVvip(anyLong());
    }

    @Test
    void vvipRefundClosesMembership() {
        Order order = order("vvip");

        service.revokeBenefits(order);

        verify(pointsService).revokeVvip(USER_ID);
    }

    private static Order order(String bizType) {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(USER_ID);
        order.setTaskId(TASK_ID);
        order.setBizType(bizType);
        order.setStyleIds("f1,f2");
        order.setAmount(new BigDecimal("10.00"));
        return order;
    }
}
