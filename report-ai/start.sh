#!/usr/bin/env bash
# ReportAI 一键启动脚本
# 用法:
#   ./start.sh           # 完整 docker 栈（mysql+redis+backend+frontend），适合演示/交付
#   ./start.sh --dev     # 只起 mysql+redis，提示你在 IDE/npm 里跑前后端（日常开发）
#   ./start.sh --rebuild # 强制重建镜像（改了 Dockerfile / 换了依赖时用）
#   ./start.sh --seed    # 起完后自动跑一次 scripts/seed-knowledge.sh 把演示语料塞进 KB
set -euo pipefail

cd "$(dirname "$0")"

MODE="full"
REBUILD=""
SEED=""

for arg in "$@"; do
  case "$arg" in
    --dev)     MODE="dev" ;;
    --rebuild) REBUILD="--build --no-cache" ;;
    --seed)    SEED="yes" ;;
    -h|--help)
      grep '^#' "$0" | head -10
      exit 0 ;;
    *) echo "[start.sh] 未知参数：$arg"; exit 1 ;;
  esac
done

# ---- 1. 依赖检查 ----
need() {
  command -v "$1" >/dev/null 2>&1 || { echo "[start.sh] 缺少 $1，请先安装后再运行。"; exit 1; }
}
need docker
docker compose version >/dev/null 2>&1 || { echo "[start.sh] 需要 docker compose v2（docker compose ...）"; exit 1; }

# ---- 2. .env 文件 ----
if [[ ! -f .env ]]; then
  echo "[start.sh] 未找到 .env，复制 .env.example 作为起点。"
  cp .env.example .env
  echo "[start.sh] 已生成 .env，请按需填入 DOUBAO_API_KEY 等后重新运行。"
fi

# ---- 3. 预编译后端 jar（完整模式才需要） ----
if [[ "$MODE" == "full" ]]; then
  if [[ ! -f backend/report-hub-api/target/report-hub-api-1.0.0.jar ]] || [[ -n "$REBUILD" ]]; then
    echo "[start.sh] 编译后端 jar（mvn package -DskipTests）..."
    ( cd backend && mvn -q -DskipTests package )
  else
    echo "[start.sh] 后端 jar 已存在，跳过编译（如需强制重建用 --rebuild）。"
  fi
fi

# ---- 4. 启动容器 ----
if [[ "$MODE" == "dev" ]]; then
  echo "[start.sh] DEV 模式：只起 mysql + redis（前后端请在 IDE 里跑）"
  docker compose up -d mysql redis
else
  echo "[start.sh] 完整模式：起 mysql + redis + backend + frontend"
  docker compose up -d $REBUILD
fi

# ---- 5. 等 mysql 健康 ----
echo -n "[start.sh] 等 MySQL 健康"
for i in {1..30}; do
  if docker compose ps mysql --format json 2>/dev/null | grep -q '"Health":"healthy"'; then
    echo " OK"
    break
  fi
  echo -n "."; sleep 2
  [[ $i -eq 30 ]] && { echo " 超时，请检查 docker logs report-ai-mysql"; exit 1; }
done

# ---- 6. DEV 模式的后续提示 ----
if [[ "$MODE" == "dev" ]]; then
  cat <<EOF
[start.sh] DEV 模式就绪。接下来请自行启动前后端：
  后端:   export JAVA_HOME=/path/to/jdk17; cd backend && mvn -pl report-hub-api spring-boot:run
  前端:   cd frontend && npm run dev    ( http://localhost:3001 )
EOF
  exit 0
fi

# ---- 7. 完整模式：等 backend 健康，塞演示数据 ----
echo -n "[start.sh] 等后端健康"
for i in {1..60}; do
  if curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1; then
    echo " OK"
    break
  fi
  echo -n "."; sleep 2
  [[ $i -eq 60 ]] && { echo " 超时，请检查 docker logs report-ai-backend"; exit 1; }
done

if [[ -n "$SEED" ]]; then
  echo "[start.sh] 塞入演示语料..."
  bash scripts/seed-knowledge.sh || echo "[start.sh] 种子脚本失败（非阻塞），请手动重跑"
fi

cat <<'EOF'

====================================================
 ReportAI 启动完成
----------------------------------------------------
 前端        http://localhost:3001   admin / admin123
 后端 API    http://localhost:8081/api/v1
 Swagger     http://localhost:8081/swagger-ui/index.html
 Actuator    http://localhost:8081/actuator/health
 MySQL       localhost:3307  reportai/reportai123   db=report_ai
 Redis       localhost:6380
----------------------------------------------------
 停止：      ./stop.sh
 重置数据：  ./stop.sh --purge
 日志：      docker compose logs -f backend
====================================================
EOF
