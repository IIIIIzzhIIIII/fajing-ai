package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.PayConfig;
import com.fajing.service.AdminLogService;
import com.fajing.service.PayConfigService;
import com.fajing.service.pay.AlipayService;
import com.fajing.service.pay.WechatPayService;
import com.fajing.service.pay.WechatVirtualPayService;
import com.fajing.util.KeyFileStore;
import com.fajing.util.SecretCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端支付配置：掩码读取、保存、测试连接、上传商户私钥。
 */
@Tag(name = "管理端 · 支付配置", description = "微信支付 / 支付宝参数配置与连通性测试")
@RestController
@RequestMapping("/api/admin/pay-config")
@RequiredArgsConstructor
public class AdminPayConfigController {

    private final PayConfigService payConfigService;
    private final WechatPayService wechatPayService;
    private final WechatVirtualPayService wechatVirtualPayService;
    private final AlipayService alipayService;
    private final KeyFileStore keyFileStore;
    private final AdminLogService adminLogService;
    private final SecretCodec secretCodec;

    @Value("${fajing.base-url:http://localhost:8081}")
    private String baseUrl;

    /** H5 用户端站点地址（与 API 域名分离时配置），用于生成支付宝回跳地址 */
    @Value("${fajing.h5-base-url:}")
    private String h5BaseUrl;

    @Operation(summary = "读取支付配置（密钥掩码）")
    @GetMapping
    public Result<Map<String, Object>> get() {
        PayConfig cfg = payConfigService.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", payConfigService.masked(cfg));
        data.put("wxReady", payConfigService.wxReady(cfg));
        data.put("wxVirtualReady", payConfigService.wxVirtualReady(cfg));
        data.put("douyinReady", payConfigService.douyinReady(cfg));
        data.put("xhsReady", payConfigService.xhsReady(cfg));
        data.put("alipayReady", payConfigService.alipayReady(cfg));
        data.put("secretConfigured", secretCodec.configured());
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // 回跳地址必须落在 H5 站点域名，API 子域无法承载前端路由
        String h5Base = (h5BaseUrl == null || h5BaseUrl.isBlank() ? baseUrl : h5BaseUrl.trim());
        h5Base = h5Base.endsWith("/") ? h5Base.substring(0, h5Base.length() - 1) : h5Base;
        data.put("wxNotifyUrlSuggested", base + "/api/pay/wechat/miniprogram/notify");
        data.put("douyinNotifyUrlSuggested", base + "/api/order/notify/douyin");
        data.put("xhsNotifyUrlSuggested", base + "/api/order/notify/xhs");
        data.put("alipayNotifyUrlSuggested", base + "/api/pay/alipay/notify");
        data.put("alipayReturnUrlSuggested", h5Base + "/#/pages/result/result");
        return Result.ok(data);
    }

    @Operation(summary = "保存支付配置")
    @PutMapping
    public Result<PayConfig> save(@RequestBody PayConfig req, HttpServletRequest request) {
        Object adminId = request.getAttribute("adminId");
        PayConfig saved = payConfigService.save(req, adminId instanceof Long value ? value : 0L);
        adminLogService.log("SAVE_PAY_CONFIG",
                "wx:" + saved.getWxEnabled() + ",alipay:" + saved.getAlipayEnabled());
        return Result.ok(payConfigService.masked(saved));
    }

    @Operation(summary = "测试支付通道连接（wx / alipay）")
    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody(required = false) Map<String, String> body) {
        String channel = body == null ? "" : String.valueOf(body.getOrDefault("channel", ""));
        if ("wx".equalsIgnoreCase(channel)) {
            // 虚拟支付与普通 JSAPI 的校验口径不同：虚拟支付只需要 OfferID/AppKey/道具 + access_token
            PayConfig cfg = payConfigService.get();
            boolean virtual = "virtual".equalsIgnoreCase(cfg.getWxPayMode());
            return Result.ok(virtual ? wechatVirtualPayService.verifyConnection()
                    : wechatPayService.verifyConnection());
        }
        if ("alipay".equalsIgnoreCase(channel)) {
            return Result.ok(alipayService.verifyConnection());
        }
        throw new BizException("请指定要测试的通道：wx / alipay");
    }

    @Operation(summary = "上传微信商户私钥 .pem")
    @PostMapping("/cert")
    public Result<Map<String, String>> uploadCert(@RequestParam("file") MultipartFile file) {
        String path = keyFileStore.save(file);
        adminLogService.log("UPLOAD_PAY_CERT", path);
        return Result.ok(Map.of("path", path));
    }
}
