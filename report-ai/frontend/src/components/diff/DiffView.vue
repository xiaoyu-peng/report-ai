<template>
  <div class="diff-view">
    <div class="diff-toolbar">
      <span class="diff-stats">
        <span class="stat-add">+{{ stats.added }} 新增</span>
        <span class="stat-del">-{{ stats.removed }} 删除</span>
      </span>
      <div class="diff-actions">
        <el-button v-if="showActions" size="small" type="success" plain @click="emit('accept')">全部接受</el-button>
        <el-button v-if="showActions" size="small" type="danger" plain @click="emit('reject')">全部拒绝</el-button>
      </div>
    </div>
    <div class="diff-content" v-html="diffHtml"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DiffMatchPatch from 'diff-match-patch'

const props = defineProps<{ before: string; after: string; showActions?: boolean }>()
const emit = defineEmits<{(e: 'accept'): void; (e: 'reject'): void}>()

const diffs = computed(() => {
  const dmp = new DiffMatchPatch()
  const list = dmp.diff_main(props.before || '', props.after || '')
  dmp.diff_cleanupSemantic(list)
  return list
})

const stats = computed(() => {
  let added = 0, removed = 0
  for (const [op, txt] of diffs.value) {
    if (op === 1) added += txt.length
    else if (op === -1) removed += txt.length
  }
  return { added, removed }
})

const diffHtml = computed(() => {
  return diffs.value.map(([op, txt]) => {
    const safe = (txt as string)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>')
    if (op === 1) return `<span class="diff-add">${safe}</span>`
    if (op === -1) return `<span class="diff-del">${safe}</span>`
    return `<span class="diff-eq">${safe}</span>`
  }).join('')
})
</script>

<style scoped>
.diff-view {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden;
}
.diff-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; background: #f5f7fa; border-bottom: 1px solid #ebeef5;
}
.diff-stats { display: flex; gap: 12px; font-size: 13px; }
.stat-add { color: #1f7a4d; }
.stat-del { color: #9b1c1c; }
.diff-actions { display: flex; gap: 8px; }
.diff-content {
  padding: 14px 18px; line-height: 1.85; max-height: 600px; overflow-y: auto;
  font-size: 14px; color: #303133;
}
:deep(.diff-add) { background: #e7f6ec; color: #1f7a4d; padding: 0 2px; border-radius: 2px; }
:deep(.diff-del) { background: #fde8e8; color: #9b1c1c; text-decoration: line-through; padding: 0 2px; border-radius: 2px; }
:deep(.diff-eq) { color: #606266; }
</style>
