package com.fajing.service.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.PayConfig;
import com.fajing.service.PayConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付 APIv3（JSAPI）：下单、小程序调起参数、回调验签解密、关单与退款。
 * 证书与私钥全部来自后台「支付配置」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatPayService {

    private static final String HOST = "https://api.mch.weixin.qq.com";
    private static final String AUTH_SCHEMA = "WECHATPAY2-SHA256-RSA2048";

    private final PayConfigService payConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Map<String, X509Certificate> platformCerts = new ConcurrentHashMap<>();
    private volatile long certFetchedAt = 0;

    /** JSAPI 下单，返回 prepay_id */
    public String createJsapi(String outTradeNo, BigDecimal amountYuan, String description, String openid) {
        PayConfig cfg = requireWxReady();
        if (!"normal".equalsIgnoreCase(cfg.getWxPayMode())) {
            throw new BizException("当前为小程序虚拟支付模式，需先在微信侧开通虚拟支付并完成对接；可先将支付模式改为 normal");
        }
        if (openid == null || openid.isBlank()) {
            throw new BizException("缺少用户 openid，无法发起微信支付");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appid", cfg.getWxAppid());
        body.put("mchid", cfg.getWxMchId());
        body.put("description", description);
        body.put("out_trade_no", outTradeNo);
        body.put("notify_url", cfg.getWxNotifyUrl());
        body.put("amount", Map.of("total", toCents(amountYuan), "currency", "CNY"));
        body.put("payer", Map.of("openid", openid));
        JsonNode resp = request(cfg, "POST", "/v3/pay/transactions/jsapi", body);
        String prepayId = resp.path("prepay_id").asText("");
        if (prepayId.isBlank()) {
            throw new BizException("微信下单失败：未返回 prepay_id");
        }
        return prepayId;
    }

    /** 小程序 wx.requestPayment 所需参数 */
    public Map<String, String> payParams(String prepayId) {
        PayConfig cfg = requireWxReady();
        String timeStamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = nonce();
        String pkg = "prepay_id=" + prepayId;
        String message = cfg.getWxAppid() + "\n" + timeStamp + "\n" + nonceStr + "\n" + pkg + "\n";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timeStamp", timeStamp);
        params.put("nonceStr", nonceStr);
        params.put("package", pkg);
        params.put("signType", "RSA");
        params.put("paySign", sign(cfg, message));
        return params;
    }

    public void close(String outTradeNo) {
        PayConfig cfg = requireWxReady();
        request(cfg, "POST", "/v3/pay/transactions/out-trade-no/" + outTradeNo + "/close",
                Map.of("mchid", cfg.getWxMchId()));
    }

    /** 申请退款，返回微信退款单号 */
    public String refund(String outTradeNo, String refundNo, BigDecimal refundYuan, BigDecimal totalYuan, String reason) {
        PayConfig cfg = requireWxReady();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("out_trade_no", outTradeNo);
        body.put("out_refund_no", refundNo);
        body.put("reason", reason == null ? "" : reason);
        body.put("amount", Map.of(
                "refund", toCents(refundYuan),
                "total", toCents(totalYuan),
                "currency", "CNY"));
        JsonNode resp = request(cfg, "POST", "/v3/refund/domestic/refunds", body);
        return resp.path("refund_id").asText("");
    }

    /** 回调验签 + 解密 resource，返回解密后的业务 JSON */
    public JsonNode verifyNotify(Map<String, String> headers, String body) {
        try {
            PayConfig cfg = requireWxReady();
            String serial = header(headers, "wechatpay-serial");
            String timestamp = header(headers, "wechatpay-timestamp");
            String nonce = header(headers, "wechatpay-nonce");
            String signature = header(headers, "wechatpay-signature");
            if (serial.isBlank() || timestamp.isBlank() || nonce.isBlank() || signature.isBlank()) {
                throw new BizException("微信回调缺少签名头");
            }
            long ts = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - ts) > 300) {
                throw new BizException("微信回调时间戳超出允许范围");
            }
            X509Certificate cert = platformCert(cfg, serial);
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            if (!verify(cert, message, signature)) {
                throw new BizException("微信回调验签失败");
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode resource = root.path("resource");
            String plain = decryptResource(
                    payConfigService.wxApiV3Key(cfg),
                    resource.path("nonce").asText(),
                    resource.path("associated_data").asText(""),
                    resource.path("ciphertext").asText());
            return objectMapper.readTree(plain);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信回调处理失败：" + e.getMessage());
        }
    }

    /** 用 /v3/certificates 探活：能拉到证书说明商户号、序列号与私钥匹配 */
    public Map<String, Object> verifyConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            PayConfig cfg = payConfigService.get();
            if (!payConfigService.wxReady(cfg)) {
                result.put("ok", false);
                result.put("msg", "微信参数不完整：需 AppID、商户号、APIv3 密钥、证书序列号、私钥、回调地址");
                return result;
            }
            refreshCertificates(cfg);
            result.put("ok", true);
            result.put("msg", "证书与签名通过");
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

    /* ---------------- 内部实现 ---------------- */

    private PayConfig requireWxReady() {
        PayConfig cfg = payConfigService.get();
        if (!payConfigService.wxReady(cfg)) {
            throw new BizException("微信支付参数未配置完整，请在管理端「支付配置」补全");
        }
        return cfg;
    }

    private JsonNode request(PayConfig cfg, String method, String path, Object body) {
        try {
            String bodyText = body == null ? "" : objectMapper.writeValueAsString(body);
            String authorization = authorization(cfg, method, path, bodyText);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(HOST + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", authorization)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8));
            }
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = resp.body() == null || resp.body().isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(resp.body());
            if (resp.statusCode() == 401) {
                throw new BizException("微信鉴权失败：请检查证书序列号与商户私钥是否匹配");
            }
            if (resp.statusCode() >= 300) {
                String code = json.path("code").asText("");
                String message = json.path("message").asText(resp.body());
                throw new BizException("微信接口失败（" + resp.statusCode() + "）" + code + " " + message);
            }
            return json;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信接口调用失败：" + e.getMessage());
        }
    }

    private String authorization(PayConfig cfg, String method, String path, String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = nonce();
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = sign(cfg, message);
        return AUTH_SCHEMA + " mchid=\"" + cfg.getWxMchId()
                + "\",nonce_str=\"" + nonce
                + "\",signature=\"" + signature
                + "\",timestamp=\"" + timestamp
                + "\",serial_no=\"" + cfg.getWxCertSerialNo() + "\"";
    }

    private String sign(PayConfig cfg, String message) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey(cfg.getWxPrivateKeyPath()));
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信签名失败：请检查商户私钥文件");
        }
    }

    private PrivateKey privateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            String content = pem.replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(content);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new BizException("商户私钥读取失败，请重新上传 .pem 文件");
        }
    }

    private X509Certificate platformCert(PayConfig cfg, String serial) {
        X509Certificate cert = platformCerts.get(serial);
        if (cert != null) {
            return cert;
        }
        refreshCertificates(cfg);
        cert = platformCerts.get(serial);
        if (cert == null) {
            throw new BizException("未找到微信平台证书：" + serial);
        }
        return cert;
    }

    private synchronized void refreshCertificates(PayConfig cfg) {
        if (System.currentTimeMillis() - certFetchedAt < 12 * 3600_000L && !platformCerts.isEmpty()) {
            return;
        }
        JsonNode root = request(cfg, "GET", "/v3/certificates", null);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new BizException("微信证书列表返回异常");
        }
        for (JsonNode item : data) {
            try {
                JsonNode enc = item.path("encrypt_certificate");
                String plain = decryptResource(
                        payConfigService.wxApiV3Key(cfg),
                        enc.path("nonce").asText(),
                        enc.path("associated_data").asText(""),
                        enc.path("ciphertext").asText());
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(plain.getBytes(StandardCharsets.UTF_8)));
                platformCerts.put(item.path("serial_no").asText(), cert);
            } catch (Exception e) {
                log.warn("解析微信平台证书失败：{}", e.getMessage());
            }
        }
        certFetchedAt = System.currentTimeMillis();
        if (platformCerts.isEmpty()) {
            throw new BizException("微信平台证书解析失败，请检查 APIv3 密钥");
        }
    }

    private boolean verify(X509Certificate cert, String message, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(cert.getPublicKey());
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            return false;
        }
    }

    private String decryptResource(String key, String nonce, String associatedData, String ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("微信回调解密失败，请检查 APIv3 密钥");
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static int toCents(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).intValueExact();
    }
}
