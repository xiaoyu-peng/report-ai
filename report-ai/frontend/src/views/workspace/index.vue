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
                    <el-dropdown-item command="POLISH">润色优化</el-dropdown-item>
                    <el-dropdown-item command="EXPAND">扩写展开</el-dropdown-item>
                    <el-dropdown-item command="CONDENSE">精简压缩</el-dropdown-item>
                    <el-dropdown-item command="CUSTOM" divided>自定义指令...</el-dropdown-item>
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
          v-if="content || generating"
          v-model="content"
          type="textarea"
          :rows="30"
          resize="none"
          class="report-editor"
          :readonly="generating || rewriting"
          placeholder="报告内容将在此处流式显示..."
        />
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
  ArrowDown
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

/**
 * Read an SSE stream from a fetch Response and accumulate data chunks into `content`.
 *
 * SSE frame format:
 *   data: <chunk>\n\n
 *   event: done\ndata: [DONE]\n\n
 *
 * Strategy:
 *   1. Decode UTF-8 incrementally with `TextDecoder({ stream: true })` so multi-byte
 *      Chinese chars that straddle chunk boundaries are preserved.
 *   2. Buffer the decoded text. Each full frame ends with `\n\n`, so we split on
 *      `\n\n` and keep the last (possibly incomplete) piece in the buffer.
 *   3. For each complete frame, read every line starting with `data:` and either
 *      handle the `[DONE]` sentinel (return true) or append the payload to content.
 *   4. Empty `data:` lines are treated as a literal newline, preserving paragraph
 *      breaks the backend emits via `\n`-only chunks.
 */
async function consumeSseStream(response: Response): Promise<void> {
  if (!response.ok || !response.body) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let done = false

  try {
    while (!done) {
      const { done: readerDone, value } = await reader.read()
      if (readerDone) break

      buffer += decoder.decode(value, { stream: true })

      // A frame is terminated by a blank line (\n\n). Split keeping the tail.
      const frames = buffer.split(/\n\n/)
      buffer = frames.pop() ?? '' // incomplete trailing frame stays in buffer

      for (const frame of frames) {
        if (!frame.trim()) continue
        const dataParts: string[] = []
        for (const rawLine of frame.split('\n')) {
          if (rawLine.startsWith('data:')) {
            // Preserve internal whitespace; only strip the "data:" prefix and at most one leading space
            const payload = rawLine.slice(5).replace(/^ /, '')
            if (payload === '[DONE]') {
              done = true
              break
            }
            dataParts.push(payload)
          }
          // Lines like `event: done`, `:keepalive`, `id: 1` are intentionally ignored
        }
        if (dataParts.length > 0) {
          content.value += dataParts.join('\n')
        }
      }
    }

    // Flush any remaining buffered data (last frame without trailing \n\n)
    if (buffer.trim()) {
      for (const rawLine of buffer.split('\n')) {
        if (rawLine.startsWith('data:')) {
          const payload = rawLine.slice(5).replace(/^ /, '')
          if (payload !== '[DONE]') {
            content.value += payload
          }
        }
      }
    }
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
  currentReportId.value = null

  const controller = new AbortController()
  activeController = controller

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

    await consumeSseStream(resp)

    ElMessage.success('报告生成完成')
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

  if (mode === 'CUSTOM') {
    try {
      const { value } = await ElMessageBox.prompt(
        '请输入自定义改写指令（例如：让语气更专业 / 增加数据支撑 / 调整为 Q&A 结构）',
        '自定义改写',
        {
          confirmButtonText: '开始改写',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请输入改写要求...',
          inputValidator: (v) => (v && v.trim().length > 0) || '请输入改写指令'
        }
      )
      instruction = value
    } catch {
      return // user cancelled
    }
  }

  rewriting.value = true
  const originalContent = content.value
  content.value = '' // clear to receive streamed rewritten content

  const controller = new AbortController()
  activeController = controller

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

    await consumeSseStream(resp)
    ElMessage.success('改写完成')
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
</style>
