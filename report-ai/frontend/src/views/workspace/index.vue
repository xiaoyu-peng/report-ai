<template>
  <div class="workspace-container">
    <!-- Left: Config Panel -->
    <div class="config-panel">
      <el-card class="config-card">
        <template #header>
          <span class="card-title">
            <el-icon><EditPen /></el-icon>
            报告设置
          </span>
        </template>

        <el-form :model="form" label-position="top">
          <el-form-item label="报告标题">
            <el-input
              v-model="form.title"
              placeholder="输入报告标题"
              maxlength="100"
              show-word-limit
              :disabled="generating"
            />
          </el-form-item>

          <el-form-item label="核心主题">
            <el-input
              v-model="form.topic"
              type="textarea"
              :rows="3"
              placeholder="描述报告的核心主题和目标..."
              :disabled="generating"
            />
          </el-form-item>

          <el-form-item label="重点内容">
            <el-input
              v-model="form.keyPoints"
              type="textarea"
              :rows="4"
              placeholder="列出需要涵盖的重点内容，每行一条..."
              :disabled="generating"
            />
          </el-form-item>

          <el-form-item label="选择知识库">
            <el-select
              v-model="form.kbId"
              placeholder="选择参考知识库（可选）"
              clearable
              style="width: 100%"
              :disabled="generating"
            >
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              >
                <span>{{ kb.name }}</span>
                <span style="float: right; color: #999; font-size: 12px">
                  {{ kb.docCount ?? 0 }} 篇文档
                </span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="写作风格模板">
            <el-select
              v-model="form.templateId"
              placeholder="选择模板（可选）"
              clearable
              style="width: 100%"
              :disabled="generating"
            >
              <el-option
                v-for="t in templates"
                :key="t.id"
                :label="t.name"
                :value="t.id"
              />
            </el-select>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="generating"
            :disabled="!canGenerate"
            @click="handleGenerate"
          >
            <el-icon v-if="!generating"><MagicStick /></el-icon>
            {{ generating ? 'AI 生成中...' : '开始生成报告' }}
          </el-button>
        </el-form>
      </el-card>
    </div>

    <!-- Right: Editor Panel -->
    <div class="editor-panel">
      <el-card class="editor-card">
        <template #header>
          <div class="editor-header">
            <span class="card-title">
              <el-icon><Document /></el-icon>
              报告内容
            </span>
            <div class="editor-actions">
              <el-tag v-if="wordCount > 0" type="info" effect="light">
                {{ wordCount }} 字
              </el-tag>
              <el-radio-group
                v-if="content"
                v-model="viewMode"
                size="small"
                :disabled="generating || rewriting"
              >
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="preview">预览</el-radio-button>
              </el-radio-group>
              <el-button
                v-if="currentReportId && content"
                size="small"
                :loading="saving"
                :disabled="generating || rewriting"
                @click="saveReport"
              >
                <el-icon><Select /></el-icon>
                保存
              </el-button>
              <el-button
                v-if="content"
                size="small"
                type="success"
                plain
                :disabled="generating"
                @click="copyContent"
              >
                <el-icon><CopyDocument /></el-icon>
                复制
              </el-button>
              <el-dropdown
                v-if="currentReportId && content"
                trigger="click"
                :disabled="generating || rewriting"
                @command="handleRewrite"
              >
                <el-button size="small" type="warning" plain :loading="rewriting">
                  <el-icon><MagicStick /></el-icon>
                  改写
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="DATA_UPDATE">数据更新（替换时间/数据）</el-dropdown-item>
                    <el-dropdown-item command="ANGLE_SHIFT">视角调整（换观点/受众）</el-dropdown-item>
                    <el-dropdown-item command="EXPAND">内容扩展（补案例/章节）</el-dropdown-item>
                    <el-dropdown-item command="STYLE_SHIFT">风格转换（正式↔通俗）</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </template>

        <!-- Empty state -->
        <div v-if="!content && !generating" class="empty-state">
          <el-empty description="填写左侧配置后点击「开始生成报告」">
            <template #image>
              <el-icon style="font-size: 80px; color: #c0c4cc">
                <Document />
              </el-icon>
            </template>
          </el-empty>
        </div>

        <!-- Loading before first token -->
        <div v-if="generating && !content" class="generating-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 正在思考和撰写，请稍候...</span>
        </div>

        <!-- Streaming / editable content -->
        <el-input
          v-if="(content || generating) && viewMode === 'edit'"
          v-model="content"
          type="textarea"
          :rows="30"
          resize="none"
          class="report-editor"
          :readonly="generating || rewriting"
          placeholder="报告内容将在此处流式显示..."
        />

        <!-- Markdown 预览（含 [n] 角标，点击跳转溯源面板） -->
        <div
          v-else-if="content && viewMode === 'preview'"
          class="report-preview"
          @click="handleCiteClick"
          v-html="renderedHtml"
        />
      </el-card>
    </div>

    <!-- Right: Citation Traceability Panel（仅在有 chunks 时展示） -->
    <div v-if="chunks.length > 0" class="citations-panel">
      <el-card class="citations-card">
        <template #header>
          <span class="card-title">
            <el-icon><Collection /></el-icon>
            引用溯源
            <el-tag size="small" type="info" effect="light" style="margin-left: 6px">
              {{ chunks.length }}
            </el-tag>
          </span>
        </template>
        <div class="citations-list">
          <div
            v-for="c in chunks"
            :key="c.index"
            :id="`cite-card-${c.index}`"
            class="citation-card"
            :class="{ highlighted: highlightedCite === c.index }"
          >
            <div class="citation-head">
              <span class="citation-idx">[{{ c.index }}]</span>
              <span class="citation-file" :title="c.filename">{{ c.filename }}</span>
              <el-tag size="small" effect="plain">#{{ c.chunkIndex }}</el-tag>
            </div>
            <div class="citation-body">{{ c.content }}</div>
            <div class="citation-foot">
              相关度 {{ c.score.toFixed(2) }}
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import {
  ElMessage,
  ElMessageBox
} from 'element-plus'
import {
  EditPen,
  Document,
  MagicStick,
  Loading,
  CopyDocument,
  Select,
  ArrowDown,
  Collection
} from '@element-plus/icons-vue'
import { getKnowledgeBases, type KnowledgeBase } from '@/api/knowledge'
import {
  getTemplates,
  createReport,
  updateReport,
  type Template,
  type RewriteMode
} from '@/api/report'
import { useUserStore } from '@/stores/user'
import { renderReportMarkdown } from '@/utils/markdown'

/** RAG 命中条目（后端 SSE chunks 事件的 payload 元素）。index 从 1 起，对应正文 [n]。 */
interface ChunkHit {
  index: number
  id: number | string
  filename: string
  chunkIndex: number
  content: string
  score: number
}

const userStore = useUserStore()

const form = ref<{
  title: string
  topic: string
  keyPoints: string
  kbId: number | null
  templateId: number | null
}>({
  title: '',
  topic: '',
  keyPoints: '',
  kbId: null,
  templateId: null
})

const content = ref('')
const generating = ref(false)
const rewriting = ref(false)
const saving = ref(false)
const currentReportId = ref<number | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const templates = ref<Template[]>([])

// 引用溯源状态 —— 后端通过 SSE chunks 事件推送 RAG top-k 命中列表。
const chunks = ref<ChunkHit[]>([])
const highlightedCite = ref<number | null>(null)

// 编辑 / 预览切换。生成/改写过程中默认编辑态（让用户看流式 token），完成后跳到预览态以显示角标。
const viewMode = ref<'edit' | 'preview'>('edit')
const renderedHtml = computed(() => renderReportMarkdown(content.value))

// In-flight abort controller for streaming requests
let activeController: AbortController | null = null

const wordCount = computed(() => content.value.length)
const canGenerate = computed(
  () => !generating.value && !!form.value.title.trim() && !!form.value.topic.trim()
)

onMounted(async () => {
  try {
    const [kbRes, tmplRes] = await Promise.all([
      getKnowledgeBases(),
      getTemplates()
    ])
    // request interceptor returns the ApiResponse directly, so `.data` is the payload
    knowledgeBases.value = ((kbRes as any).data as KnowledgeBase[]) || []
    templates.value = ((tmplRes as any).data as Template[]) || []
  } catch (e) {
    console.error('加载知识库/模板失败:', e)
  }
})

onBeforeUnmount(() => {
  // Cancel any in-flight SSE stream on unmount to avoid leaks
  activeController?.abort()
})

interface SseHandlers {
  onToken?: (payload: string) => void
  onChunks?: (hits: ChunkHit[]) => void
  onDone?: (payload: string) => void
  onError?: (msg: string) => void
}

/**
 * 解析 fetch Response 上的 SSE 流，按 `event:` 名称分发：
 *   - token  → onToken（正文 token，默认追加到 content）
 *   - chunks → onChunks（RAG 命中列表 JSON）
 *   - done   → onDone（结束信号，同时会令读取循环退出）
 *   - error  → onError（后端在生成过程中抛错）
 *
 * 关键要点：
 *   1. TextDecoder 开启 stream 模式，保证跨网络包的多字节中文字符不被截断；
 *   2. 每帧以空行（\n\n）结尾，拆分后把不完整的尾部留在 buffer；
 *   3. data 行支持同帧多条，按 \n 连接（与 SSE 规范一致）；
 *   4. 同一帧只会有一个 event 名；未声明 event 时按 "message" 处理，我们当作 token。
 */
async function consumeSseStream(response: Response, handlers: SseHandlers): Promise<void> {
  if (!response.ok || !response.body) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  const dispatchFrame = (frame: string) => {
    let eventName = 'message'
    const dataParts: string[] = []
    for (const rawLine of frame.split('\n')) {
      if (!rawLine || rawLine.startsWith(':')) continue // keepalive / 注释
      if (rawLine.startsWith('event:')) {
        eventName = rawLine.slice(6).trim()
      } else if (rawLine.startsWith('data:')) {
        dataParts.push(rawLine.slice(5).replace(/^ /, ''))
      }
    }
    const data = dataParts.join('\n')
    switch (eventName) {
      case 'chunks': {
        if (!handlers.onChunks) return
        try {
          const parsed = JSON.parse(data) as ChunkHit[]
          handlers.onChunks(Array.isArray(parsed) ? parsed : [])
        } catch (e) {
          console.warn('SSE chunks payload parse failed:', e)
        }
        return
      }
      case 'done':
        finished = true
        handlers.onDone?.(data)
        return
      case 'error':
        finished = true
        handlers.onError?.(data)
        return
      case 'token':
      case 'message':
      default:
        // 兜底：未知 event 也当 token 处理，避免漏掉内容
        if (data) handlers.onToken?.(data)
    }
  }

  try {
    while (!finished) {
      const { done: readerDone, value } = await reader.read()
      if (readerDone) break

      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\n\n/)
      buffer = frames.pop() ?? ''

      for (const frame of frames) {
        if (!frame.trim()) continue
        dispatchFrame(frame)
        if (finished) break
      }
    }

    // Flush 最后一帧（没有尾随空行）
    if (!finished && buffer.trim()) dispatchFrame(buffer)
  } finally {
    try {
      reader.releaseLock()
    } catch (_) {
      /* noop */
    }
  }
}

async function handleGenerate() {
  if (!canGenerate.value) return

  generating.value = true
  content.value = ''
  chunks.value = []
  currentReportId.value = null
  // 生成期保持 edit 态，方便用户看 token 流；完成后再切到 preview。
  viewMode.value = 'edit'

  const controller = new AbortController()
  activeController = controller

  let streamError: string | null = null

  try {
    // 1. Create draft report first to get an ID
    const createRes = await createReport({
      title: form.value.title.trim(),
      topic: form.value.topic.trim(),
      keyPoints: form.value.keyPoints,
      kbId: form.value.kbId,
      templateId: form.value.templateId
    })
    const draft = (createRes as any).data as { id: number } | null
    if (!draft?.id) {
      throw new Error('创建报告草稿失败')
    }
    currentReportId.value = draft.id

    // 2. Open SSE stream via fetch (EventSource cannot send Authorization header)
    const token = userStore.token || localStorage.getItem('token') || ''
    const resp = await fetch(`/api/v1/reports/${draft.id}/generate`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream'
      },
      signal: controller.signal
    })

    if (!resp.ok) {
      throw new Error(`生成接口返回 ${resp.status}`)
    }

    await consumeSseStream(resp, {
      onToken: (t) => { content.value += t },
      onChunks: (hits) => { chunks.value = hits },
      onError: (msg) => { streamError = msg || '生成失败' }
    })

    if (streamError) throw new Error(streamError)

    ElMessage.success('报告生成完成')
    // 有正文之后默认切到预览态，用户第一眼就能看到角标效果。
    if (content.value) viewMode.value = 'preview'
  } catch (e: any) {
    if (e?.name === 'AbortError') {
      ElMessage.info('已取消生成')
    } else {
      console.error('生成失败:', e)
      ElMessage.error(e?.message || '生成失败，请重试')
    }
  } finally {
    generating.value = false
    activeController = null
  }
}

async function handleRewrite(mode: RewriteMode) {
  if (!currentReportId.value || !content.value) return

  let instruction: string | undefined

  if (mode === 'DATA_UPDATE') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请输入数据更新指令（例如：把 2024 年数据换成 2025 年 / 更新市场规模到最新季度）',
        '数据更新',
        {
          confirmButtonText: '开始改写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请描述要替换/更新的数据...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入数据更新指令'
        }
      )
      instruction = value
    } catch {
      return
    }
  } else if (mode === 'ANGLE_SHIFT') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请输入视角调整指令（例如：面向领导的简报风格 / 从企业视角切换到行业监管视角）',
        '视角调整',
        {
          confirmButtonText: '开始改写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请描述目标视角 / 受众...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入视角指令'
        }
      )
      instruction = value
    } catch {
      return
    }
  }

  rewriting.value = true
  const originalContent = content.value
  content.value = '' // clear to receive streamed rewritten content
  // 改写期同样先回到编辑态以便查看 token；完成后若仍有正文再切回预览。
  viewMode.value = 'edit'

  const controller = new AbortController()
  activeController = controller

  let streamError: string | null = null

  try {
    const token = userStore.token || localStorage.getItem('token') || ''
    const resp = await fetch(`/api/v1/reports/${currentReportId.value}/rewrite`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      },
      body: JSON.stringify({
        mode,
        targetParagraph: originalContent,
        instruction
      }),
      signal: controller.signal
    })

    if (!resp.ok) {
      throw new Error(`改写接口返回 ${resp.status}`)
    }

    await consumeSseStream(resp, {
      onToken: (t) => { content.value += t },
      // 改写不触发 RAG 重检索，后端一般不会再发 chunks；若发则刷新。
      onChunks: (hits) => { chunks.value = hits },
      onError: (msg) => { streamError = msg || '改写失败' }
    })

    if (streamError) throw new Error(streamError)

    ElMessage.success('改写完成')
    if (content.value) viewMode.value = 'preview'
  } catch (e: any) {
    // Roll back to original on failure
    content.value = originalContent
    if (e?.name === 'AbortError') {
      ElMessage.info('已取消改写')
    } else {
      console.error('改写失败:', e)
      ElMessage.error(e?.message || '改写失败，请重试')
    }
  } finally {
    rewriting.value = false
    activeController = null
  }
}

/**
 * 正文 `[n]` 角标点击 → 滚动到右侧溯源卡 + 短暂高亮。
 * 用事件委托：markdown 是 v-html 渲染的，Vue 的 @click 绑不到动态 sup 上。
 */
function handleCiteClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target) return
  const sup = target.closest('sup.cite') as HTMLElement | null
  if (!sup) return
  const idxStr = sup.dataset.idx
  if (!idxStr) return
  const idx = Number(idxStr)
  if (!Number.isFinite(idx)) return
  scrollToCitation(idx)
}

function scrollToCitation(n: number) {
  const el = document.getElementById(`cite-card-${n}`)
  if (!el) {
    ElMessage.warning(`未找到第 ${n} 条引用`)
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  highlightedCite.value = n
  // 1.5s 后撤销高亮；若期间又点了别的则不动它
  window.setTimeout(() => {
    if (highlightedCite.value === n) highlightedCite.value = null
  }, 1500)
}

async function saveReport() {
  if (!currentReportId.value) return
  saving.value = true
  try {
    await updateReport(currentReportId.value, {
      title: form.value.title,
      content: content.value
    })
    ElMessage.success('保存成功')
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    saving.value = false
  }
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(content.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // Fallback for browsers without clipboard API
    const ta = document.createElement('textarea')
    ta.value = content.value
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败，请手动选择文本')
    } finally {
      document.body.removeChild(ta)
    }
  }
}
</script>

<style scoped>
.workspace-container {
  display: flex;
  gap: 20px;
  height: calc(100vh - 140px);
}
.config-panel {
  width: 360px;
  flex-shrink: 0;
}
.config-card {
  height: 100%;
  overflow: auto;
}
.editor-panel {
  flex: 1;
  min-width: 0;
}
.editor-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.editor-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
}
.editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 400px;
}
.generating-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  color: #409eff;
  font-size: 14px;
}
.report-editor {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.8;
}
.report-editor :deep(textarea) {
  min-height: 600px;
}

/* Markdown 预览区 —— 保持与编辑器一致的行高/字号 */
.report-preview {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.85;
  color: #303133;
  padding: 4px 8px 24px;
  min-height: 600px;
  overflow-wrap: break-word;
}
.report-preview :deep(h1),
.report-preview :deep(h2),
.report-preview :deep(h3) {
  font-weight: 600;
  color: #1f2d3d;
  margin: 1.2em 0 0.6em;
}
.report-preview :deep(p) { margin: 0.6em 0; }
.report-preview :deep(ul),
.report-preview :deep(ol) { margin: 0.5em 0; padding-left: 1.4em; }
.report-preview :deep(code) {
  background: #f4f6fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 0.92em;
}
.report-preview :deep(sup.cite) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, color 0.15s;
}
.report-preview :deep(sup.cite:hover) {
  background: #409eff;
  color: #fff;
}

/* 右侧引用溯源面板 */
.citations-panel {
  width: 340px;
  flex-shrink: 0;
}
.citations-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.citations-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
  padding: 12px;
}
.citations-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.citation-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafbfc;
  transition: background 0.3s, border-color 0.3s, box-shadow 0.3s;
}
.citation-card.highlighted {
  background: #ecf5ff;
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}
.citation-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}
.citation-idx {
  color: #409eff;
  font-weight: 700;
  font-size: 13px;
}
.citation-file {
  flex: 1;
  color: #606266;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.citation-body {
  font-size: 12.5px;
  line-height: 1.7;
  color: #4a4a4a;
  max-height: 8.5em;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
}
.citation-foot {
  margin-top: 6px;
  font-size: 11px;
  color: #909399;
  text-align: right;
}
</style>
