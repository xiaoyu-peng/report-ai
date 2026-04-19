#!/usr/bin/env bash
# ReportAI 一键停机
# 用法:
#   ./stop.sh          # 停容器，保留数据卷
#   ./stop.sh --purge  # 连同 mysql/redis 数据一起清掉（演示前重置用）
set -euo pipefail
cd "$(dirname "$0")"

if [[ "${1:-}" == "--purge" ]]; then
  echo "[stop.sh] 停全部容器并清数据卷..."
  docker compose down -v
else
  echo "[stop.sh] 停全部容器（保留 mysql/redis 数据卷）..."
  docker compose down
fi
echo "[stop.sh] done"
