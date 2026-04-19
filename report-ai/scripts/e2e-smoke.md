# T5 端到端浏览器验收清单

> 启动：`./start.sh --dev` → IDE 跑 backend → `cd frontend && npm run dev`
> 登录：admin / admin123 → 前端 http://localhost:5173

## 12 步核心路径

### A. 知识库（已存在，回归）
1. **`/knowledge` 列表** — 看到 4 个 KB（政策法规/行业报告/历史报告/媒体资讯），文档计数 > 0
2. **某 KB 详情** — 文档列表展开，点 `查看` 弹窗可读全文，点 `编辑` 可改写正文
3. **「重新嵌入」按钮** — 点击后 chunk 表里 `paragraph_index` 字段被填充
   ```sql
   SELECT COUNT(*), COUNT(paragraph_index) FROM knowledge_chunk;
   ```

### B. 报告生成 + 杀手锏交互
4. **`/workspace` 创建报告** — 填标题 + 主题 + KB → 生成 → 跳详情页
5. **`/reports/{id}` 「报告正文」tab** — 看到 [n] 角标，点击 → popover 显示文档+页码+snippet（杀手锏 ②）
6. **「智能编辑（Tiptap）」tab** — 选中一段 → 顶部浮起 ✨/➕/✂️ 三按钮 → 点 ✨ → 文段被改写（杀手锏 ③）
7. **「章节流式」tab** — 编辑大纲（拖拽 + 增删）→ 「提交大纲」→ 右侧每章 pending → generating → done（杀手锏 ⑤）
8. **「覆盖度体检（T5）」tab** — 看到 KPI 三卡 + 饼图 + 事实性可疑列表（杀手锏 ④）

### C. 改写与对比
9. **「版本对比」tab** — 选两版本 → diff 高亮新增/删除/修改（杀手锏 ①，已有实现）
10. **「质量保障」tab（旧）** — LLM-as-judge 三维度评分

### D. 导出
11. **「导出 Word」按钮** — 下载 .docx，文末有「引用列表」附录，每条 [n] 含文档名+页码+原文片段
12. **「导出 PDF」按钮** — 前端 html2pdf 输出 PDF

## 杀手锏交互到 UI 路径速查

| 编号 | 交互 | 入口 |
|---|---|---|
| ① | 修订 diff（词级三色） | `/reports/{id}` → 「版本对比」 tab → 选两版本（已有 + 新组件 DiffView 可选） |
| ② | 引用溯源 popover | `/reports/{id}` → 「报告正文」 tab → 点正文里任意 [n] |
| ③ | 段落 AI 浮窗 | `/reports/{id}` → 「智能编辑（Tiptap）」 tab → 选中段落 |
| ④ | 覆盖度仪表盘 | `/reports/{id}` → 「覆盖度体检（T5）」 tab |
| ⑤ | 大纲拖拽 + 章节流式 | `/reports/{id}` → 「章节流式」 tab |

## DB 验收 SQL

```sql
-- 4 张新表 + 3 列扩展存在
SHOW TABLES LIKE 'report_%';
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='report_ai'
  AND ((TABLE_NAME='knowledge_chunk' AND COLUMN_NAME='paragraph_index')
    OR (TABLE_NAME='report_template'  AND COLUMN_NAME='outline_json')
    OR (TABLE_NAME='report_version'   AND COLUMN_NAME='before_content'));

-- 跑过 demo seed 后
SELECT COUNT(*) FROM report;                  -- 期望 ≥ 3
SELECT COUNT(*) FROM report_version;          -- 期望 ≥ 16（3 初版 + 12 改写 + 1 续写）
SELECT COUNT(*) FROM report_citation;         -- 期望 ≥ 50（每报告 5-15 个引用）
SELECT COUNT(*) FROM report_section;          -- 期望 = 提交过大纲的章节数
SELECT report_id, coverage_rate FROM report_quality;
```

## 已知降级 / 风险

| 项 | 降级方案 |
|---|---|
| Tiptap v3 BubbleMenu 不再有 Vue 组件 | 已用自实现浮动 div（监听 selectionUpdate） |
| 段落级 SSE 多并发 | 当前顺序跑（避免 LLM rate limit） |
| DOCX 真脚注 XWPFFootnote 复杂 | 已降级为「文末引用列表附录」（含文档名+页码+片段） |
| PDF 后端 LibreOffice | 用现有前端 html2pdf 兜底（无需后端改动） |
