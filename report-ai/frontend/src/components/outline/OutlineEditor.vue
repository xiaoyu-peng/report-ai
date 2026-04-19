<template>
  <div class="outline-editor">
    <div class="outline-header">
      <h3 class="header-title">报告大纲</h3>
      <div class="header-actions">
        <el-button size="small" :icon="Plus" @click="add">添加章节</el-button>
      </div>
    </div>

    <draggable
      v-model="items"
      item-key="key"
      handle=".drag-handle"
      animation="200"
      class="outline-list"
    >
      <template #item="{ element, index }">
        <div class="outline-card">
          <div class="card-row">
            <span class="drag-handle" title="拖拽排序">⋮⋮</span>
            <span class="idx-badge">{{ index + 1 }}</span>
            <el-input
              v-model="element.title"
              placeholder="章节标题"
              size="default"
              class="title-input"
            />
            <el-button
              :icon="Delete"
              type="danger"
              link
              @click="remove(index)"
            />
          </div>
          <el-input
            v-model="element.prompt"
            type="textarea"
            :rows="2"
            placeholder="本章要点 / 要回答的问题（可选）"
            size="small"
            class="prompt-input"
          />
        </div>
      </template>
    </draggable>

    <div v-if="items.length === 0" class="empty-state">
      <el-empty description="还没有章节，点上方「添加章节」开始" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import draggable from 'vuedraggable'
import { Delete, Plus } from '@element-plus/icons-vue'

interface OutlineItem { key: string; title: string; prompt: string }

const props = defineProps<{ modelValue?: { title: string; prompt?: string }[] }>()
const emit = defineEmits<{(e: 'update:modelValue', val: any[]): void}>()

const items = ref<OutlineItem[]>(initFrom(props.modelValue))

watch(() => props.modelValue, (v) => {
  // 仅在外部覆盖式给出时同步（避免编辑回环）
  if (!v) return
  const incomingTitles = v.map(i => i.title).join('|')
  const localTitles = items.value.map(i => i.title).join('|')
  if (incomingTitles !== localTitles) items.value = initFrom(v)
}, { deep: false })

watch(items, (v) => {
  emit('update:modelValue', v.map(i => ({ title: i.title, prompt: i.prompt })))
}, { deep: true })

function initFrom(src?: { title: string; prompt?: string }[]): OutlineItem[] {
  if (!src || src.length === 0) return []
  return src.map((s, i) => ({ key: 'k' + i + '_' + Date.now(), title: s.title || '', prompt: s.prompt || '' }))
}

function add() {
  items.value.push({
    key: 'k' + items.value.length + '_' + Date.now(),
    title: '',
    prompt: '',
  })
}

function remove(i: number) { items.value.splice(i, 1) }

defineExpose({
  getOutline: () => items.value.map(i => ({ title: i.title, prompt: i.prompt })),
  setOutline: (v: { title: string; prompt?: string }[]) => { items.value = initFrom(v) },
})
</script>

<style scoped>
.outline-editor { display: flex; flex-direction: column; gap: 12px; }
.outline-header {
  display: flex; align-items: center; justify-content: space-between;
}
.header-title { margin: 0; font-size: 16px; color: #303133; font-weight: 600; }
.outline-list { display: flex; flex-direction: column; gap: 8px; min-height: 60px; }
.outline-card {
  background: #fff; border: 1px solid #ebeef5;
  border-radius: 6px; padding: 12px; transition: box-shadow .2s;
}
.outline-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.06); }
.card-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.drag-handle {
  cursor: grab; color: #c0c4cc; user-select: none;
  padding: 4px 6px; font-size: 16px; line-height: 1;
}
.drag-handle:active { cursor: grabbing; }
.idx-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 50%;
  background: #ecf5ff; color: #409eff; font-size: 12px; font-weight: 600;
}
.title-input { flex: 1; }
.prompt-input { padding-left: 40px; }
.empty-state { padding: 16px; }
</style>
