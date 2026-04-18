<template>
  <div class="report-charts">
    <div v-for="(chart, i) in charts" :key="i" class="chart-block">
      <h4 v-if="chart.title" class="chart-title">{{ chart.title }}</h4>
      <EChartsChart :option="chart.option" height="280px" />
      <div v-if="chart.description" class="chart-caption">
        图{{ i + 1 }}：{{ chart.description }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import EChartsChart from '@/components/EChartsChart.vue'
import { mcpOverview, mcpEmotionalDistribution, mcpDatasourceSound, mcpHotWords } from '@/api/report'

interface ChartItem {
  title: string
  description: string
  option: Record<string, any>
}

const props = defineProps<{
  topic: string
  startDate?: string
  endDate?: string
}>()

const charts = ref<ChartItem[]>([])

onMounted(() => loadCharts())
watch(() => props.topic, () => loadCharts())

async function loadCharts() {
  if (!props.topic) return
  charts.value = []

  try {
    const [overviewRes, emotionalRes, datasourceRes, hotWordsRes] = await Promise.allSettled([
      mcpOverview(props.topic, props.startDate, props.endDate),
      mcpEmotionalDistribution(props.topic, props.startDate, props.endDate),
      mcpDatasourceSound(props.topic, props.startDate, props.endDate),
      mcpHotWords(props.topic, props.startDate, props.endDate)
    ])

    if (overviewRes.status === 'fulfilled') {
      const data = (overviewRes.value as any).data
      if (data?.channelDistribution) {
        charts.value.push({
          title: '渠道分布',
          description: `${props.topic}舆情信息渠道来源分布`,
          option: buildPieChart(data.channelDistribution)
        })
      }
    }

    if (emotionalRes.status === 'fulfilled') {
      const data = (emotionalRes.value as any).data
      if (data?.emotionDistribution || data?.emotionalDistribution) {
        const dist = data.emotionDistribution || data.emotionalDistribution
        charts.value.push({
          title: '情感分布',
          description: `${props.topic}舆情情感倾向分布`,
          option: buildPieChart(dist)
        })
      }
    }

    if (datasourceRes.status === 'fulfilled') {
      const data = (datasourceRes.value as any).data
      if (data?.datasourceSound || data?.channelSound) {
        const dist = data.datasourceSound || data.channelSound
        charts.value.push({
          title: '渠道声量',
          description: `${props.topic}各渠道声量对比`,
          option: buildBarChart(dist)
        })
      }
    }

    if (hotWordsRes.status === 'fulfilled') {
      const data = (hotWordsRes.value as any).data
      if (data?.hotWords || data?.words) {
        const words = data.hotWords || data.words
        charts.value.push({
          title: '热门词云',
          description: `${props.topic}高频关键词词云`,
          option: buildWordCloud(words)
        })
      }
    }
  } catch (e) {
    console.error('加载图表数据失败:', e)
  }
}

function buildPieChart(data: any): Record<string, any> {
  if (!Array.isArray(data)) return {}
  const seriesData = data.map((item: any) => ({
    name: item.name || item.key || item.channelName || '',
    value: item.value || item.count || item.num || 0
  }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    color: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316', '#ec4899'],
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: seriesData
    }]
  }
}

function buildBarChart(data: any): Record<string, any> {
  if (!Array.isArray(data)) return {}
  const names = data.map((item: any) => item.name || item.key || item.channelName || '')
  const values = data.map((item: any) => item.value || item.count || item.num || 0)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#6366f1' },
            { offset: 1, color: '#818cf8' }
          ]
        },
        borderRadius: [4, 4, 0, 0]
      }
    }]
  }
}

function buildWordCloud(data: any): Record<string, any> {
  if (!Array.isArray(data)) return {}
  const words = data.slice(0, 30).map((item: any) => ({
    name: item.name || item.word || item.key || '',
    value: item.value || item.count || item.weight || 0
  }))
  return {
    tooltip: {},
    series: [{
      type: 'wordCloud',
      shape: 'circle',
      left: 'center',
      top: 'center',
      width: '80%',
      height: '80%',
      sizeRange: [14, 48],
      rotationRange: [-30, 30],
      gridSize: 8,
      textStyle: { fontFamily: 'PingFang SC' },
      data: words
    }]
  }
}

defineExpose({ charts })
</script>

<style scoped>
.report-charts {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 16px 0;
}
.chart-block {
  flex: 1;
  min-width: 300px;
  max-width: 50%;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px;
  background: #fff;
  transition: box-shadow 0.2s;
}
.chart-block:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin: 0 0 8px;
}
.chart-caption {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}
</style>
