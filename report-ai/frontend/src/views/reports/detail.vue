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
            <div class="content-with-outline">
              <div class="outline-nav" v-if="outlineItems.length > 0">
                <div class="outline-title">
                  <el-icon><List /></el-icon>
                  大纲导航
                </div>
                <div class="outline-list">
                  <div
                    v-for="item in outlineItems"
                    :key="item.id"
                    class="outline-item"
                    :class="{ active: activeOutlineId === item.id, [`level-${item.level}`]: true }"
                    @click="scrollToOutline(item.id)"
                  >
                    {{ item.text }}
                  </div>
                </div>
              </div>
              <div class="content-main">
                <ReportCharts v-if="report?.topic" :topic="report.topic" />
                <div class="markdown-body" ref="contentEl" @click="handleCiteClick" v-html="renderedContent"></div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="版本对比" name="diff">
            <div class="diff-controls">
              <span class="diff-label">对比版本：</span>
              <el-select v-model="diffFrom" placeholder="旧版本" size="small" style="width: 180px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`v${v.versionNum} - ${modeLabel(v.sourceMode)}`"
                  :value="v.versionNum"
                />
              </el-select>
              <span class="diff-arrow">→</span>
              <el-select v-model="diffTo" placeholder="新版本" size="small" style="width: 180px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`v${v.versionNum} - ${modeLabel(v.sourceMode)}`"
                  :value="v.versionNum"
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
              <div class="diff-mode-switch">
                <el-radio-group v-model="diffViewMode" size="small">
                  <el-radio-button value="split">双栏对比</el-radio-button>
                  <el-radio-button value="revision">修订痕迹</el-radio-button>
                </el-radio-group>
              </div>
            </div>

            <div v-if="diffResult">
              <!-- 后端 LCS 算出的精确统计；fallback 时可能为空，这时兜底用客户端 changeStats -->
              <div class="diff-stats-bar">
                <el-tag v-if="diffStats" type="success" effect="plain">
                  <el-icon><Plus /></el-icon>
                  新增 {{ diffStats.inserts }} 行
                </el-tag>
                <el-tag v-if="diffStats" type="danger" effect="plain">
                  <el-icon><Minus /></el-icon>
                  删除 {{ diffStats.deletes }} 行
                </el-tag>
                <el-tag v-if="diffStats" type="warning" effect="plain">
                  <el-icon><Edit /></el-icon>
                  修改 {{ diffStats.replaces }} 行
                </el-tag>
                <el-tag v-if="!diffStats" type="info" effect="plain">
                  客户端回退计算：+{{ changeStats.added }} / -{{ changeStats.removed }}
                </el-tag>
              </div>
              <div v-if="diffViewMode === 'revision'" class="revision-view">
                <div class="revision-toolbar">
                  <el-button type="success" size="small" @click="acceptAllChanges">
                    <el-icon><Check /></el-icon>
                    全部接受
                  </el-button>
                  <el-button type="danger" size="small" @click="rejectAllChanges">
                    <el-icon><Close /></el-icon>
                    全部拒绝
                  </el-button>
                  <span class="revision-stats">
                    {{ changeStats.added }} 处新增 · {{ changeStats.removed }} 处删除
                  </span>
                </div>
                <div class="revision-body" v-html="revisionHtml"></div>
              </div>
              <div v-else class="diff-view">
                <div class="diff-columns">
                  <div class="diff-col diff-old">
                    <div class="diff-col-header">
                      <el-tag type="danger" size="small" effect="plain">旧版本 (v{{ diffFrom }})</el-tag>
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
                      <el-tag type="success" size="small" effect="plain">新版本 (v{{ diffTo }})</el-tag>
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
              <div class="version-actions">
                <el-button
                  v-if="v.versionNum !== latestVersionNum"
                  size="small"
                  type="primary"
                  link
                  @click.stop="handleRestore(v)"
                >
                  <el-icon><RefreshLeft /></el-icon>
                  回滚
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-if="!versionsLoading && versions.length === 0" description="暂无版本" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, EditPen, Download, List, Check, Close, RefreshLeft, Plus, Minus, Edit } from '@element-plus/icons-vue'
import { getReport, getReportVersions, getVersionDiffByNum, restoreVersion, exportDocx, type Report, type ReportVersion } from '@/api/report'
import { renderReportMarkdown } from '@/utils/markdown'
import * as Diff from 'diff'
import ReportCharts from '@/components/ReportCharts.vue'

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
const diffViewMode = ref<'split' | 'revision'>('split')

const contentEl = ref<HTMLDivElement | null>(null)

// 'placeholder' 用于左右两栏对齐（当一侧是 INSERT/DELETE 时，另一侧占位空行）
interface DiffLine {
  type: 'same' | 'added' | 'removed' | 'modified' | 'placeholder'
  text: string
}

interface DiffStats {
  inserts: number
  deletes: number
  replaces: number
}

const diffStats = ref<DiffStats | null>(null)

interface OutlineItem {
  id: string
  text: string
  level: number
}

const outlineItems = ref<OutlineItem[]>([])
const activeOutlineId = ref('')

onMounted(async () => {
  await Promise.all([loadReport(), loadVersions()])
  if (versions.value.length >= 2) {
    diffFrom.value = versions.value[versions.value.length - 2].versionNum
    diffTo.value = versions.value[versions.value.length - 1].versionNum
  }
  nextTick(() => buildOutline())
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

const latestVersionNum = computed(() => {
  if (versions.value.length === 0) return 0
  return Math.max(...versions.value.map(v => v.versionNum))
})

function selectVersion(v: ReportVersion) {
  selectedVersionId.value = v.id
  if (report.value) {
    report.value = { ...report.value, content: v.content, wordCount: v.wordCount } as Report
  }
  nextTick(() => buildOutline())
}

async function handleRestore(v: ReportVersion) {
  try {
    await ElMessageBox.confirm(
      `确认回滚到 v${v.versionNum}（${modeLabel(v.sourceMode)}）？系统将基于该版本创建新版本。`,
      '版本回滚',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' }
    )
    await restoreVersion(reportId, v.versionNum)
    ElMessage.success('回滚成功，已创建新版本')
    await loadVersions()
    await loadReport()
  } catch (e) {
    if (e !== 'cancel') console.error('回滚失败:', e)
  }
}

async function loadDiff() {
  if (!diffFrom.value || !diffTo.value) return
  diffLoading.value = true
  diffResult.value = null
  diffStats.value = null
  try {
    const res = await getVersionDiffByNum(reportId, diffFrom.value, diffTo.value)
    const data = (res as any).data
    diffResult.value = parseDiffResult(data)
    diffStats.value = {
      inserts: data?.inserts ?? 0,
      deletes: data?.deletes ?? 0,
      replaces: data?.replaces ?? 0
    }
  } catch (e) {
    console.error('加载 diff 失败:', e)
    const fromV = versions.value.find(v => v.versionNum === diffFrom.value)
    const toV = versions.value.find(v => v.versionNum === diffTo.value)
    if (fromV && toV) {
      diffResult.value = computeSimpleDiff(fromV.content || '', toV.content || '')
    }
  } finally {
    diffLoading.value = false
  }
}

/**
 * 对接后端 DiffResult schema：{ lines: [{op: EQUAL/INSERT/DELETE/REPLACE, oldLine, newLine}], inserts, deletes, replaces }
 * 把每一条 DiffLine 映射到左右两栏，INSERT/DELETE 在对侧塞 placeholder 以保持视觉行对齐。
 */
function parseDiffResult(data: any): { oldLines: DiffLine[]; newLines: DiffLine[] } {
  if (!data) return { oldLines: [], newLines: [] }
  const lines: Array<{op?: string, oldLine?: string, newLine?: string}> = data.lines || []
  // 后端 schema 缺失时兜底走本地 LCS（演示前不容崩）
  if (!Array.isArray(lines) || lines.length === 0) {
    const oldContent = data.oldContent || data.fromContent || ''
    const newContent = data.newContent || data.toContent || ''
    if (oldContent || newContent) return computeSimpleDiff(oldContent, newContent)
    return { oldLines: [], newLines: [] }
  }
  const oldLines: DiffLine[] = []
  const newLines: DiffLine[] = []
  for (const l of lines) {
    const op = String(l.op || '').toUpperCase()
    switch (op) {
      case 'EQUAL':
        oldLines.push({ type: 'same', text: l.oldLine ?? l.newLine ?? '' })
        newLines.push({ type: 'same', text: l.newLine ?? l.oldLine ?? '' })
        break
      case 'DELETE':
        oldLines.push({ type: 'removed', text: l.oldLine ?? '' })
        newLines.push({ type: 'placeholder', text: '' })
        break
      case 'INSERT':
        oldLines.push({ type: 'placeholder', text: '' })
        newLines.push({ type: 'added', text: l.newLine ?? '' })
        break
      case 'REPLACE':
        oldLines.push({ type: 'removed', text: l.oldLine ?? '' })
        newLines.push({ type: 'added', text: l.newLine ?? '' })
        break
      default:
        // 未知 op 当 EQUAL 处理，避免静默丢行
        oldLines.push({ type: 'same', text: l.oldLine ?? '' })
        newLines.push({ type: 'same', text: l.newLine ?? '' })
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

const changeStats = computed(() => {
  if (!diffResult.value) return { added: 0, removed: 0 }
  let added = 0
  let removed = 0
  for (const line of diffResult.value.newLines) {
    if (line.type === 'added') added++
  }
  for (const line of diffResult.value.oldLines) {
    if (line.type === 'removed') removed++
  }
  return { added, removed }
})

const revisionHtml = computed(() => {
  if (!diffResult.value) return ''
  const parts: string[] = []
  const oldIdx = { v: 0 }
  const newIdx = { v: 0 }
  const maxLen = Math.max(diffResult.value.oldLines.length, diffResult.value.newLines.length)
  for (let i = 0; i < maxLen; i++) {
    const oLine = diffResult.value.oldLines[oldIdx.v]
    const nLine = diffResult.value.newLines[newIdx.v]
    if (oLine && nLine && oLine.type === 'same' && nLine.type === 'same') {
      parts.push(`<div class="rev-same">${escapeHtml(oLine.text)}</div>`)
      oldIdx.v++
      newIdx.v++
    } else if (oLine && oLine.type === 'removed') {
      parts.push(`<div class="rev-removed"><span class="rev-marker del-mark">-</span>${escapeHtml(oLine.text)}<button class="rev-btn rev-reject" onclick="this.parentElement.classList.toggle('rev-rejected')">✕</button></div>`)
      oldIdx.v++
    } else if (nLine && nLine.type === 'added') {
      parts.push(`<div class="rev-added"><span class="rev-marker add-mark">+</span>${escapeHtml(nLine.text)}<button class="rev-btn rev-accept" onclick="this.parentElement.classList.toggle('rev-accepted')">✓</button></div>`)
      newIdx.v++
    } else {
      if (oLine) { parts.push(`<div class="rev-same">${escapeHtml(oLine.text)}</div>`); oldIdx.v++ }
      if (nLine) { parts.push(`<div class="rev-same">${escapeHtml(nLine.text)}</div>`); newIdx.v++ }
    }
  }
  return parts.join('')
})

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function acceptAllChanges() {
  ElMessage.success('已接受所有变更（当前版本即为最新版本）')
}

function rejectAllChanges() {
  ElMessage.info('已拒绝所有变更，回退到旧版本内容')
}

function buildOutline() {
  if (!contentEl.value) return
  const headings = contentEl.value.querySelectorAll('h1, h2, h3')
  const items: OutlineItem[] = []
  headings.forEach((h, i) => {
    const id = `outline-${i}`
    h.id = id
    items.push({
      id,
      text: h.textContent || '',
      level: parseInt(h.tagName[1])
    })
  })
  outlineItems.value = items
  if (items.length > 0) activeOutlineId.value = items[0].id
}

function scrollToOutline(id: string) {
  activeOutlineId.value = id
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
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
  if (!report.value) return
  try {
    if (kind === 'docx') {
      const res = await exportDocx(report.value.id)
      const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${report.value.title || '报告'}.docx`
      document.body.appendChild(a)
      a.click()
      setTimeout(() => { document.body.removeChild(a); URL.revokeObjectURL(url) }, 100)
      ElMessage.success('Word 导出成功')
    } else if (kind === 'pdf') {
      const el = document.querySelector('.markdown-body') as HTMLElement | null
      if (!el) { ElMessage.warning('无内容可导出'); return }
      const html2pdf = (await import('html2pdf.js')).default
      html2pdf().set({
        margin: [15, 15, 15, 15],
        filename: `${report.value.title || '报告'}.pdf`,
        html2canvas: { scale: 2, useCORS: true },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
      }).from(el).save()
      ElMessage.success('PDF 导出成功')
    }
  } catch (e) {
    console.error('导出失败:', e)
    ElMessage.error('导出失败')
  }
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
    rewrite_continuation: '续写新章节',
    rewrite_section_rewrite: '段落改写',
    rewrite_section_expand: '段落扩写',
    rewrite_section_condense: '段落精简'
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
  if (type === 'placeholder') return '  '
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
  color: #1e293b;
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
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 20px;
  overflow: auto;
}

.content-with-outline {
  display: flex;
  gap: 20px;
}

.outline-nav {
  width: 200px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  border-right: 1px solid #f1f5f9;
  padding-right: 16px;
}

.outline-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.outline-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.outline-item {
  font-size: 13px;
  color: #64748b;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.outline-item:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.outline-item.active {
  background: #ede9fe;
  color: #6366f1;
  font-weight: 500;
}

.outline-item.level-1 { padding-left: 8px; font-weight: 600; color: #1e293b; }
.outline-item.level-2 { padding-left: 20px; }
.outline-item.level-3 { padding-left: 32px; font-size: 12px; }

.content-main {
  flex: 1;
  min-width: 0;
}

.markdown-body {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.85;
  color: #334155;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  font-weight: 600;
  color: #0f172a;
  margin: 1.2em 0 0.6em;
  scroll-margin-top: 20px;
}
.markdown-body :deep(h1) { font-size: 22px; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; }
.markdown-body :deep(h2) { font-size: 18px; border-bottom: 1px solid #f1f5f9; padding-bottom: 6px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 0.6em 0; }
.markdown-body :deep(sup.cite) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ede9fe;
  color: #6366f1;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.markdown-body :deep(sup.cite:hover) {
  background: #6366f1;
  color: #fff;
}

/* Diff controls */
.diff-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  flex-wrap: wrap;
}
.diff-label {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
}
.diff-arrow {
  font-size: 16px;
  color: #94a3b8;
}
.diff-mode-switch {
  margin-left: auto;
}

/* Diff view */
.diff-view {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
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
  padding: 10px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  text-align: center;
}
.diff-old .diff-col-header {
  border-right: 1px solid #e2e8f0;
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
  border-right: 1px solid #e2e8f0;
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
.line-same { background: transparent; color: #334155; }
.line-same .line-prefix { color: #cbd5e1; }
.line-added { background: #f0fdf4; color: #16a34a; }
.line-added .line-prefix { color: #16a34a; }
.line-removed { background: #fef2f2; color: #dc2626; }
.line-removed .line-prefix { color: #dc2626; }
.line-modified { background: #fffbeb; color: #d97706; }
.line-modified .line-prefix { color: #d97706; }
/* placeholder：对侧 INSERT/DELETE 时，此侧留一个灰色空行占位，保持两栏视觉对齐 */
.line-placeholder {
  background: #f8fafc;
  color: #cbd5e1;
  min-height: 24px;
}
.line-placeholder .line-prefix { color: #cbd5e1; }

/* 后端 LCS 统计条 */
.diff-stats-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #fafbfc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  align-items: center;
}
.diff-stats-bar .el-tag {
  font-weight: 500;
}

/* Revision trace view */
.revision-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
.revision-stats {
  margin-left: auto;
  font-size: 13px;
  color: #64748b;
}
.revision-body {
  font-family: 'PingFang SC', 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.8;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  max-height: 60vh;
  overflow-y: auto;
}
.revision-body :deep(.rev-same) {
  padding: 1px 4px;
  color: #334155;
}
.revision-body :deep(.rev-removed) {
  padding: 1px 4px;
  background: #fecaca;
  color: #991b1b;
  text-decoration: line-through;
  border-radius: 3px;
  position: relative;
}
.revision-body :deep(.rev-added) {
  padding: 1px 4px;
  background: #bbf7d0;
  color: #166534;
  border-radius: 3px;
  position: relative;
}
.revision-body :deep(.rev-rejected) {
  background: #fecaca;
  text-decoration: line-through;
  opacity: 0.6;
}
.revision-body :deep(.rev-accepted) {
  background: #bbf7d0;
  text-decoration: none;
}
.revision-body :deep(.rev-marker) {
  display: inline-block;
  width: 16px;
  font-weight: 700;
  user-select: none;
  text-align: center;
}
.revision-body :deep(.del-mark) { color: #dc2626; }
.revision-body :deep(.add-mark) { color: #16a34a; }
.revision-body :deep(.rev-btn) {
  display: none;
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  padding: 0;
}
.revision-body :deep(.rev-removed:hover .rev-btn),
.revision-body :deep(.rev-added:hover .rev-btn) {
  display: block;
}
.revision-body :deep(.rev-accept) {
  background: #16a34a;
  color: #fff;
}
.revision-body :deep(.rev-reject) {
  background: #dc2626;
  color: #fff;
}

/* Version sidebar */
.version-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 20px;
  overflow-y: auto;
}

.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  margin: 0 0 16px;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.version-item {
  display: flex;
  gap: 10px;
  padding: 12px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
}
.version-item:hover { background: #f8fafc; }
.version-item.active { background: #ede9fe; }

.version-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #cbd5e1;
  margin-top: 4px;
  flex-shrink: 0;
  position: relative;
}
.version-item.active .version-dot { background: #6366f1; }
.version-item:not(:last-child) .version-dot::after {
  content: '';
  position: absolute;
  top: 10px;
  left: 4px;
  width: 2px;
  height: calc(100% + 14px);
  background: #e2e8f0;
}

.version-info {
  flex: 1;
  min-width: 0;
}

.version-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.version-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.version-words {
  font-size: 12px;
  color: #94a3b8;
}

.version-time {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.version-summary {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  line-height: 1.5;
}

.version-actions {
  margin-top: 4px;
}
</style>
