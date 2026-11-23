package com.fajing.service;

import com.fajing.entity.Order;
import com.fajing.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单结算：支付成功入账、权益发放、退款权益回收。
 * 状态更新与权益发放放在同一事务里，避免「已置 paid 但发货失败」的断链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSettlementService {

    private final OrderMapper orderMapper;
    private final PointsService pointsService;
    private final CouponService couponService;
    private final GenerationService generationService;
    private final GenerationProcessor generationProcessor;

    /** 幂等入账：已支付订单不重复发放，解锁类补解锁以修复历史断链 */
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Order order, String transactionId) {
        if ("paid".equals(order.getStatus())) {
            if (order.getBizType() == null || order.getBizType().isBlank() || "unlock".equals(order.getBizType())) {
                unlockAll(order.getUserId(), splitIds(order.getStyleIds()), order.getTaskId());
            }
            return;
        }
        order.setStatus("paid");
        order.setTransactionId(transactionId == null ? "" : transactionId);
        order.setPaidAt(LocalDateTime.now());
        order.setRefundStatus("none");
        orderMapper.updateById(order);
        fulfill(order);
    }

    /** 按订单业务类型发放权益；重复回调由 markPaid 的幂等判断兜住 */
    public void fulfill(Order order) {
        String bizType = order.getBizType() == null || order.getBizType().isBlank()
                ? "unlock" : order.getBizType();
        switch (bizType) {
            case "points" -> pointsService.grantPoints(order.getUserId(),
                    order.getPoints() == null ? 0 : order.getPoints());
            case "vip" -> pointsService.grantVip(order.getUserId(),
                    order.getDays() == null || order.getDays() <= 0 ? 30 : order.getDays());
            case "vvip" -> pointsService.grantVvip(order.getUserId());
            case "coupon" -> couponService.issue(order);
            default -> {
                List<String> paidStyleIds = splitIds(order.getStyleIds());
                unlockAll(order.getUserId(), paidStyleIds, order.getTaskId());
                if (order.getTaskId() != null && order.getTaskId() > 0) {
                    generationProcessor.generatePaidStyles(order.getTaskId(), paidStyleIds);
                }
            }
        }
    }

    /** 全额退款完成后回收权益：解锁、团购券、VIP / VVIP；魅力值充值禁止退款，不在此处理 */
    @Transactional(rollbackFor = Exception.class)
    public void revokeBenefits(Order order) {
        generationService.revokeUnlocks(order.getUserId(), order.getTaskId(), order.getStyleIds());
        if ("coupon".equals(order.getBizType())) {
            couponService.markRefundedByOrder(order.getId());
        } else if ("vip".equals(order.getBizType())) {
            pointsService.revokeVip(order.getUserId(), order.getDays() == null ? 0 : order.getDays());
        } else if ("vvip".equals(order.getBizType())) {
            pointsService.revokeVvip(order.getUserId());
        }
    }

    private void unlockAll(Long userId, List<String> styleIds, Long taskId) {
        for (String id : styleIds) {
            generationService.unlock(userId, id, taskId);
        }
    }

    private static List<String> splitIds(String ids) {
        List<String> list = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return list;
        }
        for (String s : ids.split(",")) {
            if (!s.isBlank()) {
                list.add(s);
            }
        }
        return list;
    }
}
