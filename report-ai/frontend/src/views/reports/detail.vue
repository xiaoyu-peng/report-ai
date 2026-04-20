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
          <el-tab-pane label="智能编辑" name="tiptap">
            <div v-if="report" class="tiptap-tab">
              <el-alert
                title="选中任意段落 → 顶部浮起 ✨ 优化 / ➕ 扩展 / ✂️ 精简 三个按钮，AI 现场改写"
                type="info" :closable="false" show-icon style="margin-bottom: 12px"
              />
              <TiptapEditor :model-value="report.content || ''" @update:model-value="onTiptapEdit" :report-id="report.id" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="覆盖度体检" name="coverage">
            <CoverageDashboard v-if="report" :report-id="report.id" />
          </el-tab-pane>
          <el-tab-pane label="章节流式" name="sections">
            <div v-if="report" class="sections-tab">
              <el-card shadow="never" class="outline-panel">
                <template #header>
                  <div class="panel-head">
                    <span><el-icon><Edit /></el-icon> 第 1 步：编辑大纲</span>
                    <el-button size="small" type="primary" :loading="initSecLoading"
                               :disabled="!outlineDraft.length" @click="submitOutline">
                      提交大纲，初始化 {{ outlineDraft.length }} 个章节
                    </el-button>
                  </div>
                </template>
                <OutlineEditor v-model="outlineDraft" />
              </el-card>
              <el-card shadow="never" class="stream-panel">
                <template #header>
                  <span><el-icon><VideoPlay /></el-icon> 第 2 步：每章独立流式生成 + 进度</span>
                </template>
                <SectionStreamView
                  ref="sectionStreamRef"
                  :report-id="report.id"
                  :kb-ids="report.kbId ? [report.kbId] : []"
                  :initial-content="report.content || ''"
                  @assembled="onSectionsAssembled"
                  @imported="onSectionsImported"
                />
              </el-card>
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
              <!-- "变更背景"卡：展示 toVersion 的 change_summary（含模式/MCP/字数变化等） -->
              <div v-if="toVersionSummary" class="change-context-card">
                <div class="change-context-icon">🗒️</div>
                <div class="change-context-body">
                  <div class="change-context-title">
                    变更背景 · v{{ diffFrom }} → v{{ diffTo }}
                  </div>
                  <div class="change-context-meta">{{ toVersionSummary }}</div>
                </div>
              </div>
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

          <el-tab-pane name="quality">
            <template #label>
              <span>
                <el-icon style="vertical-align: -2px"><MagicStick /></el-icon>
                质量检查
              </span>
            </template>
            <div class="quality-panel" v-loading="qualityLoading" element-loading-text="AI 正在审查报告覆盖度 / 引用准确性 / 事实性…">
              <div v-if="!qualityReport && !qualityLoading" class="quality-empty">
                <el-icon :size="44" color="#6366f1"><MagicStick /></el-icon>
                <div class="quality-empty-title">AI 审查员待命</div>
                <div class="quality-empty-desc">
                  对本报告从 <b>覆盖度</b>、<b>引用准确性</b>、<b>事实性</b> 三个维度做一次体检，
                  帮助定位要点漏写、引用错配、无源数据等问题（赛题 3.4）。
                </div>
                <el-button type="primary" size="large" @click="runQualityCheck">
                  <el-icon><MagicStick /></el-icon>
                  开始质量检查
                </el-button>
              </div>

              <div v-if="qualityReport" class="quality-result">
                <div class="quality-header">
                  <div class="quality-score-card">
                    <div class="quality-score-big" :class="'tone-' + scoreTagType(qualityReport.overallScore)">
                      {{ qualityReport.overallScore ?? '—' }}
                      <span class="quality-score-unit">/100</span>
                    </div>
                    <div class="quality-score-label">综合得分</div>
                  </div>
                  <div class="quality-summary">
                    <div class="quality-summary-title">AI 一句话总评</div>
                    <div class="quality-summary-text">{{ qualityReport.summary || '（未给出总评）' }}</div>
                    <div class="quality-summary-meta">
                      检查时间 {{ qualityCheckedAt }}
                      <el-button link type="primary" size="small" @click="runQualityCheck">
                        <el-icon><RefreshLeft /></el-icon>
                        重新检查
                      </el-button>
                    </div>
                  </div>
                </div>

                <div class="quality-dims">
                  <div class="quality-dim-card">
                    <div class="dim-head">
                      <span class="dim-title">覆盖度</span>
                      <el-tag :type="scoreTagType(qualityReport.coverageScore)" size="small" effect="plain">
                        {{ pct(qualityReport.coverageScore) }}
                      </el-tag>
                    </div>
                    <div class="dim-desc">用户要点是否被报告正文覆盖</div>
                    <div v-if="qualityReport.missingKeyPoints?.length" class="dim-issues">
                      <div class="dim-issues-title">未覆盖要点（{{ qualityReport.missingKeyPoints.length }}）</div>
                      <ul>
                        <li v-for="(it, i) in qualityReport.missingKeyPoints" :key="i">{{ it }}</li>
                      </ul>
                    </div>
                    <el-empty v-else description="全部要点已覆盖" :image-size="48" />
                  </div>

                  <div class="quality-dim-card">
                    <div class="dim-head">
                      <span class="dim-title">引用准确性</span>
                      <el-tag :type="scoreTagType(qualityReport.citationAccuracyScore)" size="small" effect="plain">
                        {{ pct(qualityReport.citationAccuracyScore) }}
                      </el-tag>
                    </div>
                    <div class="dim-desc">正文 [n] 标记是否真能从对应 chunk 推出</div>
                    <div v-if="qualityReport.citationIssues?.length" class="dim-issues">
                      <div class="dim-issues-title">可疑引用（{{ qualityReport.citationIssues.length }}）</div>
                      <div v-for="(it, i) in qualityReport.citationIssues" :key="i" class="issue-row">
                        <div class="issue-badge">[{{ it.citedIndex }}]</div>
                        <div class="issue-body">
                          <div class="issue-sentence">{{ it.sentence }}</div>
                          <div class="issue-reason">
                            <el-icon><Warning /></el-icon>
                            {{ it.reason }}
                          </div>
                        </div>
                      </div>
                    </div>
                    <el-empty v-else description="未发现可疑引用" :image-size="48" />
                  </div>

                  <div class="quality-dim-card">
                    <div class="dim-head">
                      <span class="dim-title">事实性</span>
                      <el-tag :type="scoreTagType(qualityReport.factualityScore)" size="small" effect="plain">
                        {{ pct(qualityReport.factualityScore) }}
                      </el-tag>
                    </div>
                    <div class="dim-desc">报告中是否存在无源数据 / 硬造事实</div>
                    <div v-if="qualityReport.factualityIssues?.length" class="dim-issues">
                      <div class="dim-issues-title">事实性疑点（{{ qualityReport.factualityIssues.length }}）</div>
                      <div v-for="(it, i) in qualityReport.factualityIssues" :key="i" class="issue-row">
                        <div class="issue-badge issue-badge-warn">!</div>
                        <div class="issue-body">
                          <div class="issue-sentence">{{ it.sentence }}</div>
                          <div class="issue-reason">
                            <el-icon><Warning /></el-icon>
                            {{ it.reason }}
                          </div>
                          <el-tag size="small" type="warning" effect="light">
                            建议：{{ suggestionLabel(it.suggestion) }}
                          </el-tag>
                        </div>
                      </div>
                    </div>
                    <el-empty v-else description="未发现事实性问题" :image-size="48" />
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="version-sidebar">
        <el-tabs v-model="sidebarTab" class="sidebar-tabs">
          <el-tab-pane name="citations">
            <template #label>
              <span class="tab-label">
                <el-icon><Collection /></el-icon>
                引用溯源
                <el-tag v-if="citations.length" size="small" type="info" effect="light">{{ citations.length }}</el-tag>
              </span>
            </template>
            <div v-if="citationsLoading" class="sidebar-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载中...</span>
            </div>
            <div v-else-if="!citations.length" class="sidebar-empty">
              <el-empty description="暂无引用溯源" :image-size="60" />
            </div>
            <div v-else class="citations-list">
              <div
                v-for="c in citations"
                :key="c.id"
                :id="`cite-card-${c.citationMarker}`"
                class="citation-card"
                :class="{ highlighted: highlightedCite === c.citationMarker }"
              >
                <div class="citation-head">
                  <span class="citation-idx">[{{ c.citationMarker }}]</span>
                  <span class="citation-file">{{ c.docTitle || '未命名来源' }}</span>
                  <el-tag v-if="c.pageStart" size="small" type="warning" effect="plain">
                    第 {{ c.pageStart }}-{{ c.pageEnd }} 页
                  </el-tag>
                </div>
                <div class="citation-body">{{ c.snippet || '（无原文片段）' }}</div>
                <div class="citation-foot">
                  <el-button size="small" text type="primary" @click="goToSource(c)">
                    <el-icon><Document /></el-icon>
                    查看原文
                  </el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane name="versions">
            <template #label>
              <span class="tab-label">
                <el-icon><Clock /></el-icon>
                版本历史
              </span>
            </template>
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
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
    <CitationPopover ref="citationPopoverRef" :report-id="reportId" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, EditPen, Download, List, Check, Close, RefreshLeft, Plus, Minus, Edit, MagicStick, Warning, VideoPlay, Collection, Document, Clock, Loading } from '@element-plus/icons-vue'
import { getReport, getReportVersions, getVersionDiffByNum, restoreVersion, exportDocx, checkReportQuality, type Report, type ReportVersion, type QualityReport } from '@/api/report'
import { renderReportMarkdown } from '@/utils/markdown'
import TiptapEditor from '@/components/editor/TiptapEditor.vue'
import CitationPopover from '@/components/editor/CitationPopover.vue'
import OutlineEditor from '@/components/outline/OutlineEditor.vue'
import SectionStreamView from '@/components/outline/SectionStreamView.vue'
import CoverageDashboard from '@/components/quality/CoverageDashboard.vue'
import { initSections, OutlineItem as ChapterOutlineItem } from '@/api/section'
import { listCitations, type ReportCitation } from '@/api/citation'
import * as Diff from 'diff'
import ReportCharts from '@/components/ReportCharts.vue'

const route = useRoute()
const reportId = Number(route.params.id)

const report = ref<Report | null>(null)
const versions = ref<ReportVersion[]>([])
const versionsLoading = ref(false)
const selectedVersionId = ref<number | null>(null)
const activeTab = ref('content')
const sidebarTab = ref('citations')

const diffFrom = ref<number | null>(null)
const diffTo = ref<number | null>(null)
const diffLoading = ref(false)
const diffResult = ref<{ oldLines: DiffLine[]; newLines: DiffLine[] } | null>(null)
const diffViewMode = ref<'split' | 'revision'>('split')

const contentEl = ref<HTMLDivElement | null>(null)
const citationPopoverRef = ref<InstanceType<typeof CitationPopover> | null>(null)
const citations = ref<ReportCitation[]>([])
const citationsLoading = ref(false)
const highlightedCite = ref<number | null>(null)

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

// —— 赛题 3.4 质量检查 ——
const qualityReport = ref<QualityReport | null>(null)
const qualityLoading = ref(false)
const qualityCheckedAt = ref<string>('')

async function runQualityCheck() {
  qualityLoading.value = true
  try {
    const res = await checkReportQuality(reportId)
    qualityReport.value = (res as any).data as QualityReport
    qualityCheckedAt.value = formatTime(new Date().toISOString())
    ElMessage.success('质量检查完成')
  } catch (e: any) {
    console.error('质量检查失败:', e)
    ElMessage.error(e?.response?.data?.message || '质量检查失败，请稍后再试')
  } finally {
    qualityLoading.value = false
  }
}

function pct(v?: number | null): string {
  if (v == null || isNaN(v)) return '—'
  const n = v <= 1 ? v * 100 : v
  return `${Math.round(n)}%`
}

function scoreTagType(v?: number | null): 'success' | 'warning' | 'danger' | 'info' {
  if (v == null) return 'info'
  const n = v <= 1 ? v * 100 : v
  if (n >= 85) return 'success'
  if (n >= 70) return 'warning'
  return 'danger'
}

function suggestionLabel(s?: string): string {
  const m: Record<string, string> = { mark: '标注待核实', fix: '修正数据', soften: '语气弱化' }
  return s ? (m[s] || s) : '建议人工复核'
}

onMounted(async () => {
  await Promise.all([loadReport(), loadVersions(), loadCitations()])
  if (versions.value.length >= 2) {
    diffFrom.value = versions.value[versions.value.length - 2].versionNum
    diffTo.value = versions.value[versions.value.length - 1].versionNum
  }
  nextTick(() => buildOutline())
  // 章节流式 tab 的 OutlineEditor 从原报告正文预填章节，避免"空大纲起手→生成→覆盖原文"
  if (outlineDraft.value.length === 0 && report.value?.content) {
    outlineDraft.value = parseOutlineFromMarkdown(report.value.content)
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

async function loadCitations() {
  citationsLoading.value = true
  try {
    const res = await listCitations(reportId)
    citations.value = (res as any).data || []
  } catch (e) {
    console.error('加载引用列表失败:', e)
  } finally {
    citationsLoading.value = false
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

/** Tiptap 编辑事件：仅本地缓存到 report.content，不立即落库（保存按钮另行处理） */
function onTiptapEdit(val: string) {
  if (report.value) report.value.content = val
}

// ====================== 章节流式 tab 状态 ======================
const outlineDraft = ref<ChapterOutlineItem[]>([])
const initSecLoading = ref(false)
const sectionStreamRef = ref<InstanceType<typeof SectionStreamView> | null>(null)

async function submitOutline() {
  if (!report.value) return
  const valid = outlineDraft.value.filter(o => o.title?.trim())
  if (valid.length === 0) { ElMessage.warning('请至少填一个有效章节'); return }
  initSecLoading.value = true
  try {
    await initSections(report.value.id, valid)
    ElMessage.success(`已初始化 ${valid.length} 个章节，可在右侧开始流式生成`)
    sectionStreamRef.value?.reload()
  } finally {
    initSecLoading.value = false
  }
}

function onSectionsAssembled(content: string) {
  if (report.value && content) {
    report.value.content = content
    nextTick(() => buildOutline())
    ElMessage.success('已合成全文写入报告正文，可切到「报告正文」tab 查看')
  }
}

/**
 * SectionStreamView 首次进入且 DB 无 section 时，会把原报告按标题切分并以 done 状态入库；
 * 完成后通过 imported 事件通知。此处把大纲回填到 OutlineEditor，让用户看到当前章节结构，
 * 方便在此基础上新增章节而不是从零开始（曾出现"只填网民观点一章→覆盖原文"的 bug）。
 */
function onSectionsImported(_count: number) {
  if (!report.value?.content) return
  const parsed = parseOutlineFromMarkdown(report.value.content)
  if (parsed.length > 0 && outlineDraft.value.length === 0) {
    outlineDraft.value = parsed
  }
}

/**
 * 与 SectionStreamView 内部的 parseReportToSections 同义的轻量版本：
 * 只抽章节标题（不抽正文）给 OutlineEditor，让用户继续编辑大纲。
 */
function parseOutlineFromMarkdown(raw: string): ChapterOutlineItem[] {
  if (!raw) return []
  const lines = raw.split(/\r?\n/)
  const headingRe = /^\s*(#{1,2}\s+.+|[一二三四五六七八九十百]+[、.．]\s*.+|\d{1,2}[、.．]\s*.+)\s*$/
  const titles: string[] = []
  for (const line of lines) {
    if (headingRe.test(line)) {
      titles.push(line.trim().replace(/^#{1,2}\s+/, '').trim())
    }
  }
  return titles.map(t => ({ title: t, prompt: '' }))
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

/** toVersion 的 change_summary（"变更背景"卡文案） */
const toVersionSummary = computed(() => {
  if (!diffTo.value) return ''
  const v = versions.value.find(x => x.versionNum === diffTo.value)
  return v?.changeSummary || ''
})

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
    if (idx) {
      const marker = parseInt(idx, 10)
      highlightedCite.value = marker
      if (citationPopoverRef.value) {
        citationPopoverRef.value.show(sup, marker)
      }
    }
  }
}

function goToSource(c: ReportCitation) {
  if (c.kbId && c.docId) {
    window.open(`/knowledge/${c.kbId}?docId=${c.docId}`, '_blank')
  } else if (c.kbId) {
    window.open(`/knowledge/${c.kbId}`, '_blank')
  } else {
    ElMessage.warning('该引用缺少原文文档信息')
  }
}

function goEdit() {
  // 以前 push('/workspace') 会把用户扔到新建报告页 —— "编辑"语义完全错了。
  // 当前页的 Tiptap Tab 就是带 AI 浮起工具栏的段落级编辑器，切过去即可。
  activeTab.value = 'tiptap'
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

/* 变更背景卡（differenze化亮点：diff ↔ 数据源叙事） */
.change-context-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 100%);
  border: 1px solid #c7d2fe;
  border-radius: 10px;
}
.change-context-icon {
  font-size: 20px;
  line-height: 1.2;
}
.change-context-body {
  flex: 1;
}
.change-context-title {
  font-size: 13px;
  font-weight: 600;
  color: #4338ca;
  margin-bottom: 4px;
}
.change-context-meta {
  font-size: 12.5px;
  color: #334155;
  line-height: 1.6;
  word-break: break-all;
}

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
  padding: 12px;
  overflow-y: auto;
}

.sidebar-tabs {
  height: 100%;
}

.sidebar-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.sidebar-tabs :deep(.el-tabs__content) {
  height: calc(100% - 50px);
  overflow-y: auto;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sidebar-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.sidebar-empty {
  padding: 20px 0;
}

.citations-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.citation-card {
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  transition: all 0.2s;
}

.citation-card.highlighted {
  background: #ede9fe;
  border-color: #c7d2fe;
}

.citation-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.citation-idx {
  display: inline-block;
  padding: 0 6px;
  border-radius: 8px;
  background: #ede9fe;
  color: #6366f1;
  font-size: 11px;
  font-weight: 600;
}

.citation-file {
  font-size: 12px;
  font-weight: 500;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.citation-body {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  max-height: 80px;
  overflow-y: auto;
  margin-bottom: 6px;
  padding: 6px 8px;
  background: #fff;
  border-radius: 4px;
  border-left: 3px solid #6366f1;
}

.citation-foot {
  display: flex;
  justify-content: flex-end;
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

/* 赛题 3.4 质量检查面板 */
.quality-panel {
  min-height: 300px;
}
.quality-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  gap: 12px;
  text-align: center;
  color: #64748b;
}
.quality-empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}
.quality-empty-desc {
  max-width: 480px;
  font-size: 14px;
  line-height: 1.7;
}
.quality-result { padding: 4px; }
.quality-header {
  display: flex;
  gap: 16px;
  align-items: stretch;
  margin-bottom: 16px;
}
.quality-score-card {
  flex-shrink: 0;
  width: 160px;
  padding: 20px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ede9fe 0%, #e0e7ff 100%);
  border: 1px solid #c7d2fe;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.quality-score-big {
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
  color: #6366f1;
  display: flex;
  align-items: baseline;
}
.quality-score-big.tone-success { color: #059669; }
.quality-score-big.tone-warning { color: #d97706; }
.quality-score-big.tone-danger { color: #dc2626; }
.quality-score-unit {
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
  margin-left: 4px;
}
.quality-score-label {
  margin-top: 6px;
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}
.quality-summary {
  flex: 1;
  padding: 20px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.quality-summary-title {
  font-size: 12px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}
.quality-summary-text {
  font-size: 15px;
  line-height: 1.7;
  color: #1e293b;
}
.quality-summary-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 12px;
}
.quality-dims {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
@media (max-width: 1100px) {
  .quality-dims { grid-template-columns: 1fr; }
}
.quality-dim-card {
  padding: 16px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dim-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dim-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}
.dim-desc {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.dim-issues {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}
.dim-issues-title {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 8px;
}
.dim-issues ul {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: #475569;
  line-height: 1.7;
}
.issue-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  padding: 8px;
  border-radius: 6px;
  background: #fef2f2;
  border: 1px solid #fecaca;
}
.issue-badge {
  flex-shrink: 0;
  width: 28px;
  height: 22px;
  border-radius: 4px;
  background: #dc2626;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.issue-badge-warn {
  background: #d97706;
}
.issue-body { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.issue-sentence {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.6;
}
.issue-reason {
  font-size: 12px;
  color: #b91c1c;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
