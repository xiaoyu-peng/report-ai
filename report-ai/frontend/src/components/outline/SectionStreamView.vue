<template>
  <div class="section-stream">
    <div class="header">
      <span class="title">章节生成进度（{{ doneCount }}/{{ sections.length }}）</span>
      <div>
        <el-button size="small" type="primary" plain :loading="streaming" @click="startAll">
          {{ streaming ? '生成中…' : '开始/继续生成' }}
        </el-button>
        <el-button size="small" :disabled="doneCount === 0" @click="assembleNow">
          合成全文 → 报告
        </el-button>
      </div>
    </div>

    <el-card
      v-for="s in sections"
      :key="s.id"
      class="section-card"
      :class="['status-' + s.status]"
      shadow="hover"
    >
      <div class="card-head">
        <span class="card-title">{{ s.sectionIndex + 1 }}. {{ s.title || '未命名章节' }}</span>
        <el-tag :type="statusType(s.status)" size="small">{{ statusLabel(s.status) }}</el-tag>
        <el-button
          v-if="s.status === 'failed' || s.status === 'pending'"
          size="small" type="warning" plain
          @click="streamOne(s.sectionIndex)"
        >
          {{ s.status === 'failed' ? '重试' : '生成' }}
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

    <el-empty v-if="sections.length === 0" description="还没有章节，请先在「大纲」面板提交大纲" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { listSections, streamSection, assembleSections, ReportSection } from '@/api/section'

const props = defineProps<{ reportId: number; kbIds?: number[] }>()
const emit = defineEmits<{(e: 'assembled', content: string): void}>()

const sections = ref<ReportSection[]>([])
const streaming = ref(false)
const eventSources = new Set<EventSource>()

const doneCount = computed(() => sections.value.filter(s => s.status === 'done').length)

onMounted(load)
onBeforeUnmount(() => closeAll())

async function load() {
  const res = await listSections(props.reportId)
  sections.value = res.data || []
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
  streaming.value = true
  try {
    // 顺序跑：避免后端单线程瓶颈和 LLM rate limit
    for (const s of sections.value) {
      if (s.status === 'done') continue
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

function streamOneInner(idx: number, onDone: () => void) {
  const target = sections.value.find(s => s.sectionIndex === idx)
  if (!target) { onDone(); return }
  target.status = 'generating'
  target.content = ''

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

async function assembleNow() {
  const res = await assembleSections(props.reportId)
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
</style>
