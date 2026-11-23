-- 仅限本地 / 演示环境：清空全部 C 端用户数据，保留管理员、门店、发型、套餐等基础数据
-- 执行后重启后端（local profile）会重新执行 data.sql 补齐演示档位

DELETE FROM commission_ledger;
DELETE FROM user_coupon;
DELETE FROM orders;
DELETE FROM user_unlock;
DELETE FROM user_color_unlock;
DELETE FROM user_record;
DELETE FROM generation_task;
DELETE FROM recommendation;
DELETE FROM user_quota;
DELETE FROM event_log;
DELETE FROM app_user;
