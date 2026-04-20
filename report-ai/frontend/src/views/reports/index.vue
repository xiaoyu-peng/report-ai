<template>
  <div class="reports-page">
    <div class="page-header">
      <h1 class="page-title">报告库</h1>
      <div class="header-actions">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="card">
            <el-icon><Grid /></el-icon>
          </el-radio-button>
          <el-radio-button value="table">
            <el-icon><List /></el-icon>
          </el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="goWorkspace">
          <el-icon><Plus /></el-icon>
          新建报告
        </el-button>
      </div>
    </div>

    <div v-if="viewMode === 'card'" class="card-view" v-loading="loading">
      <el-row :gutter="16">
        <el-col v-for="r in list" :key="r.id" :xs="24" :sm="12" :md="8" :lg="6">
          <div class="report-card" @click="handleView(r)">
            <div class="card-top">
              <div class="card-icon" :style="{ background: getCardColor(r.templateId).bg, color: getCardColor(r.templateId).fg }">
                <el-icon :size="24"><Document /></el-icon>
              </div>
              <el-tag :type="statusTagType(r.status)" size="small" effect="light">
                {{ statusLabel(r.status) }}
              </el-tag>
            </div>
            <div class="card-title">{{ r.title || '(无标题)' }}</div>
            <div class="card-topic" v-if="r.topic">{{ r.topic }}</div>
            <div class="card-footer">
              <span class="card-words">{{ r.wordCount ?? 0 }} 字</span>
              <span class="card-time">{{ formatTime(r.createdAt) }}</span>
            </div>
            <div class="card-actions">
              <el-button size="small" type="primary" link @click.stop="handleView(r)">查看详情</el-button>
              <el-button size="small" type="danger" link @click.stop="handleDelete(r)">删除</el-button>
            </div>
          </div>
        </el-col>
        <el-col v-if="!loading && list.length === 0" :span="24">
          <el-empty description="暂无报告，去写作工作台生成第一篇吧" />
        </el-col>
      </el-row>
    </div>

    <el-card v-else class="table-card" shadow="never" v-loading="loading">
      <el-table
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Grid, List, Document } from '@element-plus/icons-vue'
import { getReports, deleteReport } from '@/api/report'

interface ReportRow {
  id: number
  title: string
  topic?: string
  content?: string
  status?: string
  wordCount?: number
  templateId?: number | null
  createdAt?: string
}

const router = useRouter()
const list = ref<ReportRow[]>([])
const loading = ref(false)
const viewMode = ref<'card' | 'table'>('table')

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

function getCardColor(templateId?: number | null): { bg: string; fg: string } {
  const colors = [
    { bg: 'rgba(99, 102, 241, 0.1)', fg: '#6366f1' },
    { bg: 'rgba(16, 185, 129, 0.1)', fg: '#10b981' },
    { bg: 'rgba(245, 158, 11, 0.1)', fg: '#f59e0b' },
    { bg: 'rgba(239, 68, 68, 0.1)', fg: '#ef4444' },
    { bg: 'rgba(139, 92, 246, 0.1)', fg: '#8b5cf6' }
  ]
  const idx = (templateId ?? 1) - 1
  return colors[idx % colors.length]
}

function statusTagType(status?: string): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'completed' || status === 'COMPLETED' || status === 'ready' || status === 'READY') return 'success'
  return 'info'
}

function statusLabel(status?: string): string {
  const map: Record<string, string> = {
    completed: '已完成', COMPLETED: '已完成', ready: '已完成', READY: '已完成',
    generating: '生成中', GENERATING: '生成中',
    draft: '草稿', DRAFT: '草稿',
    failed: '失败', FAILED: '失败'
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

function handleView(row: ReportRow) {
  router.push(`/reports/${row.id}`)
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
  color: #0f172a;
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.table-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.report-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  min-height: 180px;
}
.report-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #c7d2fe;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 6px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-topic {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.card-words {
  font-size: 12px;
  color: #94a3b8;
}

.card-time {
  font-size: 12px;
  color: #94a3b8;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  opacity: 0;
  transition: opacity 0.15s;
}
.report-card:hover .card-actions {
  opacity: 1;
}
</style>
