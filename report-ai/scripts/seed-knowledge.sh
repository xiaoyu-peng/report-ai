#!/usr/bin/env bash
# 把一批公开的权威源（政策/行业/历史/媒体）抓进对应 KB，用于演示 RAG 生成与以稿写稿
# ------------------------------------------------------------
# 前置：docker 栈已通（`./start.sh` 启动完成），admin/admin123 可登录
# 运行：bash scripts/seed-knowledge.sh
# ------------------------------------------------------------
set -euo pipefail

BACKEND="${BACKEND:-http://localhost:8081}"

login_and_token() {
  local token
  token=$(curl -sf -X POST "$BACKEND/api/v1/login" \
            -H "Content-Type: application/json" \
            -d '{"username":"admin","password":"admin123"}' \
         | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
  [[ -z "$token" ]] && { echo "[seed] 登录失败"; exit 1; }
  echo "$token"
}

# 若缺 history KB 则建一个，返回 id
ensure_kb() {
  local token=$1 name=$2 cat=$3 desc=$4
  # 先看是否已存在同 name
  local exist
  exist=$(curl -sf "$BACKEND/api/v1/knowledge/bases" -H "Authorization: Bearer $token" \
          | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or {}
items=d if isinstance(d,list) else d.get('records',[])
for kb in items:
    if kb.get('name')=='$name': print(kb['id']); break" 2>/dev/null)
  if [[ -n "$exist" ]]; then
    echo "$exist"
    return
  fi
  curl -sf -X POST "$BACKEND/api/v1/knowledge/bases" \
    -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    -d "{\"name\":\"$name\",\"description\":\"$desc\",\"category\":\"$cat\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])"
}

imp() {
  local token=$1 kb=$2 url=$3 name=$4
  local resp
  resp=$(curl -s -X POST "$BACKEND/api/v1/knowledge/bases/$kb/url" \
           -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
           -d "{\"url\":\"$url\",\"filename\":\"$name\"}")
  # 用正则提取 primitives，避免 content 字段里的反斜杠把 json.load 弄翻
  local line
  line=$(echo "$resp" | python3 -c "
import sys,re
raw=sys.stdin.read()
out=[]
for k in ('code','chunkCount','fileSize'):
    m=re.search(r'\"'+k+r'\"\s*:\s*(\d+)',raw)
    out.append(k+'='+(m.group(1) if m else '?'))
print(' '.join(out))")
  printf "  [kb=%s] %s :: %s\n" "$kb" "$line" "$name"
}

TOKEN=$(login_and_token)

echo "[seed] 确保 4 类 KB 存在（policy/industry/history/media）..."
KB_POLICY=$(ensure_kb   "$TOKEN" "政策法规库" policy   "国务院/发改委/工信部政策文件")
KB_INDUSTRY=$(ensure_kb "$TOKEN" "AI行业知识库" industry "行业研报、大模型发展、产业规模数据")
KB_HISTORY=$(ensure_kb  "$TOKEN" "历史报告库"  history  "历年政府工作报告 / 白皮书 / 专题报告")
KB_MEDIA=$(ensure_kb    "$TOKEN" "舆情监测库"  media    "新华网/央视网等主流媒体稿件")

echo ""
echo "[seed] 政策法规 → KB $KB_POLICY"
imp "$TOKEN" "$KB_POLICY" "https://www.gov.cn/zhengce/content/202508/content_7037861.htm" "国务院关于深入实施人工智能+行动的意见"
imp "$TOKEN" "$KB_POLICY" "https://www.gov.cn/zhengce/202507/content_7034734.htm"          "国务院常务会议解读-人工智能+行动"
imp "$TOKEN" "$KB_POLICY" "https://www.gov.cn/zhengce/zhengceku/202511/content_7047250.htm" "工信部-2025年AI产业揭榜挂帅通知"
imp "$TOKEN" "$KB_POLICY" "https://www.gov.cn/zhengce/zhengceku/202601/content_7054201.htm" "八部门-人工智能+制造专项行动实施意见"
imp "$TOKEN" "$KB_POLICY" "https://www.gov.cn/zhengce/202508/content_7037929.htm"          "中国人工智能+行动路线图"
imp "$TOKEN" "$KB_POLICY" "https://www.ndrc.gov.cn/xxgk/jd/zctj/202508/t20250826_1400057.html" "发改委-一图读懂人工智能+行动"

echo ""
echo "[seed] AI 行业 → KB $KB_INDUSTRY"
imp "$TOKEN" "$KB_INDUSTRY" "https://www.news.cn/tech/20260120/5c20c6f7ba864526b6e674acaea53e25/c.html" "特稿-展望全球人工智能2026年演进新局"
imp "$TOKEN" "$KB_INDUSTRY" "http://www.news.cn/20251220/35cf0eda6db8479da1a278bbbe974ec5/c.html"       "报告预计明年我国人工智能产业维持高速增长"
imp "$TOKEN" "$KB_INDUSTRY" "http://www.news.cn/tech/20251215/071bdd29f56348f1ae3102b8507ff25e/c.html"  "中国信通院-AI核心产业规模有望超过1.2万亿元"
imp "$TOKEN" "$KB_INDUSTRY" "https://www.news.cn/world/20260108/a6291ff2eca94acc8d10236a5ffb396f/c.html" "2026年全球科技展望-生命家园深空智能"
imp "$TOKEN" "$KB_INDUSTRY" "http://www.news.cn/liangzi/20251204/60de15f58722489f82180e40a1ddae22/c.html" "智能体普及或将催生超级公司"

echo ""
echo "[seed] 历史报告 → KB $KB_HISTORY"
imp "$TOKEN" "$KB_HISTORY" "https://www.ndrc.gov.cn/fzggw/jgsj/zys/sjdt/202403/t20240320_1365089.html" "发改委-2024政府工作报告全文"
imp "$TOKEN" "$KB_HISTORY" "http://www.china.org.cn/chinese/2024-03/14/content_117057714.htm"          "2024政府工作报告全文-china.org.cn"
imp "$TOKEN" "$KB_HISTORY" "http://lianghui.people.com.cn/2024/n1/2024/0312/c458561-40194559.html"     "人民网-2024政府工作报告"
imp "$TOKEN" "$KB_HISTORY" "https://www.gov.cn/zhuanti/2024qglh/2024nzfgzbg/"                         "政府工作报告专题-2024"

echo ""
echo "[seed] 媒体资讯 → KB $KB_MEDIA"
imp "$TOKEN" "$KB_MEDIA" "https://www.news.cn/politics/20260128/25f6978fe8cf41bda53b78bcf7106fec/c.html" "新华深读-2026中国AI发展趋势前瞻"
imp "$TOKEN" "$KB_MEDIA" "https://www.news.cn/tech/20260113/635d4b8aa5ed44d6af693ae39fe53b14/c.html"     "新华网-八部门印发AI+制造专项行动"
imp "$TOKEN" "$KB_MEDIA" "https://news.cctv.cn/2026/01/28/ARTIEvCAzmKkHNEUbwOAS6et260128.shtml"          "央视网-2026中国AI发展趋势前瞻"

echo ""
echo "[seed] 完成。进 http://localhost:3001 查看。"
