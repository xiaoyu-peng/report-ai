#!/usr/bin/env bash
# 把本机 MySQL 里的 ReportAI 业务数据导出为 database/snapshot.sql
# ----------------------------------------------------------------
# 用途：在新电脑上 ./setup.sh 时把演示数据原样还原
# 运行前提：./start.sh 已启动 mysql 容器（report-ai-mysql）
# 大坑规避：knowledge_document.file_blob（PDF 原文件）会被跳过，否则 dump 近 100MB
#          原 PDF 预览需要重新上传，但生成/RAG/引用溯源都不依赖 blob
# ----------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

MYSQL_CONTAINER="${MYSQL_CONTAINER:-report-ai-mysql}"
DB="${DB:-report_ai}"
ROOT_PASS="${ROOT_PASS:-root123456}"
OUT="database/snapshot.sql"

if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  echo "[export] 未发现运行中的容器 ${MYSQL_CONTAINER}，先跑 ./start.sh 或 ./start.sh --dev"
  exit 1
fi

# 不含 blob 的小表
TABLES_LIGHT=(
  knowledge_base
  knowledge_chunk
  report_template
  report
  report_version
  report_citation
  report_section
)

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$ROOT_PASS" "$DB" "$@"
}
mysqldump_exec() {
  docker exec "$MYSQL_CONTAINER" mysqldump -uroot -p"$ROOT_PASS" \
    --no-create-info --complete-insert --hex-blob --skip-extended-insert \
    --default-character-set=utf8mb4 "$@"
}

echo "[export] 步骤 1/3：构建 knowledge_document 去 blob 临时表"
mysql_exec <<SQL 2>/dev/null
DROP TABLE IF EXISTS knowledge_document_nb;
CREATE TABLE knowledge_document_nb AS
  SELECT id, kb_id, filename, file_type, file_size, content, status,
         chunk_count, created_by, created_at, updated_at, deleted
  FROM knowledge_document;
SQL

echo "[export] 步骤 2/3：导出业务表 → ${OUT}"
{
  echo "-- ReportAI 演示数据快照"
  echo "-- 生成时间：$(date '+%Y-%m-%d %H:%M:%S')"
  echo "-- 仅数据，不含表结构；表结构由 init.sql 建好。"
  echo "-- knowledge_document.file_blob 已剔除（首次使用时如需 PDF 预览请手动重新上传原文件）。"
  echo "SET FOREIGN_KEY_CHECKS=0;"
  echo "SET NAMES utf8mb4;"
  echo
  mysqldump_exec "$DB" "${TABLES_LIGHT[@]}"
  echo
  # knowledge_document: dump 临时表 → 改名
  mysqldump_exec "$DB" knowledge_document_nb \
    | sed 's/`knowledge_document_nb`/`knowledge_document`/g'
  echo
  echo "SET FOREIGN_KEY_CHECKS=1;"
} > "$OUT"

echo "[export] 步骤 3/3：清理临时表"
mysql_exec <<SQL 2>/dev/null
DROP TABLE IF EXISTS knowledge_document_nb;
SQL

SIZE=$(wc -c < "$OUT" | awk '{print $1}')
HUMAN=$(ls -lh "$OUT" | awk '{print $5}')
ROWS=$(grep -c '^INSERT ' "$OUT" || true)
echo "[export] 完成：${OUT}（${HUMAN}, ${ROWS} 条 INSERT）"
echo "[export] git add ${OUT} 并提交后，新电脑 ./setup.sh 就能原样还原演示数据。"
