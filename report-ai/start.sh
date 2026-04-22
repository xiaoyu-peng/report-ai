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
    --rebuild) REBUILD="1" ;;
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
    # 自动发现 JDK 17（绕开用户 JAVA_HOME 指向 1.8/11/21 的坑）
    if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "17'; then
      JDK17=""
      if [[ "$(uname -s)" == "Darwin" ]]; then
        JDK17=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
      fi
      if [[ -z "$JDK17" ]]; then
        for cand in /usr/lib/jvm/java-17-* /usr/lib/jvm/temurin-17* /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
          [[ -d "$cand" ]] && { JDK17="$cand"; break; }
        done
      fi
      if [[ -n "$JDK17" ]]; then
        echo "[start.sh] 当前 JAVA_HOME 不是 17，自动切换到：$JDK17"
        export JAVA_HOME="$JDK17"
        export PATH="$JDK17/bin:$PATH"
      else
        echo "[start.sh] ❌ 未找到 JDK 17。当前 JAVA_HOME=${JAVA_HOME:-未设置}"
        echo "[start.sh] 请装 JDK 17 后重试，或显式 export JAVA_HOME=/path/to/jdk17"
        exit 1
      fi
    fi
    echo "[start.sh] 用 JDK：$(java -version 2>&1 | head -1)"
    # 用项目内 .mvn-settings.xml 强制走阿里云镜像（修国内 _remote.repositories 缓存失效问题）
    MVN_OPTS=""
    if [[ -f backend/.mvn-settings.xml ]]; then
      MVN_OPTS="-s .mvn-settings.xml"
      echo "[start.sh] 使用 backend/.mvn-settings.xml（阿里云镜像）"
    fi
    echo "[start.sh] 编译后端 jar（mvn package -DskipTests，输出在 logs/mvn-build.log）..."
    mkdir -p logs
    ( cd backend && mvn -B -DskipTests $MVN_OPTS package ) 2>&1 | tee logs/mvn-build.log | grep -E "^\[INFO\] (Building|BUILD|---|Installing)" || true
    if [[ ! -f backend/report-hub-api/target/report-hub-api-1.0.0.jar ]]; then
      echo "[start.sh] ❌ 编译失败，详见 logs/mvn-build.log 末尾"
      tail -30 logs/mvn-build.log
      exit 1
    fi
  else
    echo "[start.sh] 后端 jar 已存在，跳过编译（如需强制重建用 --rebuild）。"
  fi
fi

# ---- 4. 启动容器 ----
if [[ "$MODE" == "dev" ]]; then
  echo "[start.sh] DEV 模式：起 mysql + redis + elasticsearch（前后端请在 IDE 里跑）"
  docker compose up -d mysql redis elasticsearch
else
  echo "[start.sh] 完整模式：起 mysql + redis + es + backend + frontend"
  if [[ -n "$REBUILD" ]]; then
    echo "[start.sh] 强制重建镜像（docker compose build --no-cache backend frontend）..."
    docker compose build --no-cache backend frontend
  fi
  docker compose up -d
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
  前端:   cd frontend && npm run dev    ( http://localhost:3002 / :3001 )
  ES 索引同步（后端起来后跑一次）：
          TOKEN=\$(curl -s -X POST http://localhost:8080/api/v1/login \\
                    -H 'Content-Type: application/json' \\
                    -d '{"username":"admin","password":"admin123"}' \\
                  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
          curl -X POST http://localhost:8080/api/v1/es/reindex -H "Authorization: Bearer \$TOKEN"
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

# ---- 7b. 从 snapshot.sql 重建 ES 索引（如有快照数据） ----
if [[ -f database/snapshot.sql ]]; then
  echo -n "[start.sh] 登录获取 token 以触发 ES reindex..."
  TOKEN=$(curl -sf -X POST "http://localhost:8081/api/v1/login" \
            -H "Content-Type: application/json" \
            -d '{"username":"admin","password":"admin123"}' \
          | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || true)
  if [[ -n "$TOKEN" ]]; then
    echo " OK"
    echo "[start.sh] 调用 POST /api/v1/es/reindex 从 MySQL 同步到 ES..."
    curl -sf -X POST "http://localhost:8081/api/v1/es/reindex" \
      -H "Authorization: Bearer $TOKEN" \
      | python3 -m json.tool 2>/dev/null || echo "[start.sh] reindex 失败（非阻塞），可手动调用"
  else
    echo " 跳过（登录失败，可能数据库未初始化）"
  fi
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
