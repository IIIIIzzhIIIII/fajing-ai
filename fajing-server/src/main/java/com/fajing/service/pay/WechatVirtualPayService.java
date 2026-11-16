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
        String productId = single ? cfg.getWxVirtualProductOne() : cfg.getWxVirtualProductAll();
        if (productId == null || productId.isBlank()) {
            throw new BizException(single ? "未配置单张解锁虚拟道具 ID" : "未配置全部解锁虚拟道具 ID");
        }
        int unitPrice = single ? 600 : 300;
        int buyQuantity = single ? 1 : Math.max(1, quantity);
        int expectCents = toCents(amount);
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

    /** 查询虚拟支付订单是否已支付，返回微信内部单号；未支付返回空串 */
    public String queryPaidTradeNo(Long userId, String outTradeNo) {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxVirtualReady(cfg)) {
            return "";
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            return "";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", user.getOpenid());
        body.put("env", cfg.getWxVirtualEnv() == null ? 0 : cfg.getWxVirtualEnv());
        body.put("order_id", outTradeNo);
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "";
        }
        String paySig = hmacHex(payConfigService.wxVirtualAppkey(cfg), URI_QUERY_ORDER + "&" + bodyJson);
        try {
            String token = accessToken();
            String url = API_BASE + "/xpay/" + URI_QUERY_ORDER + "?access_token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&pay_sig=" + paySig;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            if (root.path("errcode").asInt(-1) != 0) {
                log.warn("微信虚拟支付查单失败：{}", resp.body());
                return "";
            }
            JsonNode order = root.path("order");
            int status = order.path("status").asInt(0);
            if (status >= 2 && status <= 4) {
                String wxOrderId = order.path("wx_order_id").asText("");
                return wxOrderId.isBlank() ? outTradeNo : wxOrderId;
            }
            return "";
        } catch (Exception e) {
            log.warn("微信虚拟支付查单异常：{}", e.getMessage());
            return "";
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
