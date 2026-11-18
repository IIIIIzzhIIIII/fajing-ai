package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AppUser;
import com.fajing.entity.CouponTier;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Order;
import com.fajing.entity.PayConfig;
import com.fajing.entity.PointsPackage;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.service.CouponService;
import com.fajing.service.GenerationService;
import com.fajing.service.GenerationProcessor;
import com.fajing.service.PayConfigService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单：按渠道下单 → 渠道回调验签 → 查询。
 * 支付成功的唯一准入口径是渠道异步通知（dev 的 mock 开关除外）；解锁只在这里写入。
 */
@Tag(name = "订单", description = "创建订单、支付回调与查询；解锁以 user_unlock 为准")
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final GenerationTaskMapper taskMapper;
    private final AppUserMapper userMapper;
    private final GenerationService generationService;
    private final GenerationProcessor generationProcessor;
    private final PayConfigService payConfigService;
    private final WechatPayService wechatPayService;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final DouyinPayService douyinPayService;
    private final AlipayService alipayService;
    private final PointsService pointsService;
    private final PointsPackageService pointsPackageService;
    private final CouponService couponService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** mock 支付仅在非生产环境开启；生产必须接真实支付 */
    @Value("${pay.mock-enabled:false}")
    private boolean mockPayEnabled;

    /** 未支付订单复用窗口：微信 prepay_id 有效期 2 小时，这里保守取 30 分钟 */
    private static final int REUSE_WINDOW_MINUTES = 30;

    /** 单张解锁原价 */
    private static final BigDecimal UNIT_PRICE = new BigDecimal("6.00");
    /** 首单「全部解锁」5 折后的每款单价 */
    private static final BigDecimal FIRST_ALL_UNIT_PRICE = new BigDecimal("3.00");

    /** 同一用户的下单请求按用户串行，避免并发请求同时查不到未支付订单而重复插入 */
    private static final Object[] CREATE_LOCKS = new Object[64];

    static {
        for (int i = 0; i < CREATE_LOCKS.length; i++) {
            CREATE_LOCKS[i] = new Object();
        }
    }

    @Operation(summary = "创建解锁订单")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.require();
        String bizType = str(body.get("bizType"));
        // 充值 / VIP / 团购券走独立商品订单，与解锁订单互不影响
        if (!bizType.isBlank() && !"unlock".equals(bizType)) {
            return createBizOrder(bizType, body, userId);
        }
        List<String> styleIds = styleIdsOf(body);
        Long taskId = taskIdOf(body);
        String plan = body.get("plan") == null ? "all" : String.valueOf(body.get("plan"));

        if (styleIds.isEmpty()) {
            throw new BizException("请选择要解锁的款式");
        }
        if (!"one".equals(plan) && !"all".equals(plan)) {
            throw new BizException("不支持的解锁套餐");
        }
        if ("one".equals(plan) && styleIds.size() != 1) {
            throw new BizException("单张解锁只能选择 1 款发型");
        }
        if (taskId > 0) {
            GenerationTask task = taskMapper.selectById(taskId);
            if (task == null || !Long.valueOf(userId).equals(task.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
            }
            // 只能解锁本任务内的款式，避免构造请求解锁无关款式或跨任务复用
            if (!splitIds(task.getStyleIds()).containsAll(styleIds)) {
                throw new BizException("解锁款式与本次生成任务不匹配");
            }
            // 已解锁款式不计入本次订单，避免用户为已拥有内容重复付费
            List<String> locked = new ArrayList<>();
            for (String styleId : styleIds) {
                if (!generationService.isUnlocked(userId, taskId, styleId)) {
                    locked.add(styleId);
                }
            }
            styleIds = locked;
            if (styleIds.isEmpty()) {
                throw new BizException("所选内容已解锁，无需重复支付");
            }
        }
        // 仅 1 款待解锁时不提供「本次全部 N 款」套餐，避免用 3 元买走单张 6 元的款式
        if ("all".equals(plan) && styleIds.size() < 2) {
            throw new BizException("仅 1 款待解锁，请使用单张解锁");
        }

        PayConfig cfg = payConfigService.get();
        boolean wxReady = payConfigService.wxReady(cfg);
        boolean wxVirtualReady = payConfigService.wxVirtualReady(cfg);
        boolean douyinReady = payConfigService.douyinReady(cfg);
        boolean xhsReady = payConfigService.xhsReady(cfg);
        boolean alipayReady = payConfigService.alipayReady(cfg);
        boolean wxVirtualMode = "virtual".equalsIgnoreCase(cfg.getWxPayMode());
        String platform = normalizePlatform(body.get("platform") == null ? "" : String.valueOf(body.get("platform")));
        String channel = body.get("channel") == null ? "" : String.valueOf(body.get("channel")).trim();
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
            } else if ("mp-xhs".equals(platform)) {
                channel = "xhs";
            } else if (wxReady) {
                channel = "wx";
            } else if (alipayReady) {
                channel = "alipay";
            }
        }

        boolean channelReady = switch (channel) {
            case "wx" -> wxReady || wxVirtualReady;
            case "douyin" -> douyinReady;
            case "xhs" -> xhsReady;
            case "alipay" -> alipayReady;
            default -> false;
        };
        boolean mock = mockPayEnabled && ("mock".equals(channel) || !channelReady);

        if (!mock && "mp-xhs".equals(platform)) {
            throw new BizException("小红书小程序支付接口需按开放平台文档联调后启用");
        }

        synchronized (lockOf(userId)) {
            // 首单优惠：每个用户首次「全部解锁」享 5 折，之后恢复原价；金额只以服务端计算为准
            boolean firstAllDiscount = "all".equals(plan) && firstAllDiscountAvailable(userId);
            BigDecimal amount = priceOf(plan, styleIds.size(), firstAllDiscount);

            // 幂等：同用户 + 同任务 + 同一批款式 + 同渠道同金额，复用窗口内未支付订单并重新下发支付参数
            Order reused = (mock || wxVirtualMode || "douyin".equals(channel) || "xhs".equals(channel))
                    ? null : findReusableOrder(userId, taskId, styleIds, amount, channel);
            if (reused != null) {
                Map<String, Object> reusedData = new LinkedHashMap<>();
                reusedData.put("amount", reused.getAmount());
                reusedData.put("mock", false);
                reusedData.put("paid", false);
                reusedData.put("reused", true);
                reusedData.put("orderId", reused.getId());
                reusedData.put("outTradeNo", reused.getOutTradeNo());
                if ("wx".equals(channel)) {
                    reusedData.put("payParams", wechatPayService.payParams(reused.getPrepayId()));
                } else {
                    reusedData.put("payForm", alipayService.buildWapForm(
                            reused.getOutTradeNo(), reused.getAmount(), "发镜 AI 解锁高清图"));
                }
                return Result.ok(reusedData);
            }

            Order order = new Order();
            order.setUserId(userId);
            order.setTaskId(taskId);
            order.setPlan(plan);
            order.setAmount(amount);
            order.setStyleIds(String.join(",", styleIds));
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
            data.put("firstAllDiscount", firstAllDiscount);
            data.put("mock", mock);
            data.put("paid", mock);

            if (mock) {
                orderMapper.insert(order);
                unlockAll(userId, styleIds, taskId);
                // 本地模拟支付同样按需生成付费款，保证流程与真实支付一致
                if (taskId > 0) {
                    generationProcessor.generatePaidStyles(taskId, styleIds);
                }
                data.put("orderId", order.getId());
                data.put("outTradeNo", order.getOutTradeNo());
                return Result.ok(data);
            }

            if ("wx".equals(channel)) {
                if (wxVirtualMode) {
                    if (!wxVirtualReady) {
                        throw new BizException("微信虚拟支付参数未配置完整，请在管理端「支付配置」补全");
                    }
                    Map<String, Object> virtualPay = wechatVirtualPayService.payParams(
                            userId, order.getOutTradeNo(), amount, plan, styleIds.size());
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
                            order.getOutTradeNo(), amount, "发镜 AI 解锁高清图", user == null ? "" : user.getOpenid());
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
                        "发镜 AI 解锁高清图", "解锁 " + styleIds.size() + " 款发型高清图", 900);
                orderMapper.insert(order);
                data.put("orderId", order.getId());
                data.put("outTradeNo", order.getOutTradeNo());
                data.put("douyinPay", douyinPay);
                return Result.ok(data);
            }

            if ("xhs".equals(channel)) {
                throw new BizException("小红书小程序支付接口需按开放平台文档联调后启用");
            }

            if ("alipay".equals(channel)) {
                if (!alipayReady) {
                    throw new BizException("支付宝参数未配置完整，请在管理端「支付配置」补全");
                }
                orderMapper.insert(order);
                String form;
                try {
                    form = alipayService.buildWapForm(order.getOutTradeNo(), amount, "发镜 AI 解锁高清图");
                } catch (RuntimeException e) {
                    // 与微信分支保持一致：下单失败即关闭订单，避免残留 created 订单影响对账
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
    }

    /** 充值 / VIP / VVIP / 团购券下单：金额与权益只以服务端档位为准 */
    private Result<Map<String, Object>> createBizOrder(String bizType, Map<String, Object> body, Long userId) {
        PayConfig cfg = payConfigService.get();
        BigDecimal amount;
        int points = 0;
        int days = 0;
        Long tierId = 0L;
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
            fulfill(order);
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

    /** 支付成功发货：按订单业务类型发放权益，重复回调不重复发放 */
    private void fulfill(Order order) {
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

    /** 兼容老前端：等同 mock 下单；生产环境关闭 */
    @Operation(summary = "兼容接口：mock 下单（生产不可用）")
    @PostMapping("/mock")
    public Result<Map<String, Object>> mockPay(@RequestBody Map<String, Object> body) {
        if (!mockPayEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口不存在");
        }
        body.put("channel", "mock");
        return create(body);
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
            markPaid(order, data.path("transaction_id").asText(""));
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
            markPaid(order, params.getOrDefault("trade_no", ""));
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
            markPaid(order, msg.path("trade_no").asText(msg.path("order_id").asText("")));
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
    @PostMapping("/{id}/virtual-confirm")
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
                markPaid(order, tradeNo);
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

    /** 按运行端下发可用支付方式：H5 支付宝、微信小程序微信支付、抖音/小红书预留 */
    @Operation(summary = "按运行端查询支付方式")
    @GetMapping("/pay-methods")
    public Result<Map<String, Object>> payMethods(@RequestParam(required = false) String platform) {
        String raw = platform == null ? "" : platform.trim();
        String p = raw.isBlank() ? "h5" : normalizePlatform(raw);
        if (p.isBlank()) {
            throw new BizException("不支持的运行端：" + raw);
        }
        PayConfig cfg = payConfigService.get();
        boolean wxReady = payConfigService.wxReady(cfg);
        boolean wxVirtualReady = payConfigService.wxVirtualReady(cfg);
        boolean douyinReady = payConfigService.douyinReady(cfg);
        boolean xhsReady = payConfigService.xhsReady(cfg);
        boolean alipayReady = payConfigService.alipayReady(cfg);
        boolean wxVirtualMode = "virtual".equalsIgnoreCase(cfg.getWxPayMode());
        List<Map<String, Object>> channels = new ArrayList<>();
        switch (p) {
            case "h5" -> channels.add(payMethod("alipay", "支付宝", alipayReady,
                    "支付宝参数未配置完整，请在管理端「支付参数配置」补全"));
            case "mp-weixin" -> {
                if (wxVirtualMode) {
                    channels.add(payMethod("wx", "微信虚拟支付", wxVirtualReady,
                            "微信虚拟支付参数未配置完整，请在管理端「支付参数配置」补全"));
                } else {
                    channels.add(payMethod("wx", "微信支付", wxReady,
                            "微信支付参数未配置完整，请在管理端「支付参数配置」补全"));
                }
            }
            case "mp-toutiao" -> channels.add(payMethod("douyin", "抖音收银台（含支付宝 / 微信 / 抖音支付）",
                    douyinReady, "抖音担保支付参数未配置完整，请在管理端「支付参数配置」补全"));
            case "mp-xhs" -> channels.add(payMethod("xhs", "小红书收银台（支付方式以平台为准）",
                    xhsReady, "小红书支付接口需官方文档与资质联调后启用"));
            default -> throw new BizException("不支持的运行端：" + raw);
        }
        boolean anyReady = wxReady || wxVirtualReady || douyinReady || xhsReady || alipayReady;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("platform", p);
        data.put("channels", channels);
        data.put("mock", mockPayEnabled && !anyReady);
        return Result.ok(data);
    }

    @Operation(summary = "查询解锁单价与首单优惠状态")
    @GetMapping("/pricing")
    public Result<Map<String, Object>> pricing() {
        Long userId = UserContext.get();
        boolean firstAllDiscount = userId != null && firstAllDiscountAvailable(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("oneUnitPrice", UNIT_PRICE);
        data.put("listUnitPrice", UNIT_PRICE);
        data.put("allUnitPrice", firstAllDiscount ? FIRST_ALL_UNIT_PRICE : UNIT_PRICE);
        data.put("firstAllDiscount", firstAllDiscount);
        return Result.ok(data);
    }

    @Operation(summary = "查询订单状态")
    @GetMapping("/{id}")
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

    /** 首单优惠判定：该用户从未有过已支付且未退款的「全部解锁」订单 */
    private boolean firstAllDiscountAvailable(Long userId) {
        Long paidAll = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("user_id", userId)
                .eq("plan", "all")
                .eq("status", "paid")
                // 已退款/退款中的订单不算消耗首单资格；refund_status 为空的历史单按未退款处理
                .and(w -> w.isNull("refund_status").or().notIn("refund_status", "refunded", "refunding")));
        return paidAll == null || paidAll == 0;
    }

    private static BigDecimal priceOf(String plan, int count, boolean firstAllDiscount) {
        if ("one".equals(plan)) {
            return UNIT_PRICE;
        }
        BigDecimal unit = firstAllDiscount ? FIRST_ALL_UNIT_PRICE : UNIT_PRICE;
        return unit.multiply(BigDecimal.valueOf(count)).setScale(2);
    }

    private static Map<String, Object> payMethod(String code, String label, boolean ready, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("label", label);
        item.put("ready", ready);
        item.put("reason", ready ? "" : reason);
        return item;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
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

    /** 查找可复用的未支付订单：唯一键是「用户 + 任务 + 款式集合 + 渠道 + 金额」，且在下单复用窗口内 */
    private Order findReusableOrder(Long userId, Long taskId, List<String> styleIds,
                                    BigDecimal amount, String channel) {
        if (!"wx".equals(channel) && !"alipay".equals(channel)) {
            return null;
        }
        List<Order> candidates = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("user_id", userId)
                .eq("task_id", taskId)
                .eq("status", "created")
                .eq("pay_channel", channel)
                .eq("amount", amount)
                .ge("created_at", LocalDateTime.now().minusMinutes(REUSE_WINDOW_MINUTES))
                .orderByDesc("id")
                .last("limit 5"));
        Set<String> wanted = new HashSet<>(styleIds);
        for (Order candidate : candidates) {
            if (!wanted.equals(new HashSet<>(splitIds(candidate.getStyleIds())))) {
                continue;
            }
            // 微信复用依赖已缓存的 prepay_id；缺失说明上次下单未完成，交给新订单处理
            if ("wx".equals(channel)
                    && (candidate.getPrepayId() == null || candidate.getPrepayId().isBlank())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static Object lockOf(Long userId) {
        return CREATE_LOCKS[Math.floorMod(userId, CREATE_LOCKS.length)];
    }

    /** 幂等：已支付订单不重复置状态；解锁类仍补齐解锁，修复上次回调「置 paid 成功、解锁失败」的断链 */
    private void markPaid(Order order, String transactionId) {
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

    private void unlockAll(Long userId, List<String> styleIds, Long taskId) {
        for (String id : styleIds) {
            generationService.unlock(userId, id, taskId);
        }
    }

    private String generateTradeNo() {
        return "FJ" + LocalDateTime.now().format(NO_FORMAT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    @SuppressWarnings("unchecked")
    private List<String> styleIdsOf(Map<String, Object> body) {
        Object raw = body.get("styleIds");
        List<String> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (raw instanceof List<?> items) {
            for (Object o : items) {
                if (o == null) {
                    continue;
                }
                String id = String.valueOf(o).trim();
                // 款式 ID 会以逗号拼接落库并用于解锁，必须限制字符集与长度，避免注入与超长写入
                if (!id.matches("[A-Za-z0-9_-]{1,32}")) {
                    throw new BizException("解锁内容不合法");
                }
                if (seen.add(id)) {
                    list.add(id);
                }
            }
        }
        if (list.isEmpty()) {
            throw new BizException("缺少解锁内容");
        }
        if (list.size() > 4) {
            throw new BizException("解锁数量不正确");
        }
        return list;
    }

    private Long taskIdOf(Map<String, Object> body) {
        Object raw = body.get("taskId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new BizException("任务标识不合法");
        }
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
