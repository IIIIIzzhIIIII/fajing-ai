#!/usr/bin/env bash
# 发镜 AI 日常备份：MySQL + 上传目录，默认保留 14 天
# 用法：DB_PASSWORD=xxx BACKUP_DIR=/opt/fajing/backups ./backup.sh
# 建议 crontab：30 3 * * * /opt/fajing/backup.sh >> /opt/fajing/logs/backup.log 2>&1

set -euo pipefail

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-fajing}
DB_USER=${DB_USER:-fajing}
DB_PASSWORD=${DB_PASSWORD:-}
UPLOAD_DIR=${UPLOAD_DIR:-/opt/fajing/uploads}
BACKUP_DIR=${BACKUP_DIR:-/opt/fajing/backups}
KEEP_DAYS=${KEEP_DAYS:-14}

TS=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"

echo "[$(date '+%F %T')] 开始备份 $DB_NAME"
mysqldump --single-transaction --routines --triggers \
  -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  | gzip > "$BACKUP_DIR/db-$TS.sql.gz"

if [ -d "$UPLOAD_DIR" ]; then
  tar -czf "$BACKUP_DIR/uploads-$TS.tar.gz" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
fi

find "$BACKUP_DIR" -type f -mtime +"$KEEP_DAYS" -delete

echo "[$(date '+%F %T')] 备份完成：$BACKUP_DIR/db-$TS.sql.gz"
