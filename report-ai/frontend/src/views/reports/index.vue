<template>
  <div class="reports-page">
    <div class="page-header">
      <h1 class="page-title">报告库</h1>
      <el-button type="primary" @click="goWorkspace">
        <el-icon><Plus /></el-icon>
        新建报告
      </el-button>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        style="width: 100%"
        empty-text="暂无报告"
      >
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="topic" label="主题" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.topic || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="wordCount" label="字数" width="100">
          <template #default="{ row }">
            <span>{{ row.wordCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleView(row)">查看</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框（朴素 Markdown 渲染） -->
    <el-dialog
      v-model="showDetailDialog"
      :title="currentReport?.title || '报告详情'"
      width="820px"
      top="6vh"
    >
      <div class="detail-meta">
        <el-tag v-if="currentReport?.status" :type="statusTagType(currentReport.status)" size="small">
          {{ statusLabel(currentReport?.status) }}
        </el-tag>
        <span class="meta-item">字数：{{ currentReport?.wordCount ?? 0 }}</span>
        <span class="meta-item">创建时间：{{ formatTime(currentReport?.createdAt) }}</span>
      </div>
      <div class="markdown-body" v-html="renderedMarkdown"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getReports, deleteReport } from '@/api/report'

interface ReportRow {
  id: number
  title: string
  topic?: string
  content?: string
  status?: string
  wordCount?: number
  createdAt?: string
}

const router = useRouter()
const list = ref<ReportRow[]>([])
const loading = ref(false)
const showDetailDialog = ref(false)
const currentReport = ref<ReportRow | null>(null)

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await getReports()
    const data = res.data as any
    list.value = Array.isArray(data) ? data : data?.records || []
  } catch (e) {
    console.error('加载报告列表失败:', e)
  } finally {
    loading.value = false
  }
}

function goWorkspace() {
  router.push('/workspace')
}

function statusTagType(status?: string): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'completed' || status === 'COMPLETED') return 'success'
  return 'info'
}

function statusLabel(status?: string): string {
  const map: Record<string, string> = {
    completed: '已完成',
    COMPLETED: '已完成',
    generating: '生成中',
    GENERATING: '生成中',
    draft: '草稿',
    DRAFT: '草稿',
    failed: '失败',
    FAILED: '失败'
  }
  return map[status || ''] || (status || '未知')
}

function formatTime(v?: string): string {
  if (!v) return '-'
  const d = new Date(v)
  if (isNaN(d.getTime())) return v
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// 朴素 Markdown 渲染：## / # / **bold**（MVP，无需 markdown 库）
function renderMarkdown(raw: string): string {
  if (!raw) return '<p class="md-empty">暂无内容</p>'
  const escaped = escapeHtml(raw)
  const lines = escaped.split(/\r?\n/)
  const out: string[] = []
  for (const line of lines) {
    let l = line
    if (/^##\s+/.test(l)) {
      out.push(`<h2>${l.replace(/^##\s+/, '')}</h2>`)
      continue
    }
    if (/^#\s+/.test(l)) {
      out.push(`<h1>${l.replace(/^#\s+/, '')}</h1>`)
      continue
    }
    l = l.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    if (l.trim() === '') {
      out.push('<br/>')
    } else {
      out.push(`<p>${l}</p>`)
    }
  }
  return out.join('\n')
}

const renderedMarkdown = computed(() => renderMarkdown(currentReport.value?.content || ''))

function handleView(row: ReportRow) {
  currentReport.value = row
  showDetailDialog.value = true
}

async function handleDelete(row: ReportRow) {
  try {
    await ElMessageBox.confirm(
      `确认删除报告「${row.title}」？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
    await deleteReport(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    if (e !== 'cancel') console.error('删除报告失败:', e)
  }
}
</script>

<style scoped>
.reports-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.table-card {
  border-radius: 8px;
}
.detail-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  color: #909399;
  font-size: 13px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.meta-item {
  color: #606266;
}
.markdown-body {
  color: #303133;
  font-size: 14px;
  line-height: 1.8;
  max-height: 60vh;
  overflow: auto;
}
.markdown-body :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  margin: 20px 0 12px;
  color: #303133;
}
.markdown-body :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 16px 0 10px;
  color: #303133;
}
.markdown-body :deep(p) {
  margin: 8px 0;
}
.markdown-body :deep(strong) {
  color: #303133;
  font-weight: 600;
}
.markdown-body :deep(.md-empty) {
  color: #c0c4cc;
  text-align: center;
  padding: 40px 0;
}
</style>
