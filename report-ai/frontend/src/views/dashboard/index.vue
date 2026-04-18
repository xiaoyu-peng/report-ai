<template>
  <div class="dashboard">
    <div class="page-header">
      <h1 class="page-title">仪表盘</h1>
      <span class="page-subtitle">欢迎回到 ReportAI 智能报告写作平台</span>
    </div>

    <!-- Stat cards -->
    <el-row :gutter="16" class="stat-row" v-loading="loadingStats">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.key">
        <el-card class="stat-card" shadow="hover" :body-style="{ padding: '18px' }">
          <div class="stat-body">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <el-icon><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-meta">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value">{{ stats[card.key] ?? 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Secondary row: recent reports + quick links -->
    <el-row :gutter="16" class="secondary-row">
      <el-col :xs="24" :md="14">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="card-title">
                <el-icon><Document /></el-icon>
                最近报告
              </span>
              <el-button text type="primary" size="small" @click="goTo('/reports')">
                查看全部
                <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          <div v-loading="loadingReports">
            <el-empty
              v-if="!loadingReports && recentReports.length === 0"
              description="暂无报告，去写作工作台生成第一篇吧"
              :image-size="80"
            />
            <ul v-else class="report-list">
              <li
                v-for="r in recentReports"
                :key="r.id"
                class="report-item"
                @click="goTo('/reports')"
              >
                <div class="report-title">
                  <el-icon class="report-icon"><Document /></el-icon>
                  <span>{{ r.title || '(无标题)' }}</span>
                </div>
                <div class="report-meta">
                  <el-tag v-if="r.wordCount" size="small" type="info" effect="light">
                    {{ r.wordCount }} 字
                  </el-tag>
                  <span class="report-time">{{ formatTime(r.updatedAt || r.createdAt) }}</span>
                </div>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <span class="card-title">
              <el-icon><MagicStick /></el-icon>
              快捷入口
            </span>
          </template>
          <div class="quick-grid">
            <div
              v-for="q in quickLinks"
              :key="q.path"
              class="quick-item"
              :style="{ borderColor: q.color }"
              @click="goTo(q.path)"
            >
              <div class="quick-icon" :style="{ background: q.bg, color: q.color }">
                <el-icon><component :is="q.icon" /></el-icon>
              </div>
              <div class="quick-meta">
                <div class="quick-label">{{ q.label }}</div>
                <div class="quick-desc">{{ q.desc }}</div>
              </div>
              <el-icon class="quick-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import {
  Document,
  Collection,
  User,
  TrendCharts,
  MagicStick,
  EditPen,
  DocumentCopy,
  ArrowRight
} from '@element-plus/icons-vue'
import {
  getDashboardStats,
  getReports,
  type Report,
  type DashboardStats
} from '@/api/report'

const router = useRouter()

const stats = reactive<DashboardStats>({
  totalReports: 0,
  totalKnowledgeBases: 0,
  totalUsers: 0,
  todayGenerated: 0
})

const loadingStats = ref(false)
const loadingReports = ref(false)
const recentReports = ref<Report[]>([])

const statCards = [
  {
    key: 'totalReports' as const,
    label: '报告总数',
    icon: markRaw(Document),
    color: '#6366f1',
    bg: 'rgba(99, 102, 241, 0.1)'
  },
  {
    key: 'totalKnowledgeBases' as const,
    label: '知识库数',
    icon: markRaw(Collection),
    color: '#10b981',
    bg: 'rgba(16, 185, 129, 0.1)'
  },
  {
    key: 'totalUsers' as const,
    label: '用户总数',
    icon: markRaw(User),
    color: '#f59e0b',
    bg: 'rgba(245, 158, 11, 0.1)'
  },
  {
    key: 'todayGenerated' as const,
    label: '今日生成',
    icon: markRaw(TrendCharts),
    color: '#ef4444',
    bg: 'rgba(239, 68, 68, 0.1)'
  }
]

const quickLinks = [
  {
    path: '/workspace',
    label: '写作工作台',
    desc: '开始生成一篇新的报告',
    icon: markRaw(EditPen),
    color: '#6366f1',
    bg: 'rgba(99, 102, 241, 0.1)'
  },
  {
    path: '/knowledge/list',
    label: '知识库',
    desc: '管理参考文档与分块',
    icon: markRaw(Collection),
    color: '#10b981',
    bg: 'rgba(16, 185, 129, 0.1)'
  },
  {
    path: '/templates',
    label: '模板中心',
    desc: '浏览与上传写作风格模板',
    icon: markRaw(DocumentCopy),
    color: '#f59e0b',
    bg: 'rgba(245, 158, 11, 0.1)'
  }
]

onMounted(() => {
  fetchStats()
  fetchRecentReports()
})

async function fetchStats() {
  loadingStats.value = true
  try {
    const res = await getDashboardStats()
    const data = (res as any).data as Partial<DashboardStats> | null
    if (data) {
      stats.totalReports = data.totalReports ?? 0
      stats.totalKnowledgeBases = data.totalKnowledgeBases ?? 0
      stats.totalUsers = data.totalUsers ?? 0
      stats.todayGenerated = data.todayGenerated ?? 0
    }
  } catch (e) {
    console.error('加载统计数据失败:', e)
  } finally {
    loadingStats.value = false
  }
}

async function fetchRecentReports() {
  loadingReports.value = true
  try {
    const res = await getReports()
    const raw = (res as any).data
    // Support both plain arrays and paginated { records: [...] }
    const list: Report[] = Array.isArray(raw)
      ? raw
      : Array.isArray(raw?.records)
        ? raw.records
        : []
    // Sort by updatedAt desc as a safety net, then take top 5
    list.sort((a, b) => {
      const ta = new Date(a.updatedAt || a.createdAt || 0).getTime()
      const tb = new Date(b.updatedAt || b.createdAt || 0).getTime()
      return tb - ta
    })
    recentReports.value = list.slice(0, 5)
  } catch (e) {
    console.error('加载最近报告失败:', e)
  } finally {
    loadingReports.value = false
  }
}

function goTo(path: string) {
  router.push(path)
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const now = Date.now()
  const diff = now - d.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return d.toLocaleDateString()
}
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}
.page-subtitle {
  font-size: 14px;
  color: #94a3b8;
}
.stat-row {
  margin: 0 !important;
}
.stat-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  transition: all 0.25s ease;
  margin-bottom: 16px;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #c7d2fe;
}
.stat-body {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}
.stat-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.stat-label {
  font-size: 13px;
  color: #94a3b8;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.2;
}
.secondary-row {
  margin: 0 !important;
}
.panel-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  margin-bottom: 16px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #0f172a;
}
.report-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.report-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 4px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.15s;
}
.report-item:last-child {
  border-bottom: none;
}
.report-item:hover {
  background: #f8fafc;
}
.report-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-icon {
  color: #6366f1;
  flex-shrink: 0;
}
.report-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.report-time {
  font-size: 12px;
  color: #94a3b8;
}
.quick-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-left: 3px solid;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}
.quick-item:hover {
  transform: translateX(2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.quick-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.quick-meta {
  flex: 1;
  min-width: 0;
}
.quick-label {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.quick-desc {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}
.quick-arrow {
  color: #cbd5e1;
  flex-shrink: 0;
}
</style>
