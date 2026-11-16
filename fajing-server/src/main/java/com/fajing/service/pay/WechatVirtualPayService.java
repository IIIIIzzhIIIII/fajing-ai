package com.fajing.service.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.AppUser;
import com.fajing.entity.PayConfig;
import com.fajing.mapper.AppUserMapper;
import com.fajing.service.PayConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序虚拟支付（道具直购）：
 * 服务端生成 signData + paySig + signature，客户端调 wx.requestVirtualPayment。
 * 支付结果以服务端 query_order 查询为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatVirtualPayService {

    private static final String API_BASE = "https://api.weixin.qq.com";
    private static final String URI_VIRTUAL_PAY = "requestVirtualPayment";
    private static final String URI_QUERY_ORDER = "query_order";

    private final PayConfigService payConfigService;
    private final AppUserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    private volatile String accessToken = "";
    private volatile long accessTokenAt = 0;

    /** 生成客户端拉起虚拟支付所需参数 */
    public Map<String, Object> payParams(Long userId, String outTradeNo, BigDecimal amount,
                                         String plan, int quantity) {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxVirtualReady(cfg)) {
            throw new BizException("微信虚拟支付参数未配置完整，请在管理端「支付配置」补全 offerId 与 AppKey");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw new BizException("缺少用户 openid，无法发起微信虚拟支付");
        }
        if (user.getSessionKey() == null || user.getSessionKey().isBlank()) {
            throw new BizException("登录态已过期，请退出重新进入小程序后支付");
        }
        boolean single = "one".equals(plan);
        int expectCents = toCents(amount);
        int unitPrice;
        int buyQuantity;
        String productId;
        if (single) {
            // 单张解锁固定 ¥6，购买 1 份 ¥6 道具
            unitPrice = 600;
            buyQuantity = 1;
            productId = cfg.getWxVirtualProductOne();
        } else {
            // 全部解锁按款数购买：首单 5 折用 ¥3 道具，优惠用完后回到 ¥6 道具 × 款数
            int count = Math.max(1, quantity);
            buyQuantity = count;
            if (expectCents == 300 * count) {
                unitPrice = 300;
                productId = cfg.getWxVirtualProductAll();
            } else if (expectCents == 600 * count) {
                unitPrice = 600;
                productId = cfg.getWxVirtualProductOne();
            } else {
                throw new BizException("订单金额与虚拟道具价格不匹配，请检查道具定价");
            }
        }
        if (productId == null || productId.isBlank()) {
            throw new BizException(single ? "未配置单张解锁虚拟道具 ID" : "未配置全部解锁虚拟道具 ID");
        }
        if (expectCents != unitPrice * buyQuantity) {
            throw new BizException("订单金额与虚拟道具价格不匹配，请检查道具定价");
        }
        int env = cfg.getWxVirtualEnv() == null ? 0 : cfg.getWxVirtualEnv();

        Map<String, Object> signMap = new LinkedHashMap<>();
        signMap.put("offerId", cfg.getWxVirtualOfferId());
        signMap.put("buyQuantity", buyQuantity);
        signMap.put("env", env);
        signMap.put("currencyType", "CNY");
        signMap.put("productId", productId);
        signMap.put("goodsPrice", unitPrice);
        signMap.put("outTradeNo", outTradeNo);
        signMap.put("attach", "order:" + outTradeNo);
        signMap.put("mode", "short_series_goods");

        String signData;
        try {
            signData = objectMapper.writeValueAsString(signMap);
        } catch (Exception e) {
            throw new BizException("虚拟支付参数序列化失败");
        }
        String appKey = payConfigService.wxVirtualAppkey(cfg);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signData", signData);
        result.put("paySig", hmacHex(appKey, URI_VIRTUAL_PAY + "&" + signData));
        result.put("signature", hmacHex(user.getSessionKey(), signData));
        result.put("mode", "short_series_goods");
        return result;
    }

    /** 虚拟支付连通性：参数齐全时用 access_token 验证 AppID/AppSecret 是否可用 */
    public Map<String, Object> verifyConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxVirtualReady(cfg)) {
            result.put("ok", false);
            result.put("msg", "虚拟支付参数不完整：需开启微信支付，并填写 OfferID、AppKey、¥6 道具 ID、¥3 道具 ID");
            return result;
        }
        try {
            accessToken();
            result.put("ok", true);
            result.put("msg", "参数齐全，AppID / AppSecret 可换取 access_token");
        } catch (BizException e) {
            result.put("ok", false);
            result.put("msg", e.getMessage());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("msg", "测试失败：" + e.getMessage());
        }
        return result;
    }

    /** 虚拟支付订单快照：金额单位为分 */
    public static class VirtualOrder {
        public boolean paid;
        public int paidFee;
        public int leftFee;
        public int status;
        public String wxOrderId;
    }

    /** 服务端查单：支付结果与剩余可退金额都以微信返回为准 */
    public VirtualOrder queryOrder(Long userId, String outTradeNo) {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxVirtualReady(cfg)) {
            throw new BizException("微信虚拟支付参数未配置完整，请在管理端「支付参数配置」补全");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw new BizException("缺少用户 openid，无法查询虚拟支付订单");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", user.getOpenid());
        body.put("env", cfg.getWxVirtualEnv() == null ? 0 : cfg.getWxVirtualEnv());
        body.put("order_id", outTradeNo);
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException("虚拟支付查单参数序列化失败");
        }
        JsonNode root = postXpay(cfg, URI_QUERY_ORDER, bodyJson, true);
        if (root.path("errcode").asInt(-1) != 0) {
            throw new BizException("虚拟支付查单失败：" + root.path("errmsg").asText(""));
        }
        JsonNode order = root.path("order");
        VirtualOrder snapshot = new VirtualOrder();
        snapshot.status = order.path("status").asInt(0);
        snapshot.paid = snapshot.status >= 2 && snapshot.status <= 4;
        snapshot.paidFee = order.path("paid_fee").asInt(0);
        snapshot.leftFee = order.path("left_fee").asInt(0);
        snapshot.wxOrderId = order.path("wx_order_id").asText("");
        return snapshot;
    }

    /** 通知微信已发货；失败只记录日志，不阻断本地解锁 */
    public boolean notifyProvided(String outTradeNo) {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxVirtualReady(cfg)) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", outTradeNo);
        body.put("env", cfg.getWxVirtualEnv() == null ? 0 : cfg.getWxVirtualEnv());
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            JsonNode root = postXpay(cfg, "notify_provide_goods", bodyJson, false);
            if (root.path("errcode").asInt(-1) != 0) {
                log.warn("微信虚拟支付通知发货失败：{}", root.toString());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("微信虚拟支付通知发货异常：{}", e.getMessage());
            return false;
        }
    }

    /** 启动虚拟支付退款任务，返回退款单号；最终状态需再查单确认 */
    public String refund(Long userId, String outTradeNo, BigDecimal amountYuan, String reason) {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxVirtualReady(cfg)) {
            throw new BizException("微信虚拟支付参数未配置完整，无法退款");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw new BizException("缺少用户 openid，无法退款");
        }
        VirtualOrder snapshot = queryOrder(userId, outTradeNo);
        if (!snapshot.paid) {
            throw new BizException("订单未支付，无法退款");
        }
        int refundFee = toCents(amountYuan);
        if (refundFee <= 0 || refundFee > snapshot.leftFee) {
            throw new BizException("退款金额超出剩余可退金额");
        }
        String refundOrderId = "R" + System.currentTimeMillis()
                + String.format("%03d", System.nanoTime() % 1000);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", user.getOpenid());
        body.put("order_id", outTradeNo);
        body.put("refund_order_id", refundOrderId);
        body.put("left_fee", snapshot.leftFee);
        body.put("refund_fee", refundFee);
        body.put("biz_meta", "order:" + outTradeNo + ",reason:" + (reason == null ? "" : reason));
        body.put("refund_reason", "5");
        body.put("req_from", "1");
        body.put("env", cfg.getWxVirtualEnv() == null ? 0 : cfg.getWxVirtualEnv());
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            JsonNode root = postXpay(cfg, "refund_order", bodyJson, true);
            if (root.path("errcode").asInt(-1) != 0) {
                throw new BizException("微信虚拟退款失败：" + root.path("errmsg").asText(""));
            }
            return root.path("refund_order_id").asText(refundOrderId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信虚拟退款请求失败：" + e.getMessage());
        }
    }

    private JsonNode postXpay(PayConfig cfg, String uri, String bodyJson, boolean withPaySig) {
        try {
            String token = accessToken();
            StringBuilder url = new StringBuilder(API_BASE).append("/xpay/").append(uri)
                    .append("?access_token=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));
            if (withPaySig) {
                String paySig = hmacHex(payConfigService.wxVirtualAppkey(cfg), uri + "&" + bodyJson);
                url.append("&pay_sig=").append(paySig);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return objectMapper.readTree(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信虚拟支付接口请求失败：" + e.getMessage());
        }
    }

    private String accessToken() {
        if (!accessToken.isBlank() && System.currentTimeMillis() - accessTokenAt < 100 * 60_000L) {
            return accessToken;
        }
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BizException("未配置微信小程序 AppID / AppSecret，无法查询虚拟支付订单");
        }
        try {
            String url = API_BASE + "/cgi-bin/token?grant_type=client_credential&appid="
                    + URLEncoder.encode(appid, StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            String token = root.path("access_token").asText("");
            if (token.isBlank()) {
                throw new BizException("获取微信 access_token 失败：" + root.path("errmsg").asText(""));
            }
            accessToken = token;
            accessTokenAt = System.currentTimeMillis();
            return token;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("获取微信 access_token 异常：" + e.getMessage());
        }
    }

    private static int toCents(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).intValueExact();
    }

    private static String hmacHex(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("虚拟支付签名失败");
        }
    }
}
