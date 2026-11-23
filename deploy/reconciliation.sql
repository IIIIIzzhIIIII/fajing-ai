-- 支付对账参考 SQL：把 @day 改成要核对的日期
-- 需与微信 / 支付宝 / 抖音后台账单逐渠道比对

SET @day = '2026-11-23';

-- 1. 按渠道汇总：支付笔数/金额、退款笔数/金额、净收入
SELECT
  pay_channel AS channel,
  SUM(CASE WHEN status = 'paid' THEN 1 ELSE 0 END) AS paid_orders,
  SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END) AS paid_amount,
  SUM(CASE WHEN refund_status = 'refunded' THEN 1 ELSE 0 END) AS refunded_orders,
  SUM(CASE WHEN refund_status = 'refunded' THEN IFNULL(refund_amount, 0) ELSE 0 END) AS refund_amount,
  SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END)
    - SUM(CASE WHEN refund_status = 'refunded' THEN IFNULL(refund_amount, 0) ELSE 0 END) AS net_amount
FROM orders
WHERE created_at >= CONCAT(@day, ' 00:00:00')
  AND created_at < DATE_ADD(CONCAT(@day, ' 00:00:00'), INTERVAL 1 DAY)
GROUP BY pay_channel
ORDER BY channel;

-- 2. 异常单：已支付但没有支付时间
SELECT id, out_trade_no, pay_channel, amount, status, paid_at
FROM orders
WHERE status = 'paid' AND paid_at IS NULL;

-- 3. 异常单：已退款但退款金额为空或为 0
SELECT id, out_trade_no, pay_channel, amount, refund_status, refund_amount
FROM orders
WHERE refund_status = 'refunded' AND IFNULL(refund_amount, 0) <= 0;

-- 4. 异常单：金额非正数
SELECT id, out_trade_no, biz_type, amount, status
FROM orders
WHERE amount <= 0;

-- 5. 退款中超过 24 小时仍未终态（可能渠道回调丢失）
SELECT id, out_trade_no, pay_channel, refund_amount, updated_at
FROM orders
WHERE refund_status = 'refunding'
  AND created_at < DATE_SUB(NOW(), INTERVAL 1 DAY);
