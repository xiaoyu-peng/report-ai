#!/usr/bin/env bash
# ReportAI 一键体检 + 安装 + 启动
# 用法:
#   ./setup.sh             # 体检 → 缺啥装啥 → 写 .env → 调 start.sh
#   ./setup.sh --dev       # 体检 → IDE 模式（只起 mysql+redis+es，前后端自己跑）
#   ./setup.sh --yes       # 自动确认所有 brew install 提示
#   ./setup.sh --skip-env  # 跳过 .env 交互填充
set -euo pipefail
cd "$(dirname "$0")"

# ---- 参数 ----
AUTO_YES=""
SKIP_ENV=""
PASSTHRU=()
for arg in "$@"; do
  case "$arg" in
    --yes|-y)    AUTO_YES="1" ;;
    --skip-env)  SKIP_ENV="1" ;;
    *) PASSTHRU+=("$arg") ;;
  esac
done

# ---- 颜色 ----
if [[ -t 1 ]]; then
  C_GREEN=$'\033[32m'; C_YEL=$'\033[33m'; C_RED=$'\033[31m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else
  C_GREEN=""; C_YEL=""; C_RED=""; C_DIM=""; C_RST=""
fi
ok()   { echo "${C_GREEN}✓${C_RST} $*"; }
warn() { echo "${C_YEL}!${C_RST} $*"; }
err()  { echo "${C_RED}✗${C_RST} $*"; }
ask()  {
  if [[ -n "$AUTO_YES" ]]; then return 0; fi
  read -r -p "$1 [Y/n] " ans
  [[ -z "$ans" || "$ans" =~ ^[Yy]$ ]]
}

# ---- 1. OS 识别 ----
OS="unknown"
case "$(uname -s)" in
  Darwin) OS="mac" ;;
  Linux)  OS="linux" ;;
  *)      err "不支持的系统：$(uname -s)；请用 macOS 或 Linux（Windows 走 WSL2）"; exit 1 ;;
esac
ok "系统：$OS"

# ---- 2. 依赖体检 ----
MISSING=()
need_check() {
  local name="$1" cmd="$2" verify="$3"
  if eval "$verify" >/dev/null 2>&1; then
    ok "$name 已就绪 ${C_DIM}($(eval "$cmd" 2>&1 | head -1))${C_RST}"
  else
    warn "$name 未安装"
    MISSING+=("$name")
  fi
}

need_check "Docker"        "docker --version"          "command -v docker"
need_check "Docker Compose" "docker compose version"   "docker compose version"
need_check "Java 17"        "java -version"            "java -version 2>&1 | grep -Eq 'version \"(17|18|19|20|21|22)'"
need_check "Maven"          "mvn -v"                   "command -v mvn"
need_check "Node.js"        "node -v"                  "command -v node && [[ \$(node -v | sed 's/v//;s/\\..*//') -ge 18 ]]"
need_check "npm"            "npm -v"                   "command -v npm"

# ---- 3. 缺啥装啥 ----
if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo
  warn "缺失依赖：${MISSING[*]}"

  if [[ "$OS" == "mac" ]]; then
    if ! command -v brew >/dev/null 2>&1; then
      err "Homebrew 未安装，请先跑："
      echo '    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
      exit 1
    fi
    ok "Homebrew 已就绪"

    declare -A BREW_PKG=(
      ["Docker"]="--cask docker"
      ["Docker Compose"]=""        # docker desktop 自带
      ["Java 17"]="openjdk@17"
      ["Maven"]="maven"
      ["Node.js"]="node"
      ["npm"]=""                   # node 自带
    )

    for dep in "${MISSING[@]}"; do
      pkg="${BREW_PKG[$dep]:-}"
      if [[ -z "$pkg" ]]; then continue; fi
      if ask "用 brew 安装 $dep ($pkg)？"; then
        # shellcheck disable=SC2086
        brew install $pkg
        if [[ "$dep" == "Java 17" ]]; then
          warn "需要把 openjdk@17 加到 PATH："
          echo "    sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk"
          echo "    或在 ~/.zshrc 加： export JAVA_HOME=\$(brew --prefix openjdk@17)"
        fi
      fi
    done

    if [[ " ${MISSING[*]} " =~ " Docker " ]]; then
      warn "Docker Desktop 装完后请手动打开应用一次，授权后再回来跑 ./setup.sh"
      exit 1
    fi

  elif [[ "$OS" == "linux" ]]; then
    err "请按需手动安装（需要 sudo），常见命令："
    cat <<'EOF'
  # Ubuntu / Debian
  sudo apt update
  sudo apt install -y docker.io docker-compose-plugin openjdk-17-jdk maven nodejs npm
  sudo systemctl enable --now docker
  sudo usermod -aG docker $USER   # 注销重登录使生效

  # CentOS / RHEL
  sudo dnf install -y docker docker-compose-plugin java-17-openjdk maven nodejs npm
  sudo systemctl enable --now docker
EOF
    exit 1
  fi
fi

# ---- 4. Docker daemon ready? ----
if ! docker info >/dev/null 2>&1; then
  err "Docker daemon 未运行；请先打开 Docker Desktop（macOS）或 sudo systemctl start docker（Linux）"
  exit 1
fi
ok "Docker daemon 在线"

# ---- 5. .env 文件 ----
if [[ ! -f .env ]]; then
  cp .env.example .env
  ok "已生成 .env（基于 .env.example）"
fi

# ---- 5b. snapshot.sql 占位（避免 docker-compose 挂载不存在的文件报错） ----
if [[ ! -f database/snapshot.sql ]]; then
  cat > database/snapshot.sql <<'EOF'
-- 占位文件：首次启动无预置数据。想要演示数据，请在已有数据的机器上跑：
--   ./scripts/export-snapshot.sh
-- 再 git commit + push，新机器 ./setup.sh 会自动加载。
SELECT 1;
EOF
  warn "未发现 database/snapshot.sql，已生成空占位；想要演示数据请跑 ./scripts/export-snapshot.sh"
fi

if [[ -z "$SKIP_ENV" ]]; then
  set +e
  source ./.env
  set -e
  if [[ -z "${DOUBAO_API_KEY:-}" ]]; then
    read -r -p "$(echo -e ${C_YEL}请输入 DOUBAO_API_KEY ${C_DIM}\(回车跳过\)${C_RST}: )" v
    if [[ -n "$v" ]]; then sed -i.bak "s|^DOUBAO_API_KEY=.*|DOUBAO_API_KEY=$v|" .env && rm -f .env.bak; fi
  fi
  if [[ -z "${DOUBAO_ENDPOINT:-}" ]]; then
    read -r -p "$(echo -e ${C_YEL}请输入 DOUBAO_ENDPOINT ${C_DIM}\(火山方舟接入点 ID, ep-xxx, 回车跳过\)${C_RST}: )" v
    if [[ -n "$v" ]]; then sed -i.bak "s|^DOUBAO_ENDPOINT=.*|DOUBAO_ENDPOINT=$v|" .env && rm -f .env.bak; fi
  fi
fi

# ---- 6. 调 start.sh ----
echo
ok "依赖齐备，开始启动 ReportAI..."
echo
exec ./start.sh "${PASSTHRU[@]}"
