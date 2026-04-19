#!/usr/bin/env bash
# ------------------------------------------------------------
# T5 演示数据 seed：3 主题报告 + 4 模式 ×3 改写 + 1 续写 = 16 个版本
# 满足赛题"3 份不同主题的完整报告 + 3 篇 ×4 改写 + 至少 1 续写"
#
# 前置：./start.sh 跑通；admin/admin123 可登录；至少 1 个 KB 已有文档
# 运行：bash scripts/demo-seed-t5.sh
# ------------------------------------------------------------
set -euo pipefail

BACKEND="${BACKEND:-http://localhost:8081}"

echo "[1/4] 登录 admin..."
TOKEN=$(curl -sf -X POST "$BACKEND/api/v1/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
[[ -z "$TOKEN" ]] && { echo "登录失败"; exit 1; }
H_AUTH="Authorization: Bearer $TOKEN"
echo "  ok"

echo "[2/4] 拉知识库列表..."
KB_IDS=$(curl -sf "$BACKEND/api/v1/knowledge/bases" -H "$H_AUTH" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or {}
items=d if isinstance(d,list) else d.get('records',[])
ids=[str(x['id']) for x in items[:4]]
print(','.join(ids))
")
[[ -z "$KB_IDS" ]] && { echo "无可用 KB，请先跑 seed-knowledge.sh"; exit 1; }
echo "  使用 KBs: $KB_IDS"

# 用第一个 KB 作为 kbId（生成接口当前接受单个 kbId）
PRIMARY_KB=$(echo "$KB_IDS" | cut -d',' -f1)

declare -a TOPICS=(
  "2026 新能源汽车产业洞察:覆盖政策/技术/竞争/出海/趋势五维"
  "ChatGPT 对教育行业影响政策建议:监管/伦理/教学/评价/政策建议"
  "2026Q3 消费电子市场月报:出货量/创新点/区域差异/品牌战/展望"
)

REPORT_IDS=()

for t in "${TOPICS[@]}"; do
  TITLE="${t%%:*}"
  KP="${t#*:}"
  echo "[3/4] 创建报告: $TITLE"
  RID=$(curl -sf -X POST "$BACKEND/api/v1/reports" \
    -H "$H_AUTH" -H "Content-Type: application/json" \
    -d "{\"title\":\"$TITLE\",\"topic\":\"$TITLE\",\"kbId\":$PRIMARY_KB,\"keyPoints\":[\"$KP\"],\"generationDepth\":\"standard\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
  echo "  报告 ID: $RID"
  REPORT_IDS+=("$RID")

  # 整篇生成（SSE，等到 done 事件再退出）
  echo "  整篇生成中..."
  curl -sf -N "$BACKEND/api/v1/reports/$RID/generate" -H "$H_AUTH" \
    | python3 -c "
import sys
for line in sys.stdin:
    if line.startswith('event:done'): print('  done'); sys.exit(0)
" || true
  sleep 1
done

echo "[4/4] 4 改写模式 + 1 续写..."
MODES=(DATA_UPDATE ANGLE_SHIFT EXPAND STYLE_SHIFT)
INSTR=(
  "用最新 2026 年数据替换报告中 2024/2025 旧数据"
  "改为面向高层决策者的领导简报口吻，800 字内"
  "每章扩充 1-2 段案例与延伸分析"
  "正式 → 通俗，让普通读者也能读懂"
)

# 取前 3 个报告每个跑 4 模式
for RID in "${REPORT_IDS[@]:0:3}"; do
  for i in "${!MODES[@]}"; do
    M="${MODES[$i]}"
    INS="${INSTR[$i]}"
    echo "  rid=$RID mode=$M..."
    curl -sf -N -X POST "$BACKEND/api/v1/reports/$RID/rewrite" \
      -H "$H_AUTH" -H "Content-Type: application/json" \
      -d "{\"mode\":\"$M\",\"instruction\":\"$INS\"}" \
      | python3 -c "
import sys
for line in sys.stdin:
    if line.startswith('event:done'): sys.exit(0)
" || true
    sleep 1
  done
done

# 1 续写示例（取第一个报告）
FIRST_RID="${REPORT_IDS[0]}"
echo "  续写新章节 -> rid=$FIRST_RID..."
curl -sf -N -X POST "$BACKEND/api/v1/reports/$FIRST_RID/rewrite" \
  -H "$H_AUTH" -H "Content-Type: application/json" \
  -d '{"mode":"CONTINUATION","instruction":"在原稿末尾追加一章「实施路径与挑战」，分阶段路线图"}' \
  | python3 -c "
import sys
for line in sys.stdin:
    if line.startswith('event:done'): sys.exit(0)
" || true

echo ""
echo "✅ T5 演示数据完成："
echo "  - ${#REPORT_IDS[@]} 篇报告：${REPORT_IDS[*]}"
echo "  - 12 个改写版本（3 报告 × 4 模式）"
echo "  - 1 个续写版本"
echo ""
echo "打开 http://localhost:5173/reports 查看，每个报告点进去可以："
echo "  ① 报告正文 → 看引用 popover (点 [n])"
echo "  ② 智能编辑（Tiptap）→ 选段落用 BubbleMenu 改写"
echo "  ③ 章节流式 → 提交大纲，看每章独立流式"
echo "  ④ 覆盖度体检（T5）→ 饼图 + KPI + 事实性可疑"
echo "  ⑤ 版本对比 → 看 4 模式 + 续写 diff"
