package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.CouponTier;
import com.fajing.entity.Order;
import com.fajing.entity.UserCoupon;
import com.fajing.mapper.CouponTierMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.UserCouponMapper;
import com.fajing.service.pay.AlipayService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.service.pay.WechatVirtualPayService;
import com.fajing.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 周边团购券：固定档位下单 → 支付成功生成核销码 → 线下门店核销。
 * 二维码使用可离线缓存的图片地址，核销以 code 为唯一凭证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTierMapper tierMapper;
    private final UserCouponMapper couponMapper;
    private final OrderMapper orderMapper;
    private final TradeRuleService tradeRuleService;
    private final WechatPayService wechatPayService;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final AlipayService alipayService;
    private final CommissionService commissionService;

    /** 平台默认档位（store_id=0）：新版本默认不启用，门店券由店长自行创建 */
    public List<CouponTier> listTiers() {
        return tierMapper.selectList(new QueryWrapper<CouponTier>()
                .eq("store_id", 0).eq("status", 1).orderByAsc("sort"));
    }

    /** 门店专属档位：只返回该门店上架的券，不回退到平台券，避免跨店核销 */
    public List<CouponTier> listTiers(Long storeId) {
        if (storeId == null || storeId <= 0) {
            return listTiers();
        }
        return tierMapper.selectList(new QueryWrapper<CouponTier>()
                .eq("store_id", storeId).eq("status", 1)
                .orderByAsc("sort").orderByAsc("id"));
    }

    public CouponTier findTier(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return tierMapper.selectById(id);
    }

    /** 支付成功后发放团购券，并生成核销二维码 */
    public UserCoupon issue(Order order) {
        CouponTier tier = findTier(order.getTierId());
        if (tier == null) {
            log.warn("团购券档位不存在 orderId={} tierId={}", order.getId(), order.getTierId());
            return null;
        }
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(order.getUserId());
        coupon.setTierId(tier.getId());
        coupon.setOrderId(order.getId());
        coupon.setStoreId(order.getStoreId() == null ? 0L : order.getStoreId());
        coupon.setStaffId(order.getStaffId() == null ? 0L : order.getStaffId());
        coupon.setTitle(tier.getTitle());
        coupon.setPayAmount(tier.getPayAmount());
        coupon.setValueAmount(tier.getValueAmount());
        coupon.setCommissionAmount(BigDecimal.ZERO);
        coupon.setSettleStatus(order.getStoreId() != null && order.getStoreId() > 0 ? "pending" : "");
        coupon.setVerifiedBy(0L);
        coupon.setCode(code());
        coupon.setQrUrl(qrUrl(coupon.getCode()));
        coupon.setStatus("unused");
        coupon.setExpireAt(LocalDateTime.now().plusDays(90));
        coupon.setCreatedAt(LocalDateTime.now());
        // 同一订单重复回调只发一次券
        Long exists = couponMapper.selectCount(new QueryWrapper<UserCoupon>()
                .eq("order_id", order.getId()));
        if (exists != null && exists > 0) {
            return couponMapper.selectOne(new QueryWrapper<UserCoupon>()
                    .eq("order_id", order.getId()).last("limit 1"));
        }
        couponMapper.insert(coupon);
        // 门店券支付成功即记平台佣金，核销后置为 settled，退款时 reversed
        commissionService.record(order, coupon);
        return coupon;
    }

    public List<UserCoupon> myCoupons() {
        return couponMapper.selectList(new QueryWrapper<UserCoupon>()
                .eq("user_id", UserContext.require())
                .orderByDesc("id"));
    }

    public long unusedCount(Long userId) {
        Long count = couponMapper.selectCount(new QueryWrapper<UserCoupon>()
                .eq("user_id", userId)
                .eq("status", "unused"));
        return count == null ? 0 : count;
    }

    /** 门店核销：只能核销未使用且未过期的券 */
    public UserCoupon redeem(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        UserCoupon coupon = couponMapper.selectOne(new QueryWrapper<UserCoupon>()
                .eq("code", code.trim()).last("limit 1"));
        if (coupon == null || !"unused".equals(coupon.getStatus())) {
            return null;
        }
        if (coupon.getExpireAt() != null && coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            coupon.setStatus("expired");
            couponMapper.updateById(coupon);
            return null;
        }
        coupon.setStatus("used");
        coupon.setUsedAt(LocalDateTime.now());
        couponMapper.updateById(coupon);
        commissionService.settle(coupon.getOrderId());
        coupon.setSettleStatus("settled");
        return coupon;
    }

    /** 店长核销：只能核销本门店的券，核销后佣金台账置为 settled */
    public UserCoupon redeemForStore(String code, Long storeId, Long adminId) {
        if (code == null || code.isBlank() || storeId == null || storeId <= 0) {
            return null;
        }
        UserCoupon coupon = couponMapper.selectOne(new QueryWrapper<UserCoupon>()
                .eq("code", code.trim()).eq("store_id", storeId).last("limit 1"));
        if (coupon == null) {
            return null;
        }
        if (!"unused".equals(coupon.getStatus())) {
            return null;
        }
        if (coupon.getExpireAt() != null && coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            coupon.setStatus("expired");
            couponMapper.updateById(coupon);
            return null;
        }
        int rows = couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("id", coupon.getId())
                .eq("store_id", storeId)
                .eq("status", "unused")
                .set("status", "used")
                .set("used_at", LocalDateTime.now())
                .set("verified_by", adminId == null ? 0L : adminId));
        if (rows == 0) {
            return null;
        }
        coupon.setStatus("used");
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setVerifiedBy(adminId);
        commissionService.settle(coupon.getOrderId());
        coupon.setSettleStatus("settled");
        return coupon;
    }

    /**
     * 用户自助退款：仅未核销的团购券可退，按原支付渠道原路退回。
     * 退款成功后券立即失效，订单退款状态以渠道结果为准。
     */
    public Map<String, Object> refund(Long userId, Long couponId) {
        UserCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !userId.equals(coupon.getUserId())) {
            throw new BizException("团购券不存在");
        }
        if ("used".equals(coupon.getStatus())) {
            throw new BizException("已核销的团购券不支持退款");
        }
        if ("refunded".equals(coupon.getStatus())) {
            throw new BizException("该团购券已退款");
        }
        if ("refunding".equals(coupon.getStatus())) {
            throw new BizException("退款处理中，请稍后查看");
        }
        Order order = orderMapper.selectById(coupon.getOrderId());
        if (order == null) {
            throw new BizException("订单不存在，无法退款");
        }
        // 后台已退款过：只同步券状态，避免重复退款
        if ("refunded".equals(order.getRefundStatus())) {
            coupon.setStatus("refunded");
            couponMapper.updateById(coupon);
            commissionService.reverse(order.getId());
            Map<String, Object> data = toMap(coupon);
            data.put("refundStatus", "refunded");
            data.put("refundAmount", order.getRefundAmount());
            data.put("alreadyRefunded", true);
            return data;
        }
        if ("refunding".equals(order.getRefundStatus())) {
            throw new BizException("退款处理中，请稍后查看");
        }
        if (!"paid".equals(order.getStatus())) {
            throw new BizException("订单未支付，无法退款");
        }
        Integer refundDays = tradeRuleService.get().getRefundDays();
        if (refundDays != null && refundDays > 0 && order.getPaidAt() != null
                && order.getPaidAt().plusDays(refundDays).isBefore(LocalDateTime.now())) {
            throw new BizException("已超过售后期限（" + refundDays + " 天），无法退款");
        }
        BigDecimal amount = coupon.getPayAmount() == null ? order.getAmount() : coupon.getPayAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || (order.getAmount() != null && amount.compareTo(order.getAmount()) > 0)) {
            throw new BizException("退款金额异常，请联系客服");
        }
        // 先锁券再锁订单，重复提交不会重复退款
        int couponLocked = couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("id", couponId)
                .eq("status", "unused")
                .set("status", "refunding"));
        if (couponLocked == 0) {
            throw new BizException("该团购券状态已变化，请刷新后重试");
        }
        int orderLocked = orderMapper.update(null, new UpdateWrapper<Order>()
                .eq("id", order.getId())
                .and(w -> w.eq("refund_status", "none").or().isNull("refund_status"))
                .set("refund_status", "refunding"));
        if (orderLocked == 0) {
            rollbackCoupon(couponId);
            throw new BizException("订单正在退款或已退款");
        }
        String channel = order.getPayChannel() == null ? "" : order.getPayChannel();
        boolean asyncRefund = false;
        try {
            switch (channel) {
                case "wx" -> {
                    WechatPayService.RefundResult result = wechatPayService.refund(order.getOutTradeNo(),
                            "RF" + order.getOutTradeNo(), amount, order.getAmount(), "团购券退款");
                    asyncRefund = !"SUCCESS".equalsIgnoreCase(result.status());
                }
                case "wx-virtual" -> {
                    wechatVirtualPayService.refund(userId, order.getOutTradeNo(), amount, "团购券退款");
                    asyncRefund = true;
                }
                case "alipay" -> alipayService.refund(order.getOutTradeNo(), amount, "团购券退款");
                case "mock" -> {
                    // 本地模拟订单无需调用渠道
                }
                default -> throw new BizException("当前支付渠道暂不支持自助退款，请联系客服");
            }
        } catch (RuntimeException e) {
            rollbackCoupon(couponId);
            orderMapper.update(null, new UpdateWrapper<Order>()
                    .eq("id", order.getId())
                    .eq("refund_status", "refunding")
                    .set("refund_status", "none"));
            throw e;
        }
        // 异步退款保持 refunding，最终结果由退款查单任务更新
        String refundStatus = asyncRefund ? "refunding" : "refunded";
        order.setRefundStatus(refundStatus);
        order.setRefundAmount(amount);
        orderMapper.updateById(order);
        coupon.setStatus("refunded");
        couponMapper.updateById(coupon);
        commissionService.reverse(order.getId());
        log.info("团购券退款 couponId={} orderId={} amount={} channel={}", couponId, order.getId(), amount, channel);
        Map<String, Object> data = toMap(coupon);
        data.put("refundStatus", refundStatus);
        data.put("refundAmount", amount);
        return data;
    }

    private void rollbackCoupon(Long couponId) {
        couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("id", couponId)
                .eq("status", "refunding")
                .set("status", "unused"));
    }

    /** 订单下是否存在已核销的券：已到店消费的订单不允许退款 */
    public boolean hasUsedCoupon(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return false;
        }
        Long count = couponMapper.selectCount(new QueryWrapper<UserCoupon>()
                .eq("order_id", orderId).eq("status", "used"));
        return count != null && count > 0;
    }

    /** 后台整单退款后同步券状态，避免券仍显示可用 */
    public void markRefundedByOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        couponMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("order_id", orderId)
                .in("status", "unused", "refunding")
                .set("status", "refunded"));
        commissionService.reverse(orderId);
    }

    public Map<String, Object> toMap(UserCoupon coupon) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", coupon.getId());
        map.put("title", coupon.getTitle());
        map.put("payAmount", coupon.getPayAmount());
        map.put("valueAmount", coupon.getValueAmount());
        map.put("storeId", coupon.getStoreId());
        map.put("staffId", coupon.getStaffId());
        map.put("commissionAmount", coupon.getCommissionAmount());
        map.put("settleStatus", coupon.getSettleStatus());
        map.put("verifiedBy", coupon.getVerifiedBy());
        map.put("code", coupon.getCode());
        map.put("qrUrl", coupon.getQrUrl());
        map.put("status", coupon.getStatus());
        map.put("expireAt", coupon.getExpireAt());
        map.put("usedAt", coupon.getUsedAt());
        map.put("createdAt", coupon.getCreatedAt());
        return map;
    }

    private static String code() {
        return "C" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** 核销二维码：以核销码为内容，门店扫码后按 code 核销 */
    private static String qrUrl(String code) {
        String data = "FAJING-COUPON:" + code;
        try {
            return "https://api.qrserver.com/v1/create-qr-code/?size=320x320&data="
                    + URLEncoder.encode(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
