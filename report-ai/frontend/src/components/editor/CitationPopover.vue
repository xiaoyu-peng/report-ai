<template>
  <el-popover
    v-model:visible="visible"
    :virtual-ref="targetEl"
    virtual-triggering
    placement="top"
    trigger="click"
    :width="380"
  >
    <div v-if="loading" class="cite-loading">加载引用中…</div>
    <div v-else-if="!citation" class="cite-empty">未找到引用 [{{ pendingMarker }}] 对应的源</div>
    <div v-else class="cite-card">
      <div class="cite-title">📄 {{ citation.docTitle || '未命名来源' }}</div>
      <div class="cite-meta">
        <span v-if="citation.pageStart">第 {{ citation.pageStart }}-{{ citation.pageEnd }} 页</span>
        <span class="cite-meta-sep" v-if="citation.pageStart"> · </span>
        <span>第 {{ citation.paragraphIndex + 1 }} 段</span>
      </div>
      <div class="cite-snippet">{{ citation.snippet || '（无原文片段）' }}</div>
      <div class="cite-actions">
        <el-button size="small" @click="openDoc">打开原文</el-button>
        <el-button size="small" type="danger" plain @click="exclude">排除此引用</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listCitations, excludeCitation, ReportCitation } from '@/api/citation'

const props = defineProps<{ reportId: number }>()

const visible = ref(false)
const targetEl = ref<HTMLElement | null>(null)
const citation = ref<ReportCitation | null>(null)
const loading = ref(false)
const pendingMarker = ref<number | null>(null)
let cache: ReportCitation[] = []

async function ensureCache() {
  if (cache.length > 0) return
  loading.value = true
  try {
    const res = await listCitations(props.reportId)
    cache = res.data || []
  } finally {
    loading.value = false
  }
}

async function show(el: HTMLElement, marker: number) {
  targetEl.value = el
  pendingMarker.value = marker
  visible.value = true
  await ensureCache()
  citation.value = cache.find(c => c.citationMarker === marker) || null
}

function openDoc() {
  if (citation.value?.kbId && citation.value?.docId) {
    window.open(`/knowledge/${citation.value.kbId}?docId=${citation.value.docId}`, '_blank')
  } else if (citation.value?.kbId) {
    window.open(`/knowledge/${citation.value.kbId}`, '_blank')
  }
}

async function exclude() {
  if (!citation.value) return
  await excludeCitation(props.reportId, citation.value.citationMarker)
  ElMessage.success('已排除此引用，下次重新生成时不会再用')
  cache = cache.filter(c => c.citationMarker !== citation.value!.citationMarker)
  visible.value = false
}

defineExpose({ show, refresh: () => { cache = [] } })
</script>

<style scoped>
.cite-loading, .cite-empty {
  padding: 12px; color: #909399; text-align: center; font-size: 13px;
}
.cite-title { font-weight: 600; margin-bottom: 6px; color: #303133; font-size: 14px; }
.cite-meta { font-size: 12px; color: #909399; margin-bottom: 8px; }
.cite-meta-sep { margin: 0 4px; }
.cite-snippet {
  font-size: 13px; line-height: 1.6; color: #606266;
  background: #f5f7fa; padding: 8px 10px; border-radius: 4px;
  max-height: 140px; overflow-y: auto; margin-bottom: 10px;
  border-left: 3px solid #409eff;
}
.cite-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
