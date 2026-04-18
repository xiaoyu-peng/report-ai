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

          <el-form-item label="外部舆情数据">
            <el-button
              size="small"
              type="success"
              plain
              style="width: 100%"
              :disabled="generating"
              @click="showMcpDialog = true"
            >
              <el-icon><Connection /></el-icon>
              引入晴天 MCP 数据
            </el-button>
            <div v-if="mcpArticles.length > 0" class="mcp-imported-hint">
              已引入 {{ mcpArticles.length }} 篇舆情文章
            </div>
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
                @command="handleExport"
              >
                <el-button size="small" type="primary" plain :loading="exporting">
                  <el-icon><Download /></el-icon>
                  导出
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="docx">导出 Word（.docx）</el-dropdown-item>
                    <el-dropdown-item command="pdf">导出 PDF（含角标）</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
                    <el-dropdown-item command="CONTINUATION" divided>续写新章节（原稿保留）</el-dropdown-item>
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

        <!-- AI 生成进度条 -->
        <div v-if="generating && progressStep" class="progress-bar">
          <div class="progress-steps">
            <div
              v-for="(label, i) in progressSteps"
              :key="i"
              class="progress-step"
              :class="{
                active: i + 1 === progressStep.stepIndex,
                done: i + 1 < progressStep.stepIndex
              }"
            >
              <div class="step-dot">
                <el-icon v-if="i + 1 < progressStep.stepIndex"><Check /></el-icon>
                <el-icon v-else-if="i + 1 === progressStep.stepIndex" class="is-loading"><Loading /></el-icon>
                <span v-else>{{ i + 1 }}</span>
              </div>
              <span class="step-label">{{ label }}</span>
            </div>
          </div>
        </div>

        <!-- Streaming: Markdown 渐进渲染 + 打字光标 -->
        <div
          v-if="content && (generating || rewriting) && viewMode === 'edit'"
          ref="streamingEl"
          class="streaming-editor"
          @click="handleCiteClick"
          v-html="streamingHtml"
        />

        <!-- Editable textarea（非流式时手动编辑） -->
        <el-input
          v-if="content && !generating && !rewriting && viewMode === 'edit'"
          v-model="content"
          type="textarea"
          :rows="30"
          resize="none"
          class="report-editor"
          placeholder="报告内容将在此处流式显示..."
        />

        <!-- Markdown 预览（含 [n] 角标，点击跳转溯源面板） -->
        <div
          v-else-if="content && viewMode === 'preview'"
          ref="previewEl"
          class="report-preview"
          @click="handleCiteClick"
          v-html="renderedHtml"
        />
      </el-card>
    </div>

    <!-- MCP 数据引入弹窗 -->
    <el-dialog v-model="showMcpDialog" title="引入晴天 MCP 舆情数据" width="700px" top="6vh">
      <div class="mcp-dialog-body">
        <el-input
          v-model="mcpKeyword"
          placeholder="输入关键词搜索舆情文章..."
          :loading="mcpSearching"
          @keyup.enter="searchMcpArticles"
        >
          <template #append>
            <el-button @click="searchMcpArticles" :loading="mcpSearching">搜索</el-button>
          </template>
        </el-input>

        <div v-if="mcpSearchResults.length > 0" class="mcp-results">
          <div class="mcp-results-header">
            <span>搜索结果（{{ mcpSearchResults.length }} 篇）</span>
            <el-button size="small" type="primary" @click="selectAllMcp">全选</el-button>
          </div>
          <div class="mcp-results-list">
            <div
              v-for="(article, i) in mcpSearchResults"
              :key="i"
              class="mcp-article-item"
              :class="{ selected: mcpSelectedIndices.has(i) }"
              @click="toggleMcpSelect(i)"
            >
              <div class="mcp-article-title">{{ article.title || article.articleTitle || `文章 ${i + 1}` }}</div>
              <div class="mcp-article-meta">
                <span v-if="article.source || article.mediaName">{{ article.source || article.mediaName }}</span>
                <span v-if="article.publishTime || article.publishDate">{{ article.publishTime || article.publishDate }}</span>
              </div>
              <div v-if="article.summary || article.content" class="mcp-article-summary">
                {{ (article.summary || article.content || '').substring(0, 120) }}...
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!mcpSearching" description="输入关键词搜索舆情文章" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="showMcpDialog = false">取消</el-button>
        <el-button type="primary" :disabled="mcpSelectedIndices.size === 0" @click="confirmMcpImport">
          确认引入（{{ mcpSelectedIndices.size }} 篇）
        </el-button>
      </template>
    </el-dialog>

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
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
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
  Collection,
  Download,
  Connection,
  Check
} from '@element-plus/icons-vue'
import { getKnowledgeBases, type KnowledgeBase } from '@/api/knowledge'
import {
  getTemplates,
  createReport,
  updateReport,
  mcpSearchArticles,
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
const exporting = ref(false)
const currentReportId = ref<number | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const templates = ref<Template[]>([])

// 引用溯源状态 —— 后端通过 SSE chunks 事件推送 RAG top-k 命中列表。
const chunks = ref<ChunkHit[]>([])
const highlightedCite = ref<number | null>(null)

// MCP 舆情数据引入状态
const showMcpDialog = ref(false)
const mcpKeyword = ref('')
const mcpSearching = ref(false)
const mcpSearchResults = ref<any[]>([])
const mcpSelectedIndices = ref<Set<number>>(new Set())
const mcpArticles = ref<any[]>([])

// AI 生成进度
const progressSteps = ['检索知识库', '分析风格与结构', '获取舆情数据', 'AI 撰写报告', '完稿与版本保存']
const progressStep = ref<{ step: string; stepIndex: number; totalSteps: number } | null>(null)

// 编辑 / 预览切换。生成/改写过程中默认编辑态（让用户看流式 token），完成后跳到预览态以显示角标。
const viewMode = ref<'edit' | 'preview'>('edit')
const renderedHtml = computed(() => renderReportMarkdown(content.value))
const streamingHtml = computed(() => {
  if (!content.value) return ''
  const html = renderReportMarkdown(content.value)
  return html + '<span class="typing-cursor">▍</span>'
})

const previewEl = ref<HTMLDivElement | null>(null)
const streamingEl = ref<HTMLDivElement | null>(null)

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
  onProgress?: (step: string, stepIndex: number, totalSteps: number) => void
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
      case 'progress': {
        if (!handlers.onProgress) return
        try {
          const parsed = JSON.parse(data)
          handlers.onProgress(parsed.step, parsed.stepIndex, parsed.totalSteps)
        } catch (e) {
          console.warn('SSE progress payload parse failed:', e)
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
      onToken: (t) => {
        content.value += t
        nextTick(() => {
          if (streamingEl.value) {
            streamingEl.value.scrollTop = streamingEl.value.scrollHeight
          }
        })
      },
      onChunks: (hits) => { chunks.value = hits },
      onProgress: (step, stepIndex, totalSteps) => {
        progressStep.value = { step, stepIndex, totalSteps }
      },
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
    progressStep.value = null
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
  } else if (mode === 'CONTINUATION') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请描述要续写的新章节（例如：补一章"风险与对策" / 延伸讨论 2026 年趋势）',
        '续写新章节',
        {
          confirmButtonText: '开始续写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '新章节的主题或要点...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入新章节主题'
        }
      )
      instruction = value
    } catch {
      return
    }
  }

  rewriting.value = true
  const originalContent = content.value
  // CONTINUATION：原稿保留不变，SSE 流出来的新章节追加到末尾；其他模式清空后接收整稿。
  const isContinuation = mode === 'CONTINUATION'
  if (!isContinuation) content.value = ''
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

    // 续写：首个 token 到达前先插一个段落分隔，避免新章节与原稿末行粘连。
    let continuationSeparatorInserted = false

    await consumeSseStream(resp, {
      onToken: (t) => {
        if (isContinuation && !continuationSeparatorInserted) {
          if (!content.value.endsWith('\n\n')) {
            content.value += content.value.endsWith('\n') ? '\n' : '\n\n'
          }
          continuationSeparatorInserted = true
        }
        content.value += t
        nextTick(() => {
          if (streamingEl.value) {
            streamingEl.value.scrollTop = streamingEl.value.scrollHeight
          }
        })
      },
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

async function searchMcpArticles() {
  if (!mcpKeyword.value.trim()) return
  mcpSearching.value = true
  mcpSearchResults.value = []
  mcpSelectedIndices.value = new Set()
  try {
    const res = await mcpSearchArticles(mcpKeyword.value.trim())
    const data = (res as any).data
    if (data && typeof data === 'object') {
      if (Array.isArray(data)) {
        mcpSearchResults.value = data
      } else if (data.list && Array.isArray(data.list)) {
        mcpSearchResults.value = data.list
      } else if (data.data && Array.isArray(data.data)) {
        mcpSearchResults.value = data.data
      } else if (data.records && Array.isArray(data.records)) {
        mcpSearchResults.value = data.records
      } else {
        mcpSearchResults.value = [data]
      }
    }
  } catch (e) {
    console.error('MCP 搜索失败:', e)
    ElMessage.error('搜索舆情数据失败')
  } finally {
    mcpSearching.value = false
  }
}

function toggleMcpSelect(index: number) {
  const newSet = new Set(mcpSelectedIndices.value)
  if (newSet.has(index)) {
    newSet.delete(index)
  } else {
    newSet.add(index)
  }
  mcpSelectedIndices.value = newSet
}

function selectAllMcp() {
  if (mcpSelectedIndices.value.size === mcpSearchResults.value.length) {
    mcpSelectedIndices.value = new Set()
  } else {
    mcpSelectedIndices.value = new Set(mcpSearchResults.value.map((_, i) => i))
  }
}

function confirmMcpImport() {
  const selected = mcpSelectedIndices.value
  const articles = Array.from(selected).map(i => mcpSearchResults.value[i])
  mcpArticles.value = [...mcpArticles.value, ...articles]
  showMcpDialog.value = false
  ElMessage.success(`已引入 ${articles.length} 篇舆情文章`)
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

function safeFilename(raw: string, fallback: string): string {
  if (!raw) return fallback
  const cleaned = raw.replace(/[\\/:*?"<>|\x00-\x1f]/g, '').trim()
  return cleaned.slice(0, 80) || fallback
}

async function handleExport(kind: 'docx' | 'pdf') {
  if (!currentReportId.value || !content.value) return
  exporting.value = true
  try {
    if (kind === 'docx') {
      await exportDocx()
    } else {
      await exportPdf()
    }
  } catch (e: any) {
    console.error(`导出 ${kind} 失败:`, e)
    ElMessage.error(e?.message || '导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

/**
 * Word 导出：后端 /export/docx 产出 .docx，走 fetch + Authorization header，
 * 再把 Blob 塞进临时 <a download> 触发保存。EventSource/window.open 无法带 JWT。
 */
async function exportDocx() {
  const token = userStore.token || localStorage.getItem('token') || ''
  const resp = await fetch(`/api/v1/reports/${currentReportId.value}/export/docx`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!resp.ok) throw new Error(`导出接口返回 ${resp.status}`)
  const blob = await resp.blob()
  const filename = safeFilename(form.value.title, 'report') + '.docx'
  triggerBlobDownload(blob, filename)
  ElMessage.success('Word 已导出')
}

/**
 * PDF 导出：html2pdf 从预览 DOM 抓图转 PDF，保留 [n] 角标样式。
 * 若当前不是预览态，先切过去并等 DOM 更新，否则抓到的是空节点。
 */
async function exportPdf() {
  if (viewMode.value !== 'preview') {
    viewMode.value = 'preview'
    await nextTick()
  }
  const el = previewEl.value
  if (!el) throw new Error('预览内容未就绪')
  // 动态 import 避免首屏 bundle 体积被 html2pdf 拖胖（jsPDF + html2canvas）
  const html2pdf = (await import('html2pdf.js')).default
  const filename = safeFilename(form.value.title, 'report') + '.pdf'
  await html2pdf()
    .set({
      filename,
      margin: [15, 15, 20, 15], // mm: 上 右 下 左
      html2canvas: { scale: 2, useCORS: true },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      // pagebreak 不在 html2pdf.js 的 .d.ts 里但运行期支持；用 any 绕过类型
      ...({ pagebreak: { mode: ['css', 'legacy'] } } as any)
    })
    .from(el)
    .save()
  ElMessage.success('PDF 已导出')
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 稍延迟释放，避免部分浏览器触发下载前就被回收
  setTimeout(() => URL.revokeObjectURL(url), 2000)
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
.report-preview,
.streaming-editor {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.85;
  color: #303133;
  padding: 4px 8px 24px;
  min-height: 600px;
  overflow-wrap: break-word;
}
.streaming-editor {
  overflow-y: auto;
}

/* 打字光标闪烁动画 */
.streaming-editor :deep(.typing-cursor) {
  display: inline;
  color: #409eff;
  font-weight: 400;
  animation: cursor-blink 0.8s step-end infinite;
}
@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.report-preview :deep(h1),
.report-preview :deep(h2),
.report-preview :deep(h3),
.streaming-editor :deep(h1),
.streaming-editor :deep(h2),
.streaming-editor :deep(h3) {
  font-weight: 600;
  color: #1f2d3d;
  margin: 1.2em 0 0.6em;
}
.report-preview :deep(p),
.streaming-editor :deep(p) { margin: 0.6em 0; }
.report-preview :deep(ul),
.report-preview :deep(ol),
.streaming-editor :deep(ul),
.streaming-editor :deep(ol) { margin: 0.5em 0; padding-left: 1.4em; }
.report-preview :deep(code),
.streaming-editor :deep(code) {
  background: #f4f6fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 0.92em;
}
.report-preview :deep(sup.cite),
.streaming-editor :deep(sup.cite) {
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
.report-preview :deep(sup.cite:hover),
.streaming-editor :deep(sup.cite:hover) {
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

/* MCP 引入相关 */
.mcp-imported-hint {
  font-size: 12px;
  color: #67c23a;
  margin-top: 4px;
}
.mcp-dialog-body {
  min-height: 300px;
}
.mcp-results {
  margin-top: 16px;
}
.mcp-results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}
.mcp-results-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mcp-article-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.mcp-article-item:hover {
  border-color: #409eff;
}
.mcp-article-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}
.mcp-article-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}
.mcp-article-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.mcp-article-summary {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
}

/* AI 生成进度条 */
.progress-bar {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #ecf5ff 100%);
  border-radius: 8px;
  border: 1px solid #d9ecff;
}
.progress-steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
}
.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: #e4e7ed;
  color: #909399;
  transition: all 0.3s;
}
.progress-step.done .step-dot {
  background: #67c23a;
  color: #fff;
}
.progress-step.active .step-dot {
  background: #409eff;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.2);
}
.step-label {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}
.progress-step.active .step-label {
  color: #409eff;
  font-weight: 600;
}
.progress-step.done .step-label {
  color: #67c23a;
}
</style>
