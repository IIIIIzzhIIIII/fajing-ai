package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.entity.CommissionLedger;
import com.fajing.entity.Order;
import com.fajing.entity.Store;
import com.fajing.entity.UserCoupon;
import com.fajing.mapper.CommissionLedgerMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.StoreMapper;
import com.fajing.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 平台佣金台账：
 * - 支付成功：按门店佣金比例记 available
 * - 门店核销：available → settled
 * - 退款：available/settled → reversed
 * 真正的资金分账由支付服务商完成，这里只做平台侧记账与统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final StoreMapper storeMapper;
    private final CommissionLedgerMapper ledgerMapper;
    private final OrderMapper orderMapper;
    private final UserCouponMapper couponMapper;

    public void record(Order order, UserCoupon coupon) {
        if (order == null || order.getId() == null || order.getStoreId() == null || order.getStoreId() <= 0) {
            return;
        }
        Long exists = ledgerMapper.selectCount(new QueryWrapper<CommissionLedger>()
                .eq("order_id", order.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        Store store = storeMapper.selectById(order.getStoreId());
        if (store == null) {
            return;
        }
        BigDecimal rate = store.getCommissionRate() == null
                ? BigDecimal.ZERO : store.getCommissionRate();
        BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        BigDecimal commission = amount.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        CommissionLedger ledger = new CommissionLedger();
        ledger.setTenantId(store.getTenantId());
        ledger.setStoreId(store.getId());
        ledger.setStaffId(order.getStaffId() == null ? 0L : order.getStaffId());
        ledger.setOrderId(order.getId());
        ledger.setOrderAmount(amount);
        ledger.setCommissionRate(rate);
        ledger.setCommissionAmount(commission);
        ledger.setStatus("available");
        ledger.setCreatedAt(LocalDateTime.now());
        try {
            ledgerMapper.insert(ledger);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 支付回调重复触发时唯一索引兜底
            return;
        }

        orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", order.getId())
                .set("commission_amount", commission));
        couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("order_id", order.getId())
                .set("commission_amount", commission)
                .set("settle_status", "pending"));
        log.info("佣金入账 orderId={} storeId={} rate={} amount={}",
                order.getId(), store.getId(), rate, commission);
    }

    public void settle(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        ledgerMapper.update(null, new UpdateWrapper<CommissionLedger>()
                .eq("order_id", orderId)
                .eq("status", "available")
                .set("status", "settled")
                .set("settled_at", LocalDateTime.now()));
        couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("order_id", orderId)
                .set("settle_status", "settled"));
    }

    public void reverse(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        ledgerMapper.update(null, new UpdateWrapper<CommissionLedger>()
                .eq("order_id", orderId)
                .in("status", "available", "settled")
                .set("status", "reversed")
                .set("reversed_at", LocalDateTime.now()));
        couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("order_id", orderId)
                .set("settle_status", "reversed"));
    }
}
