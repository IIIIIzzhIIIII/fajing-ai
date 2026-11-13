-- 发镜 AI · 建表脚本（启动时自动执行）
CREATE TABLE IF NOT EXISTS hairstyle (
  id VARCHAR(16) PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  gender VARCHAR(8) NOT NULL DEFAULT 'female',
  cat VARCHAR(32) NOT NULL DEFAULT '',
  len VARCHAR(32) NOT NULL DEFAULT '',
  face VARCHAR(64) NOT NULL DEFAULT '',
  en VARCHAR(255) NOT NULL DEFAULT '',
  img_url VARCHAR(512) NOT NULL DEFAULT '',
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
