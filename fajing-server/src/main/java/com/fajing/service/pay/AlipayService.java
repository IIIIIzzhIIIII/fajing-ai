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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付宝手机网站支付（alipay.trade.wap.pay）：RSA2 签名、异步通知验签、退款与探活。
 * 仅 H5 端使用；小程序端不提供支付宝通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PayConfigService payConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 生成自动提交的支付表单 HTML */
    public String buildWapForm(String outTradeNo, BigDecimal amountYuan, String subject) {
        PayConfig cfg = requireAlipayReady();
        Map<String, String> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);
        biz.put("total_amount", amountYuan.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        biz.put("subject", subject);
        biz.put("product_code", "QUICK_WAP_WAY");

        Map<String, String> params = baseParams(cfg, "alipay.trade.wap.pay");
        params.put("biz_content", toJson(biz));
        if (notBlank(cfg.getAlipayReturnUrl())) {
            params.put("return_url", cfg.getAlipayReturnUrl());
        }
        params.put("sign", sign(cfg, params));
        return formHtml(cfg.getAlipayGateway(), params);
    }

    /** 异步通知验签：去掉 sign/sign_type 后按字典序拼接原始值验签 */
    public boolean verifyNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        PayConfig cfg = payConfigService.get();
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) {
            return false;
        }
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.remove("sign");
        sorted.remove("sign_type");
        String content = joinRaw(sorted);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey(payConfigService.alipayPublicKey(cfg)));
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.warn("支付宝通知验签失败：{}", e.getMessage());
            return false;
        }
    }

    /** 用 alipay.trade.query 探活：40004/ACQ.TRADE_NOT_EXIST 表示参数与签名基本可用 */
    public Map<String, Object> verifyConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            PayConfig cfg = payConfigService.get();
            if (!payConfigService.alipayReady(cfg)) {
                result.put("ok", false);
                result.put("msg", "支付宝参数不完整：需 APPID、应用私钥、支付宝公钥、网关、异步通知地址");
                return result;
            }
            // H5 付款后需要回跳地址才能回到 App 确认结果，缺失时付款即断链
            if (!notBlank(cfg.getAlipayReturnUrl())) {
                result.put("ok", false);
                result.put("msg", "缺少同步回跳地址：H5 付款后将停留在支付宝页面，无法自动解锁");
                return result;
            }
            Map<String, String> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", "FJVERIFY" + System.currentTimeMillis());
            Map<String, String> params = baseParams(cfg, "alipay.trade.query");
            params.put("biz_content", toJson(biz));
            params.put("sign", sign(cfg, params));

            String body = formEncode(params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.getAlipayGateway()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode node = root.has("alipay_trade_query_response")
                    ? root.path("alipay_trade_query_response") : root;
            String code = node.path("code").asText("");
            String subCode = node.path("sub_code").asText("");
            if ("10000".equals(code) || "40004".equals(code) && "ACQ.TRADE_NOT_EXIST".equals(subCode)) {
                result.put("ok", true);
                result.put("msg", "参数与签名可用");
                return result;
            }
            result.put("ok", false);
            result.put("msg", "支付宝返回：" + code + " " + subCode + " " + node.path("sub_msg").asText(""));
            result.put("raw", node.toString());
            return result;
        } catch (BizException e) {
            result.put("ok", false);
            result.put("msg", e.getMessage());
            return result;
        } catch (Exception e) {
            result.put("ok", false);
            result.put("msg", "测试失败：" + e.getMessage());
            return result;
        }
    }

    /** 退款 */
    public void refund(String outTradeNo, BigDecimal amountYuan, String reason) {
        PayConfig cfg = requireAlipayReady();
        Map<String, String> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);
        // 同一订单只允许退一次，用确定性退款请求号防重复退款
        biz.put("out_request_no", "RF" + outTradeNo);
        biz.put("refund_amount", amountYuan.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        biz.put("refund_reason", reason == null ? "" : reason);
        Map<String, String> params = baseParams(cfg, "alipay.trade.refund");
        params.put("biz_content", toJson(biz));
        params.put("sign", sign(cfg, params));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.getAlipayGateway()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(params), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode node = root.has("alipay_trade_refund_response")
                    ? root.path("alipay_trade_refund_response") : root;
            if (!"10000".equals(node.path("code").asText())) {
                throw new BizException("支付宝退款失败：" + node.path("sub_code").asText("")
                        + " " + node.path("sub_msg").asText(""));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("支付宝退款请求失败：" + e.getMessage());
        }
    }

    /* ---------------- 内部实现 ---------------- */

    private PayConfig requireAlipayReady() {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.alipayReady(cfg)) {
            throw new BizException("支付宝参数未配置完整，请在管理端「支付配置」补全");
        }
        return cfg;
    }

    private Map<String, String> baseParams(PayConfig cfg, String method) {
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", cfg.getAlipayAppId());
        params.put("method", method);
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TS));
        params.put("version", "1.0");
        if (notBlank(cfg.getAlipayNotifyUrl())) {
            params.put("notify_url", cfg.getAlipayNotifyUrl());
        }
        return params;
    }

    private String sign(PayConfig cfg, Map<String, String> params) {
        String content = joinRaw(new TreeMap<>(params));
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey(payConfigService.alipayPrivateKey(cfg)));
            signer.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new BizException("支付宝签名失败：请检查应用私钥");
        }
    }

    private PrivateKey privateKey(String pem) throws Exception {
        String content = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(content)));
    }

    private PublicKey publicKey(String pem) throws Exception {
        String content = pem.contains("BEGIN")
                ? pem.replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "")
                : pem.replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(content)));
    }

    /** 待签名串：key 字典序，原始值，& 拼接，跳过空值 */
    private static String joinRaw(Map<String, String> sorted) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String formEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String formHtml(String gateway, Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<form id=\"alipay_form\" method=\"post\" action=\"").append(escape(gateway)).append("\">");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append("<input type=\"hidden\" name=\"").append(escape(entry.getKey()))
                    .append("\" value=\"").append(escape(entry.getValue())).append("\" />");
        }
        sb.append("</form><script>document.getElementById('alipay_form').submit();</script>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("支付宝参数序列化失败");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
