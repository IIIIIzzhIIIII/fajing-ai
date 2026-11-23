package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AppUser;
import com.fajing.entity.CouponTier;
import com.fajing.entity.Order;
import com.fajing.entity.PayConfig;
import com.fajing.entity.PointsPackage;
import com.fajing.entity.Store;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.StoreMapper;
import com.fajing.service.CouponService;
import com.fajing.service.OrderSettlementService;
import com.fajing.service.PayConfigService;
import com.fajing.service.PointsPackageService;
import com.fajing.service.PointsService;
import com.fajing.service.pay.AlipayService;
import com.fajing.service.pay.DouyinPayService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.service.pay.WechatVirtualPayService;
import com.fajing.util.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单：充值 / VIP / 团购券下单 → 渠道回调验签 → 查询。
 * 支付成功的唯一准入口径是渠道异步通知（dev 的 mock 开关除外）；
 * 发型解锁统一走魅力值（UnlockController），这里不再处理解锁订单。
 */
@Tag(name = "订单", description = "充值 / VIP / 团购券订单：创建、支付回调与查询")
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final AppUserMapper userMapper;
    private final OrderSettlementService settlementService;
    private final PayConfigService payConfigService;
    private final WechatPayService wechatPayService;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final DouyinPayService douyinPayService;
    private final AlipayService alipayService;
    private final PointsService pointsService;
    private final PointsPackageService pointsPackageService;
    private final CouponService couponService;
    private final StoreMapper storeMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** mock 支付仅在非生产环境开启；生产必须接真实支付 */
    @Value("${pay.mock-enabled:false}")
    private boolean mockPayEnabled;

    @Operation(summary = "创建充值 / VIP / 团购券订单")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.require();
        String bizType = str(body.get("bizType"));
        // 发型解锁已统一改为魅力值（/api/unlock），这里不再接受现金解锁订单
        if (bizType.isBlank() || "unlock".equals(bizType)) {
            throw new BizException("发型解锁请使用魅力值");
        }
        return createBizOrder(bizType, body, userId);
    }

    /** 充值 / VIP / VVIP / 团购券下单：金额与权益只以服务端档位为准 */
    private Result<Map<String, Object>> createBizOrder(String bizType, Map<String, Object> body, Long userId) {
        PayConfig cfg = payConfigService.get();
        BigDecimal amount;
        int points = 0;
        int days = 0;
        Long tierId = 0L;
        Long storeId = 0L;
        Long staffId = 0L;
        switch (bizType) {
            // 充值/VIP/VVIP 一律按后台套餐定价，前端传什么价格都不作数
            case "points", "vip", "vvip" -> {
                PointsPackage pkg = pointsPackageService.findEnabled(longOf(body.get("packageId")));
                bizType = pkg.getBizType();
                amount = pkg.getPrice();
                points = "points".equals(bizType) && pkg.getPoints() != null ? pkg.getPoints() : 0;
                days = "vip".equals(bizType) && pkg.getDays() != null ? pkg.getDays() : 0;
            }
            case "coupon" -> {
                tierId = longOf(body.get("tierId"));
                CouponTier tier = couponService.findTier(tierId);
                if (tier == null || tier.getStatus() == null || tier.getStatus() != 1) {
                    throw new BizException("团购券档位不存在或已下架");
                }
                storeId = tier.getStoreId() == null ? 0L : tier.getStoreId();
                if (storeId > 0) {
                    Store store = storeMapper.selectById(storeId);
                    if (store == null || store.getStatus() == null || store.getStatus() != 1) {
                        throw new BizException("门店已停用，暂时无法购券");
                    }
                    if (!"approved".equals(store.getKycStatus())) {
                        throw new BizException("门店资质审核中，暂时无法购券");
                    }
                    // 员工归因统一在店长后台核销台选择，下单阶段不再依据扫码绑定
                }
                amount = tier.getPayAmount();
            }
            default -> throw new BizException("不支持的订单类型");
        }

        boolean wxReady = payConfigService.wxReady(cfg);
        boolean wxVirtualReady = payConfigService.wxVirtualReady(cfg);
        boolean douyinReady = payConfigService.douyinReady(cfg);
        boolean alipayReady = payConfigService.alipayReady(cfg);
        boolean wxVirtualMode = "virtual".equalsIgnoreCase(cfg.getWxPayMode());
        String platform = normalizePlatform(str(body.get("platform")));
        String channel = str(body.get("channel"));
        if (platform.isBlank()) {
            platform = "wx".equals(channel) ? "mp-weixin" : ("alipay".equals(channel) ? "h5" : "");
        }
        if (channel.isBlank()) {
            if ("h5".equals(platform)) {
                channel = "alipay";
            } else if ("mp-weixin".equals(platform)) {
                channel = "wx";
            } else if ("mp-toutiao".equals(platform)) {
                channel = "douyin";
            } else if (wxReady) {
                channel = "wx";
            } else if (alipayReady) {
                channel = "alipay";
            }
        }
        boolean channelReady = switch (channel) {
            case "wx" -> wxReady || wxVirtualReady;
            case "douyin" -> douyinReady;
            case "alipay" -> alipayReady;
            default -> false;
        };
        boolean mock = mockPayEnabled && ("mock".equals(channel) || !channelReady);

        Order order = new Order();
        order.setUserId(userId);
        order.setTaskId(0L);
        order.setStoreId(storeId);
        order.setStaffId(staffId);
        order.setCommissionAmount(BigDecimal.ZERO);
        order.setBizType(bizType);
        order.setPlan(bizType);
        order.setPoints(points);
        order.setDays(days);
        order.setTierId(tierId);
        order.setAmount(amount);
        order.setStyleIds("");
        order.setOutTradeNo(generateTradeNo());
        order.setPayChannel(mock ? "mock" : channel);
        order.setPlatform(platform);
        order.setRefundStatus("none");
        order.setRefundAmount(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(mock ? "paid" : "created");
        if (mock) {
            order.setPaidAt(LocalDateTime.now());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount);
        data.put("mock", mock);
        data.put("paid", mock);
        data.put("bizType", bizType);

        if (mock) {
            orderMapper.insert(order);
            settlementService.fulfill(order);
            data.put("orderId", order.getId());
            data.put("outTradeNo", order.getOutTradeNo());
            data.put("quota", pointsService.toMap(pointsService.get(userId)));
            if ("coupon".equals(bizType)) {
                data.put("coupons", couponService.myCoupons().stream()
                        .map(couponService::toMap).toList());
            }
            return Result.ok(data);
        }

        String subject = switch (bizType) {
            case "points" -> "发镜 AI 充值 " + points + " 魅力值";
            case "vip" -> "发镜 AI VIP 包月（30 天）";
            case "vvip" -> "发镜 AI VVIP 永久会员";
            default -> "发镜 AI 周边团购券";
        };

        if ("wx".equals(channel)) {
            if (wxVirtualMode) {
                if (!wxVirtualReady) {
                    throw new BizException("微信虚拟支付参数未配置完整，请在管理端「支付配置」补全");
                }
                String productId = virtualProductOf(cfg, amount);
                Map<String, Object> virtualPay = wechatVirtualPayService.payParamsForProduct(
                        userId, order.getOutTradeNo(), amount, productId);
                order.setPayChannel("wx-virtual");
                orderMapper.insert(order);
                data.put("orderId", order.getId());
                data.put("outTradeNo", order.getOutTradeNo());
                data.put("virtualPay", virtualPay);
                return Result.ok(data);
            }
            if (!wxReady) {
                throw new BizException("微信支付参数未配置完整，请在管理端「支付配置」补全");
            }
            AppUser user = userMapper.selectById(userId);
            orderMapper.insert(order);
            try {
                String prepayId = wechatPayService.createJsapi(
                        order.getOutTradeNo(), amount, subject, user == null ? "" : user.getOpenid());
                order.setPrepayId(prepayId);
                orderMapper.updateById(order);
                data.put("orderId", order.getId());
                data.put("outTradeNo", order.getOutTradeNo());
                data.put("payParams", wechatPayService.payParams(prepayId));
            } catch (BizException e) {
                order.setStatus("closed");
                orderMapper.updateById(order);
                throw e;
            }
            return Result.ok(data);
        }

        if ("douyin".equals(channel)) {
            if (!douyinReady) {
                throw new BizException("抖音支付参数未配置完整，请在管理端「支付配置」补全");
            }
            Map<String, Object> douyinPay = douyinPayService.createOrder(order.getOutTradeNo(), amount,
                    subject, subject, 900);
            orderMapper.insert(order);
            data.put("orderId", order.getId());
            data.put("outTradeNo", order.getOutTradeNo());
            data.put("douyinPay", douyinPay);
            return Result.ok(data);
        }

        if ("alipay".equals(channel)) {
            if (!alipayReady) {
                throw new BizException("支付宝参数未配置完整，请在管理端「支付配置」补全");
            }
            orderMapper.insert(order);
            String form;
            try {
                form = alipayService.buildWapForm(order.getOutTradeNo(), amount, subject);
            } catch (RuntimeException e) {
                order.setStatus("closed");
                orderMapper.updateById(order);
                throw e;
            }
            data.put("orderId", order.getId());
            data.put("outTradeNo", order.getOutTradeNo());
            data.put("payForm", form);
            return Result.ok(data);
        }

        throw new BizException("不支持的支付渠道：" + channel);
    }

    /** 按金额选择充值虚拟道具 */
    private static String virtualProductOf(PayConfig cfg, BigDecimal amount) {
        String productId;
        if (amount.compareTo(new BigDecimal("10.00")) == 0) {
            productId = cfg.getWxVirtualProductP10();
        } else if (amount.compareTo(new BigDecimal("20.00")) == 0) {
            productId = cfg.getWxVirtualProductP20();
        } else if (amount.compareTo(new BigDecimal("29.00")) == 0) {
            productId = cfg.getWxVirtualProductP29();
        } else if (amount.compareTo(new BigDecimal("99.00")) == 0) {
            productId = cfg.getWxVirtualProductP99();
        } else {
            productId = "";
        }
        if (productId == null || productId.isBlank()) {
            throw new BizException("未配置该充值档位的微信虚拟道具 ID");
        }
        return productId;
    }

    /** 微信支付回调：验签 → 解密 → 校验金额 → 幂等置 paid */
    @Operation(summary = "微信支付回调")
    @PostMapping("/notify/wx")
    public ResponseEntity<Map<String, String>> wxNotify(@RequestHeader Map<String, String> headers,
                                                        @RequestBody(required = false) String body) {
        Map<String, String> resp = new LinkedHashMap<>();
        try {
            JsonNode data = wechatPayService.verifyNotify(headers, body == null ? "" : body);
            String tradeState = data.path("trade_state").asText("");
            if (!"SUCCESS".equals(tradeState)) {
                throw new BizException("微信交易状态非成功：" + (tradeState.isBlank() ? "未知" : tradeState));
            }
            String outTradeNo = data.path("out_trade_no").asText("");
            int totalCents = data.path("amount").path("total").asInt(-1);
            Order order = findByTradeNo(outTradeNo);
            if (order == null) {
                throw new BizException("订单不存在：" + outTradeNo);
            }
            // 校验商户号/AppID，防止其他商户的有效签名回调被用来标记本商户订单
            PayConfig cfg = payConfigService.get();
            if (!String.valueOf(data.path("mchid").asText("")).equals(String.valueOf(cfg.getWxMchId()))
                    || !String.valueOf(data.path("appid").asText("")).equals(String.valueOf(cfg.getWxAppid()))) {
                throw new BizException("微信回调商户信息不匹配");
            }
            int expectCents = order.getAmount().multiply(BigDecimal.valueOf(100)).intValueExact();
            if (totalCents != expectCents) {
                throw new BizException("微信回调金额不匹配");
            }
            settlementService.markPaid(order, data.path("transaction_id").asText(""));
            resp.put("code", "SUCCESS");
            resp.put("message", "成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.warn("微信回调处理失败：{}", e.getMessage());
            resp.put("code", "FAIL");
            resp.put("message", e.getMessage() == null ? "处理失败" : e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    /** 支付宝异步通知：RSA2 验签 → 校验 app_id/金额 → 幂等置 paid，返回纯文本 success */
    @Operation(summary = "支付宝异步通知")
    @PostMapping("/notify/alipay")
    public ResponseEntity<String> alipayNotify(@RequestParam Map<String, String> params) {
        try {
            if (!alipayService.verifyNotify(params)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            PayConfig cfg = payConfigService.get();
            String appId = params.get("app_id");
            if (appId == null || !appId.equals(cfg.getAlipayAppId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            String tradeStatus = params.getOrDefault("trade_status", "");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            String outTradeNo = params.getOrDefault("out_trade_no", "");
            Order order = findByTradeNo(outTradeNo);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            BigDecimal notifyAmount = new BigDecimal(params.getOrDefault("total_amount", "0"));
            if (notifyAmount.compareTo(order.getAmount()) != 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
            }
            settlementService.markPaid(order, params.getOrDefault("trade_no", ""));
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.warn("支付宝回调处理失败：{}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
        }
    }

    /** 旧回调路径已废弃，避免被误用绕过验签 */
    @Operation(summary = "旧回调（已废弃）")
    @PostMapping("/notify")
    public void legacyNotify() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口已废弃，请使用渠道回调地址");
    }

    /** 抖音支付回调：SHA1 验签 → 解析 msg → 校验金额 → 幂等置 paid */
    @Operation(summary = "抖音支付回调")
    @PostMapping("/notify/douyin")
    public Map<String, Object> douyinNotify(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            if (body == null) {
                throw new BizException("回调内容为空");
            }
            String timestamp = str(body.get("timestamp"));
            String nonce = str(body.get("nonce"));
            Object rawMsg = body.get("msg");
            String msgJson = rawMsg instanceof String text ? text
                    : objectMapper.writeValueAsString(rawMsg);
            String signature = str(body.get("msg_signature"));
            if (!douyinPayService.verifyNotify(timestamp, nonce, msgJson, signature)) {
                throw new BizException("抖音回调验签失败");
            }
            JsonNode msg = objectMapper.readTree(msgJson);
            Order order = findByTradeNo(msg.path("out_order_no").asText(""));
            if (order == null) {
                throw new BizException("抖音订单不存在");
            }
            // 只接受支付成功的通知，避免非成功事件（关闭/失败）被误记成已支付
            String tradeStatus = msg.path("status").asText("");
            if (!tradeStatus.isBlank() && !"SUCCESS".equalsIgnoreCase(tradeStatus)) {
                throw new BizException("抖音交易状态非成功：" + tradeStatus);
            }
            // 校验 app_id，防止其他应用的合法签名回调被用来标记本应用订单
            PayConfig cfg = payConfigService.get();
            String notifyAppId = msg.path("app_id").asText("");
            if (!notifyAppId.isBlank() && !notifyAppId.equals(cfg.getDouyinAppId())) {
                throw new BizException("抖音回调 app_id 不匹配");
            }
            long totalCents = msg.path("total_amount").asLong(-1);
            if (totalCents >= 0 && BigDecimal.valueOf(totalCents)
                    .compareTo(order.getAmount().multiply(BigDecimal.valueOf(100))) != 0) {
                throw new BizException("抖音回调金额不匹配");
            }
            settlementService.markPaid(order, msg.path("trade_no").asText(msg.path("order_id").asText("")));
            resp.put("err_no", 0);
            resp.put("err_tips", "success");
        } catch (Exception e) {
            log.warn("抖音回调处理失败：{}", e.getMessage());
            resp.put("err_no", 1);
            resp.put("err_tips", e.getMessage() == null ? "处理失败" : e.getMessage());
        }
        return resp;
    }

    /** 小红书回调占位：官方支付接口需开放平台文档与资质联调 */
    @Operation(summary = "小红书支付回调（待接入）")
    @PostMapping("/notify/xhs")
    public ResponseEntity<String> xhsNotify() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("xhs payment not configured");
    }

    /** 微信虚拟支付：客户端支付成功后由服务端查单确认并入账 */
    @Operation(summary = "确认微信虚拟支付结果")
    @PostMapping("/{id:\\d+}/virtual-confirm")
    public Result<Map<String, Object>> virtualConfirm(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        Long userId = UserContext.require();
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!"paid".equals(order.getStatus())) {
            WechatVirtualPayService.VirtualOrder snapshot =
                    wechatVirtualPayService.queryOrder(userId, order.getOutTradeNo());
            if (snapshot.paid) {
                BigDecimal paidYuan = BigDecimal.valueOf(snapshot.paidFee).divide(BigDecimal.valueOf(100));
                if (paidYuan.compareTo(order.getAmount()) != 0) {
                    throw new BizException("虚拟支付金额与订单金额不一致");
                }
                String tradeNo = snapshot.wxOrderId.isBlank() ? order.getOutTradeNo() : snapshot.wxOrderId;
                settlementService.markPaid(order, tradeNo);
            }
        }
        if ("paid".equals(order.getStatus())) {
            // 重复确认时也补发发货通知：上次可能已入账但通知微信失败
            wechatVirtualPayService.notifyProvided(order.getOutTradeNo());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paid", "paid".equals(order.getStatus()));
        data.put("orderId", order.getId());
        return Result.ok(data);
    }

    /** JSAPI 查单兜底：回调丢失时主动向微信确认订单并补入账 */
    @Operation(summary = "主动同步支付结果（微信 JSAPI 查单）")
    @PostMapping("/{id:\\d+}/sync")
    public Result<Map<String, Object>> sync(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        Long userId = UserContext.require();
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!"paid".equals(order.getStatus()) && "wx".equals(order.getPayChannel())) {
            PayConfig cfg = payConfigService.get();
            if (payConfigService.wxReady(cfg)) {
                JsonNode payload = wechatPayService.queryOrder(order.getOutTradeNo());
                if (!String.valueOf(payload.path("mchid").asText("")).equals(String.valueOf(cfg.getWxMchId()))
                        || !String.valueOf(payload.path("appid").asText("")).equals(String.valueOf(cfg.getWxAppid()))) {
                    throw new BizException("微信订单商户信息不匹配");
                }
                int totalCents = payload.path("amount").path("total").asInt(-1);
                int expectCents = order.getAmount().multiply(BigDecimal.valueOf(100)).intValueExact();
                if ("SUCCESS".equals(payload.path("trade_state").asText("")) && totalCents == expectCents) {
                    settlementService.markPaid(order, payload.path("transaction_id").asText(""));
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paid", "paid".equals(order.getStatus()));
        data.put("orderId", order.getId());
        return Result.ok(data);
    }

    @Operation(summary = "查询订单状态")
    @GetMapping("/{id:\\d+}")
    public Result<Map<String, Object>> status(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        Long userId = UserContext.require();
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("status", order.getStatus());
        data.put("paid", "paid".equals(order.getStatus()));
        data.put("amount", order.getAmount());
        data.put("styleIds", splitIds(order.getStyleIds()));
        data.put("payChannel", order.getPayChannel());
        data.put("outTradeNo", order.getOutTradeNo());
        data.put("paidAt", order.getPaidAt());
        data.put("refundStatus", order.getRefundStatus());
        data.put("refundAmount", order.getRefundAmount());
        data.put("bizType", order.getBizType());
        data.put("points", order.getPoints());
        data.put("days", order.getDays());
        data.put("tierId", order.getTierId());
        return Result.ok(data);
    }

    /* ---------------- 内部实现 ---------------- */

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String normalizePlatform(String raw) {
        String p = raw == null ? "" : raw.trim().toLowerCase();
        return switch (p) {
            case "h5", "web" -> "h5";
            case "mp-weixin", "weixin", "wx" -> "mp-weixin";
            case "mp-toutiao", "toutiao", "douyin", "tt" -> "mp-toutiao";
            case "mp-xhs", "xhs", "xiaohongshu" -> "mp-xhs";
            default -> "";
        };
    }

    private Order findByTradeNo(String outTradeNo) {
        if (outTradeNo == null || outTradeNo.isBlank()) {
            return null;
        }
        return orderMapper.selectOne(new QueryWrapper<Order>().eq("out_trade_no", outTradeNo).last("limit 1"));
    }

    private String generateTradeNo() {
        return "FJ" + LocalDateTime.now().format(NO_FORMAT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private List<String> splitIds(String ids) {
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
