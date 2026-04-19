<template>
  <div class="coverage-dashboard">
    <div class="head">
      <h3 class="title">📊 报告体检（T5 覆盖度 + KB 命中 + 事实性）</h3>
      <el-button size="small" plain :loading="loading" @click="refresh">重新检查</el-button>
    </div>

    <el-row v-if="quality" :gutter="16" class="kpis">
      <el-col :span="8">
        <div class="kpi" :class="coverageBadge">
          <div class="kpi-num">{{ quality.coverageRate || 0 }}%</div>
          <div class="kpi-lbl">引用覆盖率</div>
          <div class="kpi-sub">{{ quality.paragraphsCited || 0 }} / {{ quality.paragraphsTotal || 0 }} 段</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi">
          <div class="kpi-num">{{ quality.citationsTotal || 0 }}</div>
          <div class="kpi-lbl">引用总数</div>
          <div class="kpi-sub">来自知识库的可溯源引用</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="kpi">
          <div class="kpi-num">{{ kbCount }}</div>
          <div class="kpi-lbl">命中知识库</div>
          <div class="kpi-sub">不同来源的多样性</div>
        </div>
      </el-col>
    </el-row>

    <el-card v-if="kbDistArr.length > 0" shadow="never" class="chart-card">
      <template #header><span>📈 知识库命中分布</span></template>
      <v-chart :option="pieOption" :init-options="{ renderer: 'canvas' }" autoresize style="height: 300px" />
    </el-card>

    <el-card v-if="suspicious.length > 0" shadow="never" class="susp-card">
      <template #header><span>⚠️ 事实性可疑列表（{{ suspicious.length }} 条）</span></template>
      <ul class="susp-list">
        <li v-for="(s, i) in suspicious" :key="i" :class="'sev-' + (s.severity || 'warn')">
          <div class="susp-row">
            <strong class="susp-text">{{ s.text || s.sentence || '(无文本)' }}</strong>
            <el-tag size="small" :type="severityType(s.severity)">{{ severityLabel(s.severity) }}</el-tag>
          </div>
          <div class="susp-reason">{{ s.reason }}</div>
          <div v-if="s.suggestion" class="susp-fix">建议：{{ suggestionLabel(s.suggestion) }}</div>
        </li>
      </ul>
    </el-card>

    <el-empty v-if="!loading && !quality" description="还没有体检数据，点上方「重新检查」开始" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getQuality, recheckQuality, ReportQuality } from '@/api/quality'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])

const props = defineProps<{ reportId: number }>()
const quality = ref<ReportQuality | null>(null)
const loading = ref(false)

const kbDistObj = computed<Record<string, number>>(() => {
  const v = quality.value?.kbDistribution
  if (!v) return {}
  if (typeof v === 'string') {
    try { return JSON.parse(v) } catch { return {} }
  }
  return v as Record<string, number>
})
const kbDistArr = computed(() =>
  Object.entries(kbDistObj.value).map(([k, n]) => ({ name: k, value: n }))
)
const kbCount = computed(() => kbDistArr.value.length)

const suspicious = computed<any[]>(() => {
  const v = quality.value?.suspiciousFacts
  if (!v) return []
  if (typeof v === 'string') {
    try { const j = JSON.parse(v); return Array.isArray(j) ? j : [] } catch { return [] }
  }
  return Array.isArray(v) ? v : []
})

const coverageBadge = computed(() => {
  const r = Number(quality.value?.coverageRate || 0)
  if (r >= 80) return 'good'
  if (r >= 50) return 'mid'
  return 'low'
})

const pieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'horizontal', bottom: 0, type: 'scroll' },
  series: [{
    type: 'pie',
    radius: ['40%', '65%'],
    avoidLabelOverlap: true,
    label: { show: true, formatter: '{b}\n{d}%' },
    data: kbDistArr.value,
  }],
}))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getQuality(props.reportId)
    quality.value = (res.data as any) || null
  } catch (_) {
    quality.value = null
  } finally {
    loading.value = false
  }
}

async function refresh() {
  loading.value = true
  try {
    const res = await recheckQuality(props.reportId)
    quality.value = (res.data as any) || null
    ElMessage.success('体检完成')
  } catch (e: any) {
    ElMessage.error('体检失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

function severityType(s?: string) {
  return s === 'warn' ? 'warning' : s === 'info' ? 'info' : 'danger'
}
function severityLabel(s?: string) {
  return s === 'warn' ? '提醒' : s === 'info' ? '提示' : '严重'
}
function suggestionLabel(s?: string) {
  return ({ mark: '标 [待核实]', fix: '改正数据', soften: '软化措辞' } as any)[s || ''] || s
}
</script>

<style scoped>
.coverage-dashboard { display: flex; flex-direction: column; gap: 14px; }
.head {
  display: flex; align-items: center; justify-content: space-between;
}
.title { margin: 0; font-size: 16px; color: #303133; }
.kpis .kpi {
  text-align: center; padding: 16px 8px;
  background: #f5f7fa; border-radius: 8px;
  border-top: 3px solid #909399;
}
.kpis .kpi.good { border-top-color: #67c23a; }
.kpis .kpi.mid  { border-top-color: #e6a23c; }
.kpis .kpi.low  { border-top-color: #f56c6c; }
.kpi-num { font-size: 28px; font-weight: 700; color: #303133; }
.kpi-lbl { font-size: 13px; color: #606266; margin-top: 4px; }
.kpi-sub { font-size: 11px; color: #909399; margin-top: 2px; }
.chart-card, .susp-card { border: 1px solid #ebeef5; }
.susp-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.susp-list li { padding: 10px 12px; background: #fffbf2; border-left: 3px solid #e6a23c; border-radius: 4px; }
.susp-list li.sev-info { background: #f0f9ff; border-left-color: #909399; }
.susp-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.susp-text { color: #303133; font-size: 14px; }
.susp-reason { color: #606266; font-size: 13px; line-height: 1.6; }
.susp-fix { color: #67c23a; font-size: 12px; margin-top: 4px; }
</style>
