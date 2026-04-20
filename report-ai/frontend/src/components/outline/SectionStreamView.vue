<template>
  <div class="section-stream">
    <div class="header">
      <div class="title-group">
        <span class="title">章节生成进度（{{ doneCount }}/{{ sections.length }}）</span>
        <el-tag v-if="importedCount > 0" type="info" size="small" effect="plain">
          已从原报告导入 {{ importedCount }} 章
        </el-tag>
      </div>
      <div>
        <el-button size="small" type="primary" plain :loading="streaming" @click="startAll">
          {{ streaming ? '生成中…' : (pendingCount > 0 ? `生成待处理 ${pendingCount} 章` : '全部已生成') }}
        </el-button>
        <el-button size="small" :disabled="doneCount === 0" @click="previewAssemble">
          合并全文
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="importing"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 8px"
      title="正在从原报告解析章节并导入…"
    />

    <el-card
      v-for="s in sections"
      :key="s.id"
      class="section-card"
      :class="['status-' + s.status]"
      shadow="hover"
    >
      <div class="card-head">
        <span class="card-title">{{ s.sectionIndex + 1 }}. {{ s.title || '未命名章节' }}</span>
        <el-tag v-if="isImported(s)" type="info" size="small" effect="plain">导入</el-tag>
        <el-tag v-else-if="s.status === 'done'" type="success" size="small" effect="plain">本次生成</el-tag>
        <el-tag :type="statusType(s.status)" size="small">{{ statusLabel(s.status) }}</el-tag>
        <el-button
          v-if="s.status === 'failed' || s.status === 'pending'"
          size="small" type="warning" plain
          @click="streamOne(s.sectionIndex)"
        >
          {{ s.status === 'failed' ? '重试' : '生成' }}
        </el-button>
        <el-button
          v-if="s.status === 'done'"
          size="small" plain
          @click="regenerate(s.sectionIndex)"
        >
          重新生成
        </el-button>
      </div>
      <el-progress
        v-if="s.status === 'generating'"
        :percentage="0" :indeterminate="true" :show-text="false"
      />
      <div v-if="s.content" class="card-content">
        {{ s.content.slice(0, 280) }}{{ s.content.length > 280 ? '…' : '' }}
      </div>
      <div class="card-meta">
        字数 {{ s.wordCount || 0 }} · 引用 {{ s.citationCount || 0 }} 处
      </div>
    </el-card>

    <el-empty v-if="sections.length === 0 && !importing" description="还没有章节，请先在「大纲」面板提交大纲" />

    <el-dialog
      v-model="previewVisible"
      title="合并预览"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="success"
        :closable="false"
        style="margin-bottom: 16px"
        v-if="importedCount > 0 && newlyGeneratedCount > 0"
      >
        <template #title>
          <span style="font-weight: 600">
            追加模式：保留 {{ importedCount }} 章原文 + 合入 {{ newlyGeneratedCount }} 章新增
          </span>
        </template>
        <template #default>
          原报告章节按合并顺序保留；新增章节追加到末尾或原有位置。
        </template>
      </el-alert>
      <el-alert
        v-else-if="importedCount === 0"
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #title>
          <span style="font-weight: 600">合并将生成新报告正文</span>
        </template>
        <template #default>
          当前没有从原报告导入的章节，合并结果可能与原文差异较大。建议选择「保存为新版本」。
        </template>
      </el-alert>

      <div class="preview-stats">
        <el-tag type="success" size="large">已生成 {{ doneCount }} 章</el-tag>
        <el-tag type="info" size="large">预计 {{ previewWordCount }} 字</el-tag>
        <el-tag v-if="pendingCount > 0" type="warning" size="large">
          跳过 {{ pendingCount }} 章未生成
        </el-tag>
      </div>
      <div class="preview-content">
        <div class="preview-label">合并后内容预览（前 500 字）：</div>
        <div class="preview-text">{{ previewContent.slice(0, 500) }}{{ previewContent.length > 500 ? '…' : '' }}</div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAssemble(false)">
          覆盖当前报告
        </el-button>
        <el-button type="success" @click="confirmAssemble(true)">
          保存为新版本
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listSections, streamSection, assembleSections, initSections,
  ReportSection, OutlineItem,
} from '@/api/section'

const props = defineProps<{
  reportId: number
  kbIds?: number[]
  /** 原报告正文；首次进入且 DB 无 section 时，按标题切分自动导入为 done 章节。 */
  initialContent?: string
}>()
const emit = defineEmits<{
  (e: 'assembled', content: string): void
  (e: 'imported', count: number): void
}>()

const sections = ref<ReportSection[]>([])
const streaming = ref(false)
const importing = ref(false)
/** 记录哪些章节是导入来的（而非本次生成），用于 UI 标签。按标题记录。 */
const importedTitles = ref<Set<string>>(new Set())
const eventSources = new Set<EventSource>()
const previewVisible = ref(false)

const doneCount = computed(() => sections.value.filter(s => s.status === 'done').length)
const pendingCount = computed(() => sections.value.filter(s => s.status === 'pending' || s.status === 'failed').length)
const importedCount = computed(() =>
  sections.value.filter(s => s.status === 'done' && s.title && importedTitles.value.has(s.title.trim())).length
)
const newlyGeneratedCount = computed(() => doneCount.value - importedCount.value)
const previewContent = computed(() =>
  sections.value
    .filter(s => s.status === 'done')
    .map(s => (s.title ? `## ${s.title}\n\n` : '') + (s.content || ''))
    .join('\n\n')
)
const previewWordCount = computed(() => previewContent.value.length)

function isImported(s: ReportSection): boolean {
  return s.status === 'done' && !!s.title && importedTitles.value.has(s.title.trim())
}

onMounted(init)
onBeforeUnmount(() => closeAll())

// 父组件动态传 initialContent 时，若尚未导入过，补做一次
watch(() => props.initialContent, async (v) => {
  if (!v || sections.value.length > 0) return
  await maybeImportFromContent(v)
})

async function init() {
  await load()
  if (sections.value.length === 0 && props.initialContent?.trim()) {
    await maybeImportFromContent(props.initialContent)
  } else {
    // 已有章节：根据内容判定哪些是"导入来源"（有 content 且 citationCount=0 且 finishedAt 早于 startedAt 不好判断）
    // 保守策略：所有已 done 章节暂不标"导入"；用户重新生成后再区分。
  }
}

async function load() {
  const res = await listSections(props.reportId)
  sections.value = res.data || []
}

async function maybeImportFromContent(content: string) {
  const parsed = parseReportToSections(content)
  if (parsed.length === 0) return
  importing.value = true
  try {
    const outline: OutlineItem[] = parsed.map(p => ({
      title: p.title,
      content: p.content,
      status: 'done',
    }))
    await initSections(props.reportId, outline)
    parsed.forEach(p => importedTitles.value.add(p.title.trim()))
    await load()
    emit('imported', parsed.length)
    ElMessage.success(`已从原报告导入 ${parsed.length} 个章节作为基础`)
  } catch (e: any) {
    console.error('自动导入章节失败:', e)
    ElMessage.warning('自动导入原报告章节失败，可在「大纲」面板手动编辑')
  } finally {
    importing.value = false
  }
}

/**
 * 把 markdown / 中文编号格式的正文切成章节。
 * 识别的 level-1 标题：
 *   - `# 标题` / `## 标题`
 *   - `一、标题` / `二、标题` ...
 *   - `1. 标题` / `1、标题`（可选）
 * 一级之下的子标题（如 `（一）`、`### `）保留为章节内容的一部分，不拆分。
 */
function parseReportToSections(raw: string): { title: string; content: string }[] {
  if (!raw || !raw.trim()) return []
  const lines = raw.split(/\r?\n/)
  const headingRe = /^\s*(#{1,2}\s+.+|[一二三四五六七八九十百]+[、.．]\s*.+|\d{1,2}[、.．]\s*.+)\s*$/
  const sections: { title: string; content: string[] }[] = []
  let current: { title: string; content: string[] } | null = null
  let preamble: string[] = []

  for (const line of lines) {
    if (headingRe.test(line)) {
      if (current) sections.push(current)
      const title = line.trim().replace(/^#{1,2}\s+/, '').trim()
      current = { title, content: [] }
    } else if (current) {
      current.content.push(line)
    } else {
      preamble.push(line)
    }
  }
  if (current) sections.push(current)

  const preambleText = preamble.join('\n').trim()
  if (preambleText && sections.length > 0) {
    sections[0].content.unshift(preamble.join('\n'))
  } else if (preambleText && sections.length === 0) {
    sections.push({ title: '正文', content: preamble })
  }

  return sections
    .map(s => ({ title: s.title, content: s.content.join('\n').trim() }))
    .filter(s => s.title)
}

function closeAll() {
  eventSources.forEach(es => es.close())
  eventSources.clear()
}

async function startAll() {
  if (sections.value.length === 0) {
    ElMessage.warning('请先提交大纲')
    return
  }
  const todo = sections.value.filter(s => s.status === 'pending' || s.status === 'failed')
  if (todo.length === 0) {
    ElMessage.info('没有待生成章节。如需重新生成某章，请点该章的「重新生成」')
    return
  }
  streaming.value = true
  try {
    for (const s of todo) {
      await new Promise<void>(resolve => streamOneInner(s.sectionIndex, resolve))
    }
  } finally {
    streaming.value = false
  }
}

function streamOne(idx: number) {
  streaming.value = true
  streamOneInner(idx, () => { streaming.value = false })
}

/** 用户对已 done 章节点"重新生成" —— 明确语义：该章走一遍 LLM，标记为本次生成（脱离"导入"）。 */
function regenerate(idx: number) {
  const target = sections.value.find(s => s.sectionIndex === idx)
  if (target?.title) importedTitles.value.delete(target.title.trim())
  streamOne(idx)
}

function streamOneInner(idx: number, onDone: () => void) {
  const target = sections.value.find(s => s.sectionIndex === idx)
  if (!target) { onDone(); return }
  target.status = 'generating'
  target.content = ''
  // 重新生成意味着脱离"导入"来源标记
  if (target.title) importedTitles.value.delete(target.title.trim())

  const es = streamSection(props.reportId, idx, props.kbIds || [])
  eventSources.add(es)

  es.addEventListener('start', () => { /* noop */ })
  es.addEventListener('chunks', () => { /* 检索结果，前端 detail 页有自己的面板，不在此重复 */ })
  es.addEventListener('token', (e: MessageEvent) => {
    try { target.content = (target.content || '') + JSON.parse(e.data) }
    catch { target.content = (target.content || '') + e.data }
  })
  es.addEventListener('done', (e: MessageEvent) => {
    try {
      const meta = JSON.parse(e.data)
      target.wordCount = meta.wordCount
      target.citationCount = meta.citationCount
    } catch {}
    target.status = 'done'
    es.close(); eventSources.delete(es)
    onDone()
  })
  es.addEventListener('error', () => {
    target.status = 'failed'
    es.close(); eventSources.delete(es)
    onDone()
  })
}

function previewAssemble() {
  if (doneCount.value === 0) {
    ElMessage.warning('还没有任何已完成章节')
    return
  }
  previewVisible.value = true
}

async function confirmAssemble(_asNewVersion: boolean) {
  const res = await assembleSections(props.reportId)
  previewVisible.value = false
  ElMessage.success('已合成全文写入报告')
  emit('assembled', (res.data as any) || '')
}

function statusType(s: string) {
  return ({ pending: 'info', generating: 'warning', done: 'success', failed: 'danger' } as any)[s] || 'info'
}
function statusLabel(s: string) {
  return ({ pending: '待生成', generating: '生成中', done: '已完成', failed: '失败' } as any)[s] || s
}

defineExpose({ reload: load })
</script>

<style scoped>
.section-stream { display: flex; flex-direction: column; gap: 10px; }
.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; background: #f5f7fa; border-radius: 6px;
}
.header .title { font-weight: 600; color: #303133; }
.title-group { display: flex; align-items: center; gap: 10px; }
.section-card { transition: all .2s; }
.section-card.status-generating { border-left: 3px solid #e6a23c; }
.section-card.status-failed { border-left: 3px solid #f56c6c; }
.section-card.status-done { border-left: 3px solid #67c23a; }
.card-head {
  display: flex; align-items: center; gap: 10px; margin-bottom: 8px;
}
.card-title { flex: 1; font-weight: 600; color: #303133; }
.card-content {
  font-size: 13px; color: #606266; line-height: 1.7;
  background: #fafafa; padding: 10px 12px; border-radius: 4px;
  margin: 8px 0; white-space: pre-wrap;
  max-height: 180px; overflow-y: auto;
}
.card-meta { font-size: 12px; color: #909399; }
.preview-stats { display: flex; gap: 10px; margin-bottom: 12px; }
.preview-content { background: #fafafa; padding: 12px; border-radius: 4px; }
.preview-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.preview-text { font-size: 13px; color: #606266; line-height: 1.7; white-space: pre-wrap; }
</style>
