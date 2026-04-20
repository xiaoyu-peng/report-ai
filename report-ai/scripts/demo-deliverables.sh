#!/usr/bin/env bash
# 竞赛交付一键脚本：3 份主题报告 × 各 4 种改写 + 1 份续写。
# 依赖：已起好 MySQL + Redis + ES + 后端（localhost:8080），KB 1-3 已灌好。
#
# 用法：
#   ./scripts/demo-deliverables.sh                       # 默认 admin/admin123
#   USERNAME=xxx PASSWORD=yyy ./scripts/demo-deliverables.sh
#
# 生成的报告 id 会打印到 stdout，便于截图选取。
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-admin123}"
# 单请求最长等待（生成 + 改写 SSE 会完全跑完才断连）。
TIMEOUT="${TIMEOUT:-240}"

log() { printf '\033[36m[demo]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[FATAL]\033[0m %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "缺少依赖: $1"; }

need curl; need python3

log "登录 $USERNAME ..."
TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
[[ -n "$TOKEN" ]] || die "登录失败"
log "token ok (len=${#TOKEN})"

# ---------- helpers ----------

# 创建草稿 → 返回 id
# 通过 argv 传入避免 shell quoting 把 JSON 撕碎
create_report() {
  local title="$1" topic="$2" kb_id="$3" tpl_id="$4" depth="${5:-standard}"
  local body
  body=$(python3 -c '
import json, sys
print(json.dumps({
    "title": sys.argv[1],
    "topic": sys.argv[2],
    "kbId": int(sys.argv[3]),
    "templateId": int(sys.argv[4]),
    "generationDepth": sys.argv[5]
}, ensure_ascii=False))
' "$title" "$topic" "$kb_id" "$tpl_id" "$depth")
  curl -fsS -X POST "$BASE_URL/api/v1/reports" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$body" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])'
}

# 跑 SSE 直到结束；打印 chunks/done 摘要到 stderr；丢弃 token 流（减少屏幕噪声）
# 注意：curl 在 SSE 流结束时常返回 18（transfer closed with outstanding data），属正常，
# 所以这里局部关 pipefail & 用 `|| true` 吃掉 curl 退出码，避免 set -e 误伤。
consume_sse() {
  local label="$1" url="$2" method="${3:-GET}" body="${4:-}"
  local args=(-sS -N --max-time "$TIMEOUT" -H "Authorization: Bearer $TOKEN" -H 'Accept: text/event-stream')
  [[ "$method" == "POST" ]] && args+=(-X POST -H 'Content-Type: application/json' -d "$body")
  set +o pipefail
  { curl "${args[@]}" "$url" || true; } | python3 -c "
import sys, json
label = sys.argv[1]
buf=''
evt='message'
data_lines=[]
tokens=0
chunks_count=0
for line in sys.stdin:
    line=line.rstrip('\n')
    if line.startswith('event:'):
        evt=line[6:].strip()
    elif line.startswith('data:'):
        data_lines.append(line[5:].lstrip(' '))
    elif line=='':
        if not data_lines:
            continue
        payload='\n'.join(data_lines)
        if evt=='chunks':
            try:
                arr=json.loads(payload)
                chunks_count=len(arr)
            except Exception:
                pass
        elif evt=='token':
            tokens+=len(payload)
        elif evt=='done':
            sys.stderr.write(f'  [{label}] done · chunks={chunks_count} tokens={tokens}\n')
        elif evt=='error':
            sys.stderr.write(f'  [{label}] ERROR {payload}\n')
            sys.exit(2)
        evt='message'; data_lines=[]
" "$label"
  local rc=$?
  set -o pipefail
  return $rc
}

generate_report() {
  local id="$1"
  consume_sse "gen#$id" "$BASE_URL/api/v1/reports/$id/generate"
}

rewrite_report() {
  local id="$1" mode="$2" instruction="$3"
  local body
  body=$(python3 -c '
import json, sys
print(json.dumps({"mode": sys.argv[1], "instruction": sys.argv[2]}, ensure_ascii=False))
' "$mode" "$instruction")
  consume_sse "rw#$id:$mode" "$BASE_URL/api/v1/reports/$id/rewrite" POST "$body"
}

# ---------- 3 份报告 × 各 4 改写 ----------
# 主题按赛题的 5 类样例覆盖：政策影响 / 传播分析 / 行业分析。
# KB 选择按语料匹配：kb=3 行业研究库（AI、数字化转型）；kb=2 宏观经济（出行、投资）；kb=1 政策法规库。
# 模板对应：3=政策影响报告、2=传播分析报告、5=行业分析报告。
# 第 5 列 depth。"行业分析" 路由会按 keywords 多次扩散搜索，叠加 industry 模板的长结构 JSON
# 容易撞豆包 32K token 上限——所以第 3 主题改用市场研究模板 + 小 KB(消费市场库 146 chunks) + brief。

declare -a topics=(
  "AI+制造专项行动政策影响研判|AI+制造专项行动对产业链/就业/区域格局的政策影响|3|3|standard"
  "五一出行政策舆情传播分析|五一期间北京电动车新规与跨省出行政策的网络舆情传播|2|2|standard"
  "家庭消费市场转型趋势分析|家庭消费品牌升级与渠道转型|4|8|brief"
)

declare -a created=()

for row in "${topics[@]}"; do
  IFS='|' read -r title topic kb tpl <<< "$row"
  log "===== 主题：$title  (kb=$kb, template=$tpl) ====="
  rid=$(create_report "$title" "$topic" "$kb" "$tpl" "standard")
  log "report id = $rid"
  created+=("$rid")

  log "[1/5] 首次生成"
  generate_report "$rid"

  log "[2/5] DATA_UPDATE"
  rewrite_report "$rid" DATA_UPDATE "把文中的数据全部更新到 2026 Q1 最新口径，标注来源年份"

  log "[3/5] ANGLE_SHIFT"
  rewrite_report "$rid" ANGLE_SHIFT "从行业视角切换到政府监管视角，受众为主管部门领导"

  log "[4/5] EXPAND"
  rewrite_report "$rid" EXPAND "在原有结构内，补充典型案例与量化数据，增加 30% 篇幅"

  log "[5/5] STYLE_SHIFT"
  rewrite_report "$rid" STYLE_SHIFT "改成公文体（庄重、短句、结构化编号），保留数据与引用"
done

# ---------- 1 份续写 ----------
first_id="${created[0]}"
log "===== 续写 #$first_id：新章节"风险与对策" ====="
rewrite_report "$first_id" CONTINUATION "续写一章"风险与对策"，涵盖合规风险、技术风险、产业链风险三类，每类给对策"

# ---------- 汇总 ----------
log "=========== 交付产物 ============="
for rid in "${created[@]}"; do
  printf '  · 报告 #%s\n' "$rid"
done
log "每份报告版本史可在前端『报告详情 → 版本对比』查看，共 1 首次 + 4 改写 + (首份多 1 续写)"
log "后端日志：report-ai/logs/backend.log"
