-- 发镜 AI · 建表脚本（启动时自动执行）
CREATE TABLE IF NOT EXISTS hairstyle (
  id VARCHAR(16) PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  gender VARCHAR(8) NOT NULL DEFAULT 'female',
  cat VARCHAR(32) NOT NULL DEFAULT '',
  len VARCHAR(32) NOT NULL DEFAULT '',
  face VARCHAR(64) NOT NULL DEFAULT '',
  en VARCHAR(255) NOT NULL DEFAULT '',
  img_url VARCHAR(1024) NOT NULL DEFAULT '',
  status TINYINT NOT NULL DEFAULT 1,
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_gender (gender),
  KEY idx_cat (cat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hair_color (
  id VARCHAR(16) PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  en VARCHAR(255) NOT NULL DEFAULT '',
  bg VARCHAR(128) NOT NULL DEFAULT '',
  sort INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS generation_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  photo_url VARCHAR(512) NOT NULL DEFAULT '',
  gender VARCHAR(8) NOT NULL DEFAULT 'female',
  mode VARCHAR(16) NOT NULL DEFAULT 'hair',
  style_ids VARCHAR(255) NOT NULL DEFAULT '',
  color_id VARCHAR(16) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  progress INT NOT NULL DEFAULT 0,
  result_json LONGTEXT NULL,
  free_quota TINYINT NOT NULL DEFAULT 0,
  expire_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  task_id BIGINT NOT NULL DEFAULT 0,
  title VARCHAR(64) NOT NULL DEFAULT '',
  before_url VARCHAR(512) NOT NULL DEFAULT '',
  cover_url VARCHAR(512) NOT NULL DEFAULT '',
  style_ids VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 讨论区已下线：清理历史帖子/评论/举报表（存在则删除，数据不再保留）
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS report;
DROP TABLE IF EXISTS post;

CREATE TABLE IF NOT EXISTS user_unlock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  task_id BIGINT NOT NULL DEFAULT 0,
  style_id VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_task_style (user_id, task_id, style_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 解锁从「用户+款式」升级为「用户+任务+款式」：同一款在新一批仍要重新解锁
ALTER TABLE user_unlock DROP INDEX uk_user_style;
ALTER TABLE user_unlock ADD UNIQUE KEY uk_user_task_style (user_id, task_id, style_id);

CREATE TABLE IF NOT EXISTS user_quota (
  user_id BIGINT PRIMARY KEY,
  free_left INT NOT NULL DEFAULT 1,
  quota_date DATE NOT NULL,
  share_claimed TINYINT NOT NULL DEFAULT 0,
  ad_used TINYINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 额度返还流水：整批生成全部失败时自动 +1 并记录
CREATE TABLE IF NOT EXISTS quota_refund (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL DEFAULT 0,
  reason VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 调用成本流水：用于统计今日生成量与成本
CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  provider VARCHAR(32) NOT NULL DEFAULT '',
  model VARCHAR(64) NOT NULL DEFAULT '',
  cost_cents INT NOT NULL DEFAULT 0,
  ok TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_created_at (created_at),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 前端行为埋点：upload_success / generate_start / generate_success / result_view / save_image / pay_click / pay_success / post_create
CREATE TABLE IF NOT EXISTS event_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 0,
  event VARCHAR(48) NOT NULL,
  payload_json TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_event (event),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  task_id BIGINT NOT NULL DEFAULT 0,
  plan VARCHAR(16) NOT NULL DEFAULT 'all',
  amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(16) NOT NULL DEFAULT 'paid',
  style_ids VARCHAR(255) NOT NULL DEFAULT '',
  platform VARCHAR(16) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表：目前登录接入前只有体验用户，接入微信登录后按 openid 建档
CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname VARCHAR(32) NOT NULL DEFAULT '微信用户',
  avatar VARCHAR(512) NOT NULL DEFAULT '',
  gender VARCHAR(8) NOT NULL DEFAULT 'unknown',
  openid VARCHAR(64) NOT NULL DEFAULT '',
  phone VARCHAR(20) NOT NULL DEFAULT '',
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  session_key VARCHAR(128) NOT NULL DEFAULT '',
  password_hash VARCHAR(100) NOT NULL DEFAULT '',
  status TINYINT NOT NULL DEFAULT 1,
  register_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_active_at DATETIME NULL,
  UNIQUE KEY uk_openid (openid),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库的增量字段（重复执行会报已存在，continue-on-error=true 会跳过）
ALTER TABLE app_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE app_user ADD COLUMN session_key VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE app_user ADD UNIQUE KEY uk_openid (openid);

-- 管理员账号：账号密码登录，密码 BCrypt 存储
CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理端操作日志
CREATE TABLE IF NOT EXISTS admin_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT NOT NULL DEFAULT 0,
  action VARCHAR(64) NOT NULL DEFAULT '',
  target VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 生成配置（单行，id 固定为 1）：图像模型 / 文案模型 / 提示词，后台可改
CREATE TABLE IF NOT EXISTS ai_config (
  id BIGINT PRIMARY KEY,
  image_provider VARCHAR(32) NOT NULL DEFAULT 'siliconflow',
  image_base_url VARCHAR(255) NOT NULL DEFAULT 'https://api.siliconflow.cn/v1',
  image_api_key VARCHAR(255) NOT NULL DEFAULT '',
  image_model VARCHAR(128) NOT NULL DEFAULT 'Qwen/Qwen-Image-Edit-2509',
  text_base_url VARCHAR(255) NOT NULL DEFAULT 'https://api.deepseek.com/v1',
  text_api_key VARCHAR(255) NOT NULL DEFAULT '',
  text_model VARCHAR(128) NOT NULL DEFAULT 'deepseek-flash',
  prompt_template TEXT NULL,
  system_prompt TEXT NULL,
  use_text_enhance TINYINT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 支付配置（单行，id 固定为 1）：微信支付 / 支付宝参数，敏感字段 AES-256-GCM 加密入库
CREATE TABLE IF NOT EXISTS pay_config (
  id BIGINT PRIMARY KEY,
  wx_enabled TINYINT NOT NULL DEFAULT 0,
  wx_pay_mode VARCHAR(16) NOT NULL DEFAULT 'virtual',
  wx_appid VARCHAR(64) NOT NULL DEFAULT '',
  wx_mch_id VARCHAR(32) NOT NULL DEFAULT '',
  wx_api_v3_key_enc VARCHAR(512) NOT NULL DEFAULT '',
  wx_cert_serial_no VARCHAR(64) NOT NULL DEFAULT '',
  wx_private_key_path VARCHAR(255) NOT NULL DEFAULT '',
  wx_notify_url VARCHAR(255) NOT NULL DEFAULT '',
  wx_virtual_appkey_enc VARCHAR(512) NOT NULL DEFAULT '',
  wx_virtual_offer_id VARCHAR(64) NOT NULL DEFAULT '',
  wx_virtual_env TINYINT NOT NULL DEFAULT 0,
  wx_virtual_product_one VARCHAR(64) NOT NULL DEFAULT '',
  wx_virtual_product_all VARCHAR(64) NOT NULL DEFAULT '',
  douyin_enabled TINYINT NOT NULL DEFAULT 0,
  douyin_app_id VARCHAR(64) NOT NULL DEFAULT '',
  douyin_secret_enc VARCHAR(512) NOT NULL DEFAULT '',
  douyin_salt_enc VARCHAR(512) NOT NULL DEFAULT '',
  douyin_token_enc VARCHAR(512) NOT NULL DEFAULT '',
  douyin_notify_url VARCHAR(255) NOT NULL DEFAULT '',
  douyin_sandbox TINYINT NOT NULL DEFAULT 0,
  xhs_enabled TINYINT NOT NULL DEFAULT 0,
  xhs_app_id VARCHAR(64) NOT NULL DEFAULT '',
  xhs_secret_enc VARCHAR(512) NOT NULL DEFAULT '',
  xhs_merchant_id VARCHAR(64) NOT NULL DEFAULT '',
  xhs_notify_url VARCHAR(255) NOT NULL DEFAULT '',
  alipay_enabled TINYINT NOT NULL DEFAULT 0,
  alipay_app_id VARCHAR(64) NOT NULL DEFAULT '',
  alipay_private_key_enc VARCHAR(1024) NOT NULL DEFAULT '',
  alipay_public_key_enc VARCHAR(1024) NOT NULL DEFAULT '',
  alipay_gateway VARCHAR(255) NOT NULL DEFAULT 'https://openapi.alipay.com/gateway.do',
  alipay_notify_url VARCHAR(255) NOT NULL DEFAULT '',
  alipay_return_url VARCHAR(255) NOT NULL DEFAULT '',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单支付渠道字段（旧库增量，重复执行由 continue-on-error 跳过）
ALTER TABLE orders ADD COLUMN out_trade_no VARCHAR(64) NULL;
ALTER TABLE orders ADD COLUMN pay_channel VARCHAR(16) NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN transaction_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN prepay_id VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN paid_at DATETIME NULL;
ALTER TABLE orders ADD COLUMN refund_status VARCHAR(16) NOT NULL DEFAULT 'none';
ALTER TABLE orders ADD COLUMN refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN platform VARCHAR(16) NOT NULL DEFAULT '';
ALTER TABLE orders ADD UNIQUE KEY uk_out_trade_no (out_trade_no);

-- 生成任务：本批是否使用免费额度（1 免费批次：第 1 款免费；0 付费批次：全部需支付后生成）
ALTER TABLE generation_task ADD COLUMN free_quota TINYINT NOT NULL DEFAULT 0;

-- 支付配置：多平台接入字段（旧库增量）
ALTER TABLE pay_config MODIFY wx_pay_mode VARCHAR(16) NOT NULL DEFAULT 'virtual';
-- 虚拟商品仅保留小程序虚拟支付，历史模式统一收敛
UPDATE pay_config SET wx_pay_mode = 'virtual' WHERE wx_pay_mode <> 'virtual';
ALTER TABLE pay_config ADD COLUMN wx_virtual_offer_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN wx_virtual_env TINYINT NOT NULL DEFAULT 0;
ALTER TABLE pay_config ADD COLUMN wx_virtual_product_one VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN wx_virtual_product_all VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN douyin_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE pay_config ADD COLUMN douyin_app_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN douyin_secret_enc VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN douyin_salt_enc VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN douyin_token_enc VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN douyin_notify_url VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN douyin_sandbox TINYINT NOT NULL DEFAULT 0;
ALTER TABLE pay_config ADD COLUMN xhs_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE pay_config ADD COLUMN xhs_app_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN xhs_secret_enc VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN xhs_merchant_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN xhs_notify_url VARCHAR(255) NOT NULL DEFAULT '';

-- 交易与售后规则（单行，id 固定为 1）
CREATE TABLE IF NOT EXISTS trade_rule (
  id BIGINT PRIMARY KEY,
  refund_days INT NOT NULL DEFAULT 7,
  after_sale_text VARCHAR(500) NOT NULL DEFAULT '',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== 魅力值 / 免费批次 / VIP（旧库增量，重复执行由 continue-on-error 跳过） =====
ALTER TABLE user_quota ADD COLUMN points INT NOT NULL DEFAULT 50;
ALTER TABLE user_quota ADD COLUMN free_batches INT NOT NULL DEFAULT 3;
ALTER TABLE user_quota ADD COLUMN vip_expire_at DATETIME NULL;
ALTER TABLE user_quota ADD COLUMN vvip TINYINT NOT NULL DEFAULT 0;
ALTER TABLE user_quota ADD COLUMN ad_date DATE NULL;
ALTER TABLE user_quota ADD COLUMN ad_count INT NOT NULL DEFAULT 0;
-- 广告获得的免费解锁次数（每看 1 次广告 +1 次，解锁时优先消耗）
ALTER TABLE user_quota ADD COLUMN ad_credits INT NOT NULL DEFAULT 0;

-- ===== 订单扩展：区分解锁 / 充值 / 券 / VIP =====
ALTER TABLE orders ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT 'unlock';
ALTER TABLE orders ADD COLUMN points INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN days INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN tier_id BIGINT NOT NULL DEFAULT 0;

-- 虚拟支付充值道具（金额：10 / 20 / 29 / 99 元）
ALTER TABLE pay_config ADD COLUMN wx_virtual_product_p10 VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN wx_virtual_product_p20 VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN wx_virtual_product_p29 VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE pay_config ADD COLUMN wx_virtual_product_p99 VARCHAR(64) NOT NULL DEFAULT '';

-- ===== 生成任务扩展：推荐记录与发色变体自动生成 =====
ALTER TABLE generation_task ADD COLUMN recommend_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generation_task ADD COLUMN auto_gen TINYINT NOT NULL DEFAULT 0;

-- 推荐结果：一次分析对应一张照片，缓存脸型/发色排名，避免重复消耗模型
CREATE TABLE IF NOT EXISTS recommendation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  photo_url VARCHAR(512) NOT NULL DEFAULT '',
  gender VARCHAR(8) NOT NULL DEFAULT 'female',
  face_shape VARCHAR(32) NOT NULL DEFAULT '',
  skin_tone VARCHAR(32) NOT NULL DEFAULT '',
  styles_json LONGTEXT NULL,
  colors_json LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 团购券档位（固定档位，后台可维护）
CREATE TABLE IF NOT EXISTS coupon_tier (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(64) NOT NULL DEFAULT '',
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  value_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  description VARCHAR(255) NOT NULL DEFAULT '',
  status TINYINT NOT NULL DEFAULT 1,
  sort INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户券包：支付成功后生成核销码，线下门店核销
CREATE TABLE IF NOT EXISTS user_coupon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  tier_id BIGINT NOT NULL DEFAULT 0,
  order_id BIGINT NOT NULL DEFAULT 0,
  title VARCHAR(64) NOT NULL DEFAULT '',
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  value_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  code VARCHAR(32) NOT NULL DEFAULT '',
  qr_url VARCHAR(512) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL DEFAULT 'unused',
  expire_at DATETIME NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 发色解锁：按「用户 + 发色」永久保留，解锁后无需重复解锁
CREATE TABLE IF NOT EXISTS user_color_unlock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  color_id VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_color (user_id, color_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认券档位（仅当表为空时由后端初始化，见 CouponService）

-- 站点基础设置：单行配置，前台文案与联系方式由后台维护
CREATE TABLE IF NOT EXISTS site_setting (
  id BIGINT PRIMARY KEY,
  hero_title_main VARCHAR(64) NOT NULL DEFAULT '',
  hero_title_accent VARCHAR(64) NOT NULL DEFAULT '',
  contact_wechat VARCHAR(64) NOT NULL DEFAULT '',
  contact_phone VARCHAR(32) NOT NULL DEFAULT '',
  about_content TEXT,
  terms_content TEXT,
  privacy_content TEXT,
  face_consent_text TEXT,
  updated_at DATETIME NULL,
  updated_by BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 充值套餐：后台可增删改，下单金额与到账权益以此为准
CREATE TABLE IF NOT EXISTS points_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(64) NOT NULL DEFAULT '',
  description VARCHAR(128) NOT NULL DEFAULT '',
  badge VARCHAR(32) NOT NULL DEFAULT '',
  biz_type VARCHAR(16) NOT NULL DEFAULT 'points',
  price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  points INT NOT NULL DEFAULT 0,
  days INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  sort INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 多租户 / 门店 / 员工 / 佣金（第一版平台化改造）
-- ============================================================

-- 商户（租户）：店长购买系统后对应一个租户
CREATE TABLE IF NOT EXISTS tenant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  contact_name VARCHAR(32) NOT NULL DEFAULT '',
  contact_phone VARCHAR(20) NOT NULL DEFAULT '',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 门店：租户下可有多家门店，第一版至少一家
CREATE TABLE IF NOT EXISTS store (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  brand_name VARCHAR(64) NOT NULL DEFAULT '',
  address VARCHAR(255) NOT NULL DEFAULT '',
  phone VARCHAR(20) NOT NULL DEFAULT '',
  business_hours VARCHAR(64) NOT NULL DEFAULT '',
  daily_capacity INT NOT NULL DEFAULT 20,
  commission_rate DECIMAL(5,2) NOT NULL DEFAULT 10.00,
  kyc_status VARCHAR(16) NOT NULL DEFAULT 'pending',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 员工：店长通过手机号/邀请码拉入；user_id 绑定后即可进入员工工作台
CREATE TABLE IF NOT EXISTS staff (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(32) NOT NULL DEFAULT '',
  phone VARCHAR(20) NOT NULL DEFAULT '',
  role VARCHAR(16) NOT NULL DEFAULT 'EMPLOYEE',
  invite_code VARCHAR(16) NOT NULL,
  staff_code VARCHAR(16) NOT NULL,
  daily_limit INT NOT NULL DEFAULT 20,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invite (invite_code),
  UNIQUE KEY uk_staff_code (staff_code),
  KEY idx_store (store_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 员工每日免费 AI 额度
CREATE TABLE IF NOT EXISTS staff_quota (
  staff_id BIGINT PRIMARY KEY,
  quota_date DATE NOT NULL,
  used INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 顾客-员工归属：顾客扫员工码后绑定 30 天，订单按 staff_id 归因
CREATE TABLE IF NOT EXISTS staff_customer_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expire_at DATETIME NOT NULL,
  UNIQUE KEY uk_customer (customer_id),
  KEY idx_staff (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 平台佣金台账：支付成功记 available，核销后 settled，退款 reversed
CREATE TABLE IF NOT EXISTS commission_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL DEFAULT 0,
  order_id BIGINT NOT NULL,
  order_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  commission_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(16) NOT NULL DEFAULT 'available',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  settled_at DATETIME NULL,
  reversed_at DATETIME NULL,
  UNIQUE KEY uk_order (order_id),
  KEY idx_store (store_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库的增量字段
ALTER TABLE admin_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_user ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_user ADD COLUMN display_name VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE coupon_tier ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN staff_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE user_coupon ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_coupon ADD COLUMN staff_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_coupon ADD COLUMN commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE user_coupon ADD COLUMN settle_status VARCHAR(16) NOT NULL DEFAULT '';
ALTER TABLE user_coupon ADD COLUMN verified_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generation_task ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generation_task ADD COLUMN staff_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generation_task ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'user';
ALTER TABLE generation_task ADD COLUMN claim_token VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE generation_task ADD COLUMN claimed_user_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE generation_task ADD COLUMN claimed_at DATETIME NULL;
ALTER TABLE ai_call_log ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_call_log ADD COLUMN store_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_call_log ADD COLUMN staff_id BIGINT NOT NULL DEFAULT 0;

