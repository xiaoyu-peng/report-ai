<template>
  <div class="report-detail">
    <div class="detail-header">
      <div class="header-left">
        <el-button @click="$router.push('/reports')" :icon="ArrowLeft" plain>返回报告库</el-button>
        <h1 class="report-title">{{ report?.title || '报告详情' }}</h1>
        <el-tag v-if="report?.status" :type="statusTagType(report.status)" size="small" effect="light">
          {{ statusLabel(report.status) }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-tag type="info" effect="light">{{ report?.wordCount ?? 0 }} 字</el-tag>
        <el-button size="small" type="primary" plain @click="goEdit">
          <el-icon><EditPen /></el-icon>
          编辑
        </el-button>
        <el-dropdown trigger="click" @command="handleExport">
          <el-button size="small" type="primary" plain>
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="docx">Word (.docx)</el-dropdown-item>
              <el-dropdown-item command="pdf">PDF</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="detail-body">
      <div class="content-area">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="报告正文" name="content">
            <div class="markdown-body" @click="handleCiteClick" v-html="renderedContent"></div>
          </el-tab-pane>
          <el-tab-pane label="版本对比" name="diff">
            <div class="diff-controls">
              <span class="diff-label">对比版本：</span>
              <el-select v-model="diffFrom" placeholder="旧版本" size="small" style="width: 160px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`v${v.versionNum} - ${modeLabel(v.sourceMode)}`"
                  :value="v.id"
                />
              </el-select>
              <span class="diff-arrow">→</span>
              <el-select v-model="diffTo" placeholder="新版本" size="small" style="width: 160px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`v${v.versionNum} - ${modeLabel(v.sourceMode)}`"
                  :value="v.id"
                />
              </el-select>
              <el-button
                size="small"
                type="primary"
                :disabled="!diffFrom || !diffTo || diffFrom === diffTo"
                :loading="diffLoading"
                @click="loadDiff"
              >
                对比
              </el-button>
            </div>

            <div v-if="diffResult" class="diff-view">
              <div class="diff-columns">
                <div class="diff-col diff-old">
                  <div class="diff-col-header">
                    <el-tag type="danger" size="small" effect="plain">旧版本</el-tag>
                  </div>
                  <div class="diff-col-body">
                    <div
                      v-for="(line, i) in diffResult.oldLines"
                      :key="'o-' + i"
                      class="diff-line"
                      :class="lineClass(line.type)"
                    >
                      <span class="line-prefix">{{ linePrefix(line.type) }}</span>
                      <span class="line-text">{{ line.text }}</span>
                    </div>
                  </div>
                </div>
                <div class="diff-col diff-new">
                  <div class="diff-col-header">
                    <el-tag type="success" size="small" effect="plain">新版本</el-tag>
                  </div>
                  <div class="diff-col-body">
                    <div
                      v-for="(line, i) in diffResult.newLines"
                      :key="'n-' + i"
                      class="diff-line"
                      :class="lineClass(line.type)"
                    >
                      <span class="line-prefix">{{ linePrefix(line.type) }}</span>
                      <span class="line-text">{{ line.text }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else-if="!diffLoading" description="选择两个版本后点击对比" />
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="version-sidebar">
        <h3 class="sidebar-title">版本历史</h3>
        <div v-loading="versionsLoading" class="version-list">
          <div
            v-for="v in versions"
            :key="v.id"
            class="version-item"
            :class="{ active: selectedVersionId === v.id }"
            @click="selectVersion(v)"
          >
            <div class="version-dot"></div>
            <div class="version-info">
              <div class="version-name">v{{ v.versionNum }}</div>
              <div class="version-meta">
                <el-tag size="small" effect="plain" :type="modeTagType(v.sourceMode)">
                  {{ modeLabel(v.sourceMode) }}
                </el-tag>
                <span class="version-words">{{ v.wordCount ?? 0 }} 字</span>
              </div>
              <div class="version-time">{{ formatTime(v.createdAt) }}</div>
              <div v-if="v.changeSummary" class="version-summary">{{ v.changeSummary }}</div>
            </div>
          </div>
          <el-empty v-if="!versionsLoading && versions.length === 0" description="暂无版本" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, EditPen, Download } from '@element-plus/icons-vue'
import { getReport, getReportVersions, getVersionDiff, type Report, type ReportVersion } from '@/api/report'
import { renderReportMarkdown } from '@/utils/markdown'
import * as Diff from 'diff'

const route = useRoute()
const router = useRouter()
const reportId = Number(route.params.id)

const report = ref<Report | null>(null)
const versions = ref<ReportVersion[]>([])
const versionsLoading = ref(false)
const selectedVersionId = ref<number | null>(null)
const activeTab = ref('content')

const diffFrom = ref<number | null>(null)
const diffTo = ref<number | null>(null)
const diffLoading = ref(false)
const diffResult = ref<{ oldLines: DiffLine[]; newLines: DiffLine[] } | null>(null)

interface DiffLine {
  type: 'same' | 'added' | 'removed' | 'modified'
  text: string
}

onMounted(async () => {
  await Promise.all([loadReport(), loadVersions()])
  if (versions.value.length >= 2) {
    diffFrom.value = versions.value[versions.value.length - 2].id
    diffTo.value = versions.value[versions.value.length - 1].id
  }
})

async function loadReport() {
  try {
    const res = await getReport(reportId)
    report.value = (res as any).data as Report
  } catch (e) {
    console.error('加载报告失败:', e)
  }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    const res = await getReportVersions(reportId)
    const data = (res as any).data
    versions.value = Array.isArray(data) ? data : data?.records || []
    if (versions.value.length > 0 && !selectedVersionId.value) {
      selectedVersionId.value = versions.value[versions.value.length - 1].id
    }
  } catch (e) {
    console.error('加载版本列表失败:', e)
  } finally {
    versionsLoading.value = false
  }
}

function selectVersion(v: ReportVersion) {
  selectedVersionId.value = v.id
  if (report.value) {
    report.value = { ...report.value, content: v.content, wordCount: v.wordCount } as Report
  }
}

async function loadDiff() {
  if (!diffFrom.value || !diffTo.value) return
  diffLoading.value = true
  diffResult.value = null
  try {
    const res = await getVersionDiff(diffFrom.value, diffTo.value)
    const data = (res as any).data
    diffResult.value = parseDiffResult(data)
  } catch (e) {
    console.error('加载 diff 失败:', e)
    ElMessage.error('对比加载失败')
  } finally {
    diffLoading.value = false
  }
}

function parseDiffResult(data: any): { oldLines: DiffLine[]; newLines: DiffLine[] } {
  if (!data) return { oldLines: [], newLines: [] }

  const patches: any[] = data.patches || data.diffs || data.changes || []
  if (patches.length === 0) {
    const oldContent = data.oldContent || data.fromContent || ''
    const newContent = data.newContent || data.toContent || ''
    return computeSimpleDiff(oldContent, newContent)
  }

  const oldLines: DiffLine[] = []
  const newLines: DiffLine[] = []

  for (const p of patches) {
    const type = p.type || p.changeType || 'EQUAL'
    const text = p.text || p.content || ''
    const lines = text.split('\n')

    for (const line of lines) {
      const dl: DiffLine = {
        type: type === 'EQUAL' || type === 'SAME' ? 'same'
          : type === 'DELETE' || type === 'REMOVED' ? 'removed'
          : type === 'INSERT' || type === 'ADDED' ? 'added'
          : 'modified',
        text: line
      }
      if (dl.type === 'removed' || dl.type === 'same') oldLines.push(dl)
      if (dl.type === 'added' || dl.type === 'same') newLines.push(dl)
      if (dl.type === 'modified') {
        oldLines.push({ type: 'removed', text: line })
        newLines.push({ type: 'added', text: p.newText || p.replacement || line })
      }
    }
  }

  return { oldLines, newLines }
}

function computeSimpleDiff(oldContent: string, newContent: string): { oldLines: DiffLine[]; newLines: DiffLine[] } {
    const changes = Diff.diffLines(oldContent, newContent)
    const oldLines: DiffLine[] = []
    const newLines: DiffLine[] = []
    for (const change of changes) {
      const text = change.value.endsWith('\n') ? change.value.slice(0, -1) : change.value
      const lines = text.split('\n')
      if (change.added) {
        lines.forEach(t => newLines.push({ type: 'added', text: t }))
      } else if (change.removed) {
        lines.forEach(t => oldLines.push({ type: 'removed', text: t }))
      } else {
        lines.forEach(t => {
          oldLines.push({ type: 'same', text: t })
          newLines.push({ type: 'same', text: t })
        })
      }
    }
    return { oldLines, newLines }
  }

const renderedContent = computed(() => {
  const c = report.value?.content || ''
  return c ? renderReportMarkdown(c) : '<p style="color:#999">暂无内容</p>'
})

function handleCiteClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target) return
  const sup = target.closest('sup.cite') as HTMLElement | null
  if (sup) {
    const idx = sup.dataset.idx
    if (idx) ElMessage.info(`引用来源 [${idx}]`)
  }
}

function goEdit() {
  router.push('/workspace')
}

async function handleExport(kind: string) {
  ElMessage.info(`导出 ${kind} 功能开发中`)
}

function statusTagType(status?: string): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'completed' || status === 'ready' || status === 'COMPLETED') return 'success'
  if (status === 'generating' || status === 'GENERATING') return 'warning'
  if (status === 'failed' || status === 'FAILED') return 'danger'
  return 'info'
}

function statusLabel(status?: string): string {
  const map: Record<string, string> = {
    completed: '已完成', COMPLETED: '已完成', ready: '已完成', READY: '已完成',
    generating: '生成中', GENERATING: '生成中',
    draft: '草稿', DRAFT: '草稿',
    failed: '失败', FAILED: '失败'
  }
  return map[status || ''] || status || '未知'
}

function modeLabel(mode?: string): string {
  if (!mode) return '初始版本'
  if (mode === 'initial') return '首次生成'
  const map: Record<string, string> = {
    rewrite_data_update: '数据更新',
    rewrite_angle_shift: '视角调整',
    rewrite_expand: '内容扩展',
    rewrite_style_shift: '风格转换',
    rewrite_continuation: '续写新章节'
  }
  return map[mode] || mode
}

function modeTagType(mode?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (!mode || mode === 'initial') return 'info'
  if (mode.includes('data_update')) return 'warning'
  if (mode.includes('angle_shift')) return 'danger'
  if (mode.includes('expand')) return 'success'
  if (mode.includes('style_shift')) return 'primary'
  return 'info'
}

function lineClass(type: string): string {
  return `line-${type}`
}

function linePrefix(type: string): string {
  if (type === 'added') return '+ '
  if (type === 'removed') return '- '
  return '  '
}

function formatTime(v?: string): string {
  if (!v) return '-'
  const d = new Date(v)
  if (isNaN(d.getTime())) return v
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.report-detail {
  height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.report-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.content-area {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  padding: 16px;
  overflow: auto;
}

.markdown-body {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.85;
  color: #303133;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  font-weight: 600;
  color: #1f2d3d;
  margin: 1.2em 0 0.6em;
}
.markdown-body :deep(p) { margin: 0.6em 0; }
.markdown-body :deep(sup.cite) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

/* Diff controls */
.diff-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.diff-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}
.diff-arrow {
  font-size: 16px;
  color: #909399;
}

/* Diff view */
.diff-view {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
.diff-columns {
  display: flex;
}
.diff-col {
  flex: 1;
  min-width: 0;
}
.diff-col-header {
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  text-align: center;
}
.diff-old .diff-col-header {
  border-right: 1px solid #e4e7ed;
}
.diff-col-body {
  padding: 0;
  font-family: 'PingFang SC', 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.7;
  max-height: 60vh;
  overflow-y: auto;
}
.diff-old .diff-col-body {
  border-right: 1px solid #e4e7ed;
}
.diff-line {
  padding: 2px 8px 2px 4px;
  white-space: pre-wrap;
  word-break: break-all;
}
.line-prefix {
  display: inline-block;
  width: 20px;
  font-weight: 600;
  user-select: none;
}
.line-same { background: transparent; color: #303133; }
.line-same .line-prefix { color: #c0c4cc; }
.line-added { background: #f0f9eb; color: #67c23a; }
.line-added .line-prefix { color: #67c23a; }
.line-removed { background: #fef0f0; color: #f56c6c; }
.line-removed .line-prefix { color: #f56c6c; }
.line-modified { background: #fdf6ec; color: #e6a23c; }
.line-modified .line-prefix { color: #e6a23c; }

/* Version sidebar */
.version-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  padding: 16px;
  overflow-y: auto;
}

.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.version-item {
  display: flex;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.version-item:hover { background: #f5f7fa; }
.version-item.active { background: #ecf5ff; }

.version-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #c0c4cc;
  margin-top: 4px;
  flex-shrink: 0;
  position: relative;
}
.version-item.active .version-dot { background: #409eff; }
.version-item:not(:last-child) .version-dot::after {
  content: '';
  position: absolute;
  top: 10px;
  left: 4px;
  width: 2px;
  height: calc(100% + 12px);
  background: #e4e7ed;
}

.version-info {
  flex: 1;
  min-width: 0;
}

.version-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.version-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.version-words {
  font-size: 12px;
  color: #909399;
}

.version-time {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.version-summary {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
}
</style>
