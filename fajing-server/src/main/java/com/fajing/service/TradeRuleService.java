package com.fajing.service;

import com.fajing.entity.TradeRule;
import com.fajing.mapper.TradeRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 交易与售后规则：单行配置，供退款校验与管理端展示 */
@Service
@RequiredArgsConstructor
public class TradeRuleService {

    private static final Long RULE_ID = 1L;

    private final TradeRuleMapper tradeRuleMapper;

    public TradeRule get() {
        TradeRule rule = tradeRuleMapper.selectById(RULE_ID);
        if (rule == null) {
            rule = new TradeRule();
            rule.setId(RULE_ID);
            rule.setRefundDays(7);
            rule.setAfterSaleText("支付完成后 7 天内、且未使用的订单可申请退款；已解锁并保存的高清图不支持退款。");
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setUpdatedBy(0L);
            tradeRuleMapper.insert(rule);
        }
        return rule;
    }

    public TradeRule save(TradeRule req, Long adminId) {
        TradeRule rule = get();
        if (req.getRefundDays() != null) {
            rule.setRefundDays(Math.max(0, Math.min(365, req.getRefundDays())));
        }
        if (req.getAfterSaleText() != null) {
            rule.setAfterSaleText(req.getAfterSaleText());
        }
        rule.setUpdatedAt(LocalDateTime.now());
        rule.setUpdatedBy(adminId == null ? 0L : adminId);
        tradeRuleMapper.updateById(rule);
        return rule;
    }
}
