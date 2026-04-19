<template>
  <div class="tiptap-wrapper">
    <!-- 自实现的浮动工具栏：选中文本时浮起 -->
    <div
      v-show="bubble.show"
      class="bubble-menu"
      :style="{ top: bubble.top + 'px', left: bubble.left + 'px' }"
      @mousedown.prevent
    >
      <el-button size="small" :loading="aiLoading" @click="onAi('default')">✨ 优化</el-button>
      <el-button size="small" :loading="aiLoading" @click="onAi('expand')">➕ 扩展</el-button>
      <el-button size="small" :loading="aiLoading" @click="onAi('condense')">✂️ 精简</el-button>
    </div>

    <EditorContent :editor="editor" class="tiptap-content" @click="onContentClick" />
    <CitationPopover ref="popoverRef" :report-id="reportId" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { ElMessage } from 'element-plus'
import { CitationMark } from './CitationMark'
import CitationPopover from './CitationPopover.vue'
import request from '@/utils/request'

const props = defineProps<{ modelValue: string; reportId: number; readonly?: boolean }>()
const emit = defineEmits<{(e: 'update:modelValue', val: string): void}>()

const popoverRef = ref<InstanceType<typeof CitationPopover> | null>(null)
const aiLoading = ref(false)
const bubble = reactive({ show: false, top: 0, left: 0 })

const editor = useEditor({
  content: markdownToHtml(props.modelValue || ''),
  editable: !props.readonly,
  extensions: [StarterKit, CitationMark],
  onUpdate({ editor }) {
    emit('update:modelValue', htmlToMarkdown(editor.getHTML()))
    updateBubble()
  },
  onSelectionUpdate() {
    updateBubble()
  },
  onBlur() {
    // 延迟隐藏，避免点击 BubbleMenu 按钮时菜单消失
    setTimeout(() => { bubble.show = false }, 200)
  },
})

watch(() => props.modelValue, (val) => {
  if (!editor.value) return
  const current = htmlToMarkdown(editor.value.getHTML())
  if (current === val) return
  editor.value.commands.setContent(markdownToHtml(val || ''))
})

watch(() => props.readonly, (ro) => {
  editor.value?.setEditable(!ro)
})

onBeforeUnmount(() => editor.value?.destroy())

function updateBubble() {
  if (!editor.value || props.readonly) { bubble.show = false; return }
  const sel = editor.value.state.selection
  if (sel.empty) { bubble.show = false; return }
  const view = editor.value.view
  const start = view.coordsAtPos(sel.from)
  const end = view.coordsAtPos(sel.to)
  const wrapper = (view.dom as HTMLElement).getBoundingClientRect()
  bubble.show = true
  // 浮起在选区上方居中
  const centerX = (start.left + end.right) / 2
  bubble.top = start.top - wrapper.top - 44   // 顶部留 44px 给 menu
  bubble.left = centerX - wrapper.left - 90    // menu 宽 ~180，居中
}

function markdownToHtml(md: string): string {
  if (!md) return ''
  const blocks = md.split(/\n{2,}/)
  return blocks.map(block => {
    const trimmed = block.trim()
    if (!trimmed) return ''
    const h = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (h) {
      const level = h[1].length
      return `<h${level}>${escapeAndCite(h[2])}</h${level}>`
    }
    return `<p>${escapeAndCite(trimmed).replace(/\n/g, '<br>')}</p>`
  }).join('')
}

function escapeAndCite(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\[(\d{1,3})\](?!\()/g,
      (_, n) => `<sup data-citation="${n}" class="citation-marker">[${n}]</sup>`)
}

function htmlToMarkdown(html: string): string {
  const tmp = document.createElement('div')
  tmp.innerHTML = html
  tmp.querySelectorAll('sup[data-citation]').forEach(s => {
    s.replaceWith(`[${s.getAttribute('data-citation')}]`)
  })
  const out: string[] = []
  tmp.childNodes.forEach(node => {
    if (node.nodeType !== 1) return
    const el = node as HTMLElement
    const tag = el.tagName.toLowerCase()
    if (/^h[1-6]$/.test(tag)) {
      const level = parseInt(tag[1], 10)
      out.push('#'.repeat(level) + ' ' + (el.textContent || '').trim())
    } else if (tag === 'p') {
      out.push((el.textContent || '').trim())
    } else {
      out.push((el.textContent || '').trim())
    }
  })
  return out.filter(Boolean).join('\n\n')
}

function onContentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target?.dataset?.citation) {
    e.preventDefault()
    e.stopPropagation()
    popoverRef.value?.show(target, parseInt(target.dataset.citation, 10))
  }
}

async function onAi(mode: 'default' | 'expand' | 'condense') {
  if (!editor.value) return
  const sel = editor.value.state.selection
  if (sel.empty) {
    ElMessage.warning('请先选中要改写的段落')
    return
  }
  const text = editor.value.state.doc.textBetween(sel.from, sel.to, '\n')
  if (!text.trim()) return
  aiLoading.value = true
  try {
    const res = await request.post(`/v1/reports/${props.reportId}/rewrite/section`, {
      content: text,
      mode,
    })
    const newText = (res.data as any) || ''
    if (newText) {
      editor.value.chain().focus().deleteSelection().insertContent(newText).run()
      ElMessage.success('已改写')
      bubble.show = false
    }
  } catch (e: any) {
    ElMessage.error('改写失败：' + (e?.message || e))
  } finally {
    aiLoading.value = false
  }
}
</script>

<style scoped>
.tiptap-wrapper { position: relative; }
.tiptap-content {
  min-height: 400px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px 20px;
}
.bubble-menu {
  position: absolute; z-index: 1000;
  display: flex; gap: 4px;
  background: #fff; padding: 4px 6px;
  border-radius: 6px; box-shadow: 0 4px 16px rgba(0, 0, 0, .15);
  border: 1px solid #ebeef5; white-space: nowrap;
  pointer-events: auto;
}
:deep(.tiptap-content .ProseMirror) {
  outline: none; min-height: 360px;
  font-size: 14px; line-height: 1.85; color: #303133;
}
:deep(.tiptap-content .ProseMirror p) { margin: 0 0 12px; }
:deep(.tiptap-content .ProseMirror h1) { font-size: 22px; margin: 18px 0 10px; }
:deep(.tiptap-content .ProseMirror h2) { font-size: 18px; margin: 16px 0 10px; }
:deep(.tiptap-content .ProseMirror h3) { font-size: 16px; margin: 14px 0 8px; }
:deep(.citation-marker) {
  color: #409eff; cursor: pointer; padding: 1px 4px; margin: 0 1px;
  background: #ecf5ff; border-radius: 3px; font-size: 11px;
  font-weight: 500; vertical-align: super; line-height: 1;
}
:deep(.citation-marker:hover) { background: #d9ecff; }
</style>
