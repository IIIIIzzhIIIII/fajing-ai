package com.fajing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付回调地址别名：与管理端展示的回调路径保持一致，
 * 实际处理仍复用 OrderController 的验签与入账逻辑。
 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayNotifyAliasController {

    private final OrderController orderController;

    @PostMapping("/wechat/miniprogram/notify")
    public ResponseEntity<Map<String, String>> wechatMiniprogramNotify(
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String body) {
        return orderController.wxNotify(headers, body);
    }

    @PostMapping("/alipay/notify")
    public ResponseEntity<String> alipayNotify(@RequestParam Map<String, String> params) {
        return orderController.alipayNotify(params);
    }
}
