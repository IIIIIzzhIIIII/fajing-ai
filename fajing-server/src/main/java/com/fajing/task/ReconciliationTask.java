package com.fajing.task;

import com.fajing.entity.AdminLog;
import com.fajing.mapper.AdminLogMapper;
import com.fajing.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 每日支付对账：汇总前一天的支付 / 退款 / 异常单，写入管理端操作日志。
 * 真实渠道账单仍需人工下载后与本汇总比对。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationTask {

    private final ReconciliationService reconciliationService;
    private final AdminLogMapper adminLogMapper;

    @Scheduled(cron = "0 30 2 * * ?")
    public void dailyReconciliation() {
        LocalDate day = LocalDate.now().minusDays(1);
        try {
            Map<String, Object> summary = reconciliationService.summary(day);
            @SuppressWarnings("unchecked")
            Map<String, Object> total = (Map<String, Object>) summary.get("total");
            @SuppressWarnings("unchecked")
            Map<String, Object> anomalies = (Map<String, Object>) summary.get("anomalies");
            long warnings = anomalies.values().stream()
                    .filter(v -> v instanceof Number n && n.longValue() > 0)
                    .count();

            String target = String.format("%s paid=%s/%s refund=%s/%s warn=%d",
                    day,
                    total.get("paidOrders"), total.get("paidAmount"),
                    total.get("refundedOrders"), total.get("refundAmount"),
                    warnings);
            if (target.length() > 64) {
                target = target.substring(0, 64);
            }

            AdminLog row = new AdminLog();
            row.setAdminId(0L);
            row.setAction("RECONCILIATION_DAILY");
            row.setTarget(target);
            row.setCreatedAt(LocalDateTime.now());
            adminLogMapper.insert(row);

            if (warnings > 0) {
                log.warn("每日对账发现 {} 类异常：{}", warnings, anomalies);
            } else {
                log.info("每日对账完成：{}", target);
            }
        } catch (Exception e) {
            log.warn("每日对账任务失败：{}", e.getMessage());
        }
    }
}
