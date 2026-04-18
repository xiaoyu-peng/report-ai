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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
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
  color: #303133;
  margin: 0;
}
.table-card {
  border-radius: 8px;
}
</style>
