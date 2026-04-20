<template>
  <div class="templates-page">
    <div class="page-header">
      <h1 class="page-title">模板中心</h1>
      <div class="header-actions">
        <el-upload
          :before-upload="handleAnalyzeUpload"
          :show-file-list="false"
          accept=".pdf,.doc,.docx,.txt,.md"
        >
          <el-button :loading="analyzing">
            <el-icon><Upload /></el-icon>
            上传参考报告（解析风格+正文）
          </el-button>
        </el-upload>
        <el-button type="primary" @click="openCreateDialog()">
          <el-icon><Plus /></el-icon>
          新建模板
        </el-button>
      </div>
    </div>

    <p class="page-hint">
      模板是「以稿写稿」的<strong>风格样本</strong>——上传参考报告后，后端用 Tika 抽出原文 + LLM 提取开头段落、章节结构、语气和高频金句，<strong>完整原文一并留存</strong>；生成新报告时 LLM 参考它复现写作风格。（知识库管的是事实素材，这里管的是写法。）
    </p>

    <el-table
      v-loading="loading"
      :data="list"
      row-key="id"
      stripe
      style="width: 100%"
      empty-text="暂无模板，点击右上角上传参考报告或新建"
      @row-click="(row) => previewTemplate(row)"
    >
      <el-table-column label="名称" min-width="200">
        <template #default="{ row }">
          <div class="tpl-name-cell">
            <div class="tpl-icon" :style="getTplColor(row.id)">
              <el-icon :size="14"><DocumentCopy /></el-icon>
            </div>
            <span class="tpl-name-text">{{ row.name }}</span>
            <el-tag v-if="row.isBuiltin" type="warning" size="small" effect="plain">内置</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="风格摘要" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="style-cell">{{ row.styleDescription || row.style || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="正文预览" min-width="280">
        <template #default="{ row }">
          <div class="preview-cell">{{ truncate(row.content, 120) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="字数" width="90" align="right">
        <template #default="{ row }">{{ (row.content?.length ?? 0).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <div class="row-actions" @click.stop>
            <el-button size="small" type="primary" link @click="previewTemplate(row)">查看</el-button>
            <el-button
              v-if="!row.isBuiltin"
              size="small"
              type="danger"
              link
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建（手动）对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建模板" width="680px" @closed="resetForm">
      <el-form ref="formRef" :model="createForm" :rules="rules" label-width="100px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="createForm.name" placeholder="如：专题日报 / 行业研报" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="2"
            placeholder="一句话说明适用场景"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="模板正文" prop="content">
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="10"
            placeholder="粘贴参考报告正文，保存时后端会自动跑一次 LLM 风格分析，把摘要+结构写入模板"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="showPreviewDialog" :title="previewTpl?.name || '模板预览'" width="860px" top="6vh">
      <div v-if="previewTpl" class="preview-wrap">
        <div class="preview-meta">
          <div class="meta-item">
            <span class="meta-key">风格摘要</span>
            <span class="meta-val">{{ previewTpl.styleDescription || previewTpl.style || '—' }}</span>
          </div>
          <div class="meta-item" v-if="previewTpl.description">
            <span class="meta-key">描述</span>
            <span class="meta-val">{{ previewTpl.description }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-key">字数</span>
            <span class="meta-val">{{ (previewTpl.content?.length ?? 0).toLocaleString() }} 字</span>
          </div>
        </div>

        <el-tabs v-model="previewTab" class="preview-tabs">
          <el-tab-pane label="原文正文" name="content">
            <div class="preview-content preview-pre">{{ previewTpl.content || '暂无正文' }}</div>
          </el-tab-pane>
          <el-tab-pane label="结构大纲" name="structure">
            <div class="structure-tags" v-if="structureSections.length">
              <el-tag v-for="(s, i) in structureSections" :key="i" size="small" effect="plain" type="info">
                {{ s }}
              </el-tag>
            </div>
            <div v-else class="preview-empty">结构未解析</div>
          </el-tab-pane>
          <el-tab-pane label="风格 JSON" name="raw">
            <pre class="preview-content preview-json">{{ prettyJson(previewTpl.structureJson || previewTpl.structure) }}</pre>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRawFile } from 'element-plus'
import axios from 'axios'
import { getTemplates, createTemplate, deleteTemplate, type Template } from '@/api/report'
import { useUserStore } from '@/stores/user'

const list = ref<Template[]>([])
const loading = ref(false)
const submitting = ref(false)
const analyzing = ref(false)
const showCreateDialog = ref(false)
const showPreviewDialog = ref(false)
const previewTpl = ref<Template | null>(null)
const previewTab = ref('content')
const formRef = ref<FormInstance>()

const createForm = reactive({
  name: '',
  description: '',
  content: ''
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入模板名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度为 2-50 个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请粘贴参考正文', trigger: 'blur' }]
}

const structureSections = computed(() => {
  if (!previewTpl.value) return []
  const raw = previewTpl.value.structureJson || previewTpl.value.structure
  if (!raw) return []
  try {
    const obj = JSON.parse(raw)
    if (Array.isArray(obj.section_hierarchy)) return obj.section_hierarchy
    if (Array.isArray(obj.sections)) return obj.sections
    if (Array.isArray(obj)) return obj.map(String)
  } catch {}
  return []
})

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await getTemplates()
    const raw = (res as any).data
    list.value = Array.isArray(raw) ? raw : Array.isArray(raw?.records) ? raw.records : []
  } catch (e) {
    console.error('加载模板失败:', e)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  showCreateDialog.value = true
}

function resetForm() {
  createForm.name = ''
  createForm.description = ''
  createForm.content = ''
  formRef.value?.clearValidate()
}

async function handleAnalyzeUpload(file: UploadRawFile) {
  analyzing.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const userStore = useUserStore()
    const token = userStore.token || localStorage.getItem('token')
    // /analyze-file 后端会：Tika 抽文本 → LLM 风格分析 → 落库（name/description/content/styleDescription/structureJson 全齐）
    // 也就是说一次调用就落好模板，不需要前端再起保存对话框
    const resp = await axios.post('/api/v1/templates/analyze-file', form, {
      headers: {
        'Content-Type': 'multipart/form-data',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      timeout: 180000
    })
    const saved = resp.data?.data ?? resp.data
    ElMessage.success(`已解析并保存模板：${saved?.name || file.name}`)
    await fetchList()
  } catch (e: any) {
    console.error('分析风格失败:', e)
    ElMessage.error(e?.response?.data?.message || '分析风格失败')
  } finally {
    analyzing.value = false
  }
  return false
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 后端 CreateDto 接收 { name, description, content }，content 必填；LLM 风格分析由后端发起
    await createTemplate({
      name: createForm.name,
      description: createForm.description,
      content: createForm.content
    } as any)
    ElMessage.success('创建成功，已触发风格分析')
    showCreateDialog.value = false
    fetchList()
  } catch (e) {
    console.error('创建模板失败:', e)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(tpl: Template) {
  try {
    await ElMessageBox.confirm(
      `确认删除模板「${tpl.name}」？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
    await deleteTemplate(tpl.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    if (e !== 'cancel') console.error('删除模板失败:', e)
  }
}

function previewTemplate(tpl: Template) {
  previewTpl.value = tpl
  previewTab.value = 'content'
  showPreviewDialog.value = true
}

function getTplColor(id: number): { background: string; color: string } {
  const colors = [
    { background: 'rgba(99, 102, 241, 0.12)', color: '#6366f1' },
    { background: 'rgba(16, 185, 129, 0.12)', color: '#10b981' },
    { background: 'rgba(245, 158, 11, 0.12)', color: '#f59e0b' },
    { background: 'rgba(239, 68, 68, 0.12)', color: '#ef4444' },
    { background: 'rgba(139, 92, 246, 0.12)', color: '#8b5cf6' },
    { background: 'rgba(14, 165, 233, 0.12)', color: '#0ea5e9' }
  ]
  return colors[(id - 1) % colors.length]
}

function truncate(s: string | undefined, n: number): string {
  if (!s) return '—'
  const collapsed = s.replace(/\s+/g, ' ').trim()
  return collapsed.length > n ? collapsed.slice(0, n) + '…' : collapsed
}

function formatTime(t?: string): string {
  if (!t) return '—'
  const s = t.replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

function prettyJson(s?: string): string {
  if (!s) return '暂无'
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}
</script>

<style scoped>
.templates-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.page-hint {
  color: #64748b;
  font-size: 13px;
  background: #f5f3ff;
  border-left: 3px solid #6366f1;
  padding: 10px 14px;
  border-radius: 4px;
  margin: 0 0 16px 0;
  line-height: 1.7;
}
.page-hint strong {
  color: #4338ca;
}

.tpl-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.tpl-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tpl-name-text {
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.style-cell {
  color: #475569;
  font-size: 13px;
}
.preview-cell {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.row-actions {
  display: inline-flex;
  gap: 4px;
}
:deep(.el-table__row) { cursor: pointer; }

.preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.preview-meta {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}
.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}
.meta-key {
  color: #94a3b8;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.meta-val {
  color: #0f172a;
  font-size: 13px;
}
.preview-tabs {
  margin-top: 4px;
}
.preview-content {
  color: #334155;
  font-size: 14px;
  line-height: 1.8;
  background: #fafbfc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 14px 16px;
  max-height: 520px;
  overflow: auto;
  white-space: pre-wrap;
}
.preview-pre {
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}
.preview-json {
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 12.5px;
  white-space: pre;
}
.structure-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 0;
}
.preview-empty {
  color: #94a3b8;
  font-size: 13px;
  padding: 16px 0;
  text-align: center;
}
</style>
