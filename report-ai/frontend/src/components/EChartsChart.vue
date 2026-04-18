<template>
  <div ref="chartEl" :style="{ width: width, height: height }" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(defineProps<{
  option?: Record<string, any>
  width?: string
  height?: string
}>(), {
  width: '100%',
  height: '300px'
})

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

onMounted(() => {
  nextTick(() => {
    if (chartEl.value) {
      chart = echarts.init(chartEl.value)
      if (props.option) {
        chart.setOption(props.option)
      }
    }
  })
})

onBeforeUnmount(() => {
  if (chart) {
    chart.dispose()
    chart = null
  }
})

watch(() => props.option, (newOption) => {
  if (chart && newOption) {
    chart.setOption(newOption, true)
  }
}, { deep: true })

defineExpose({ chart })
</script>
