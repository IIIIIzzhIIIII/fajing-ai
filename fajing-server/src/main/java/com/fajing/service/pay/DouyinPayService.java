package com.fajing.service.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.PayConfig;
import com.fajing.service.PayConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抖音小程序担保支付：服务端预下单 → 客户端 tt.pay 收银台 → 回调验签入账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinPayService {

    private static final String PROD_URL = "https://developer.toutiao.com/api/apps/ecpay/v1/create_order";
    private static final String SANDBOX_URL = "https://open-sandbox.douyin.com/api/apps/ecpay/v1/create_order";

    private final PayConfigService payConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 预下单，返回 order_id / order_token，供客户端 tt.pay 使用 */
    public Map<String, Object> createOrder(String outTradeNo, BigDecimal amountYuan,
                                           String subject, String bodyText, int validSeconds) {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.douyinReady(cfg)) {
            throw new BizException("抖音支付参数未配置完整，请在管理端「支付配置」补全");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("app_id", cfg.getDouyinAppId());
        params.put("out_order_no", outTradeNo);
        params.put("total_amount", amountYuan.multiply(BigDecimal.valueOf(100)).intValueExact());
        params.put("subject", subject);
        params.put("body", bodyText);
        params.put("valid_time", validSeconds);
        params.put("notify_url", cfg.getDouyinNotifyUrl());
        params.put("cp_extra", "order:" + outTradeNo);
        params.put("sign", sign(params, payConfigService.douyinSalt(cfg)));

        String url = Integer.valueOf(1).equals(cfg.getDouyinSandbox()) ? SANDBOX_URL : PROD_URL;
        try {
            String json = objectMapper.writeValueAsString(params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            int errNo = root.path("err_no").asInt(-1);
            if (errNo != 0) {
                throw new BizException("抖音预下单失败：" + root.path("err_tips").asText("") + "（" + errNo + "）");
            }
            JsonNode data = root.path("data");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderId", data.path("order_id").asText(""));
            result.put("orderToken", data.path("order_token").asText(""));
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("抖音预下单请求失败：" + e.getMessage());
        }
    }

    /** 回调验签：sha1(sort(token, timestamp, nonce, msg).join("")) */
    public boolean verifyNotify(String timestamp, String nonce, String msg, String signature) {
        PayConfig cfg = payConfigService.get();
        String token = payConfigService.douyinToken(cfg);
        if (token == null || token.isBlank() || timestamp == null || nonce == null
                || msg == null || signature == null) {
            return false;
        }
        List<String> parts = new ArrayList<>(List.of(token, timestamp, nonce, msg));
        parts.sort(Comparator.naturalOrder());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String sign(Map<String, Object> params, String salt) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if ("app_id".equals(key) || "thirdparty_id".equals(key) || "sign".equals(key)
                    || "other_settle_params".equals(key)) {
                continue;
            }
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            if (value.isEmpty() || "null".equals(value)) {
                continue;
            }
            values.add(value);
        }
        values.add(salt == null ? "" : salt);
        values.sort(Comparator.naturalOrder());
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(String.join("&", values).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("抖音支付签名失败");
        }
    }
}
