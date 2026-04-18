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
            从文件分析风格
          </el-button>
        </el-upload>
        <el-button type="primary" @click="openCreateDialog()">
          <el-icon><Plus /></el-icon>
          新建模板
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="tpl in list" :key="tpl.id" :span="8">
        <el-card class="tpl-card" shadow="hover">
          <div class="tpl-header">
            <div class="tpl-icon">
              <el-icon><DocumentCopy /></el-icon>
            </div>
            <el-tag v-if="tpl.isBuiltin" type="warning" size="small" effect="light">内置</el-tag>
          </div>
          <div class="tpl-name">{{ tpl.name }}</div>
          <div class="tpl-desc">{{ tpl.description || '暂无描述' }}</div>
          <div class="tpl-meta" v-if="tpl.category">
            <el-tag size="small" type="info" effect="plain">{{ tpl.category }}</el-tag>
          </div>
          <div class="tpl-actions">
            <el-button size="small" @click="previewTemplate(tpl)">预览</el-button>
            <el-button
              v-if="!tpl.isBuiltin"
              size="small"
              type="danger"
              plain
              @click="handleDelete(tpl)"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col v-if="!loading && list.length === 0" :span="24">
        <el-empty description="暂无模板，点击右上角新建或上传文件分析风格" />
      </el-col>
    </el-row>

    <!-- 新建模板对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      :title="dialogTitle"
      width="680px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="createForm" :rules="rules" label-width="100px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入模板名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="2"
            placeholder="一句话说明模板用途（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="风格描述" prop="styleDescription">
          <el-input
            v-model="createForm.styleDescription"
            type="textarea"
            :rows="4"
            placeholder="AI 分析出的风格描述，可手动编辑"
          />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="6"
            placeholder="模板正文 / 结构大纲（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="showPreviewDialog" :title="previewTpl?.name || '模板预览'" width="720px">
      <div class="preview-block">
        <div class="preview-label">描述</div>
        <div class="preview-content">{{ previewTpl?.description || '暂无' }}</div>
      </div>
      <div class="preview-block">
        <div class="preview-label">风格</div>
        <div class="preview-content">{{ previewTpl?.style || previewTpl?.styleDescription || '暂无' }}</div>
      </div>
      <div class="preview-block">
        <div class="preview-label">结构 / 内容</div>
        <div class="preview-content preview-pre">{{ previewTpl?.structure || previewTpl?.content || '暂无' }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRawFile } from 'element-plus'
import { Plus, Upload, Delete, DocumentCopy } from '@element-plus/icons-vue'
import axios from 'axios'
import { getTemplates, createTemplate, deleteTemplate } from '@/api/report'
import { useUserStore } from '@/stores/user'

interface TemplateItem {
  id: number
  name: string
  description?: string
  category?: string
  structure?: string
  style?: string
  styleDescription?: string
  content?: string
  isBuiltin?: boolean
  createdAt?: string
}

const list = ref<TemplateItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const analyzing = ref(false)
const showCreateDialog = ref(false)
const showPreviewDialog = ref(false)
const previewTpl = ref<TemplateItem | null>(null)
const formRef = ref<FormInstance>()

const createForm = reactive({
  name: '',
  description: '',
  styleDescription: '',
  content: ''
})

const dialogTitle = computed(() =>
  createForm.styleDescription ? '新建模板（已导入 AI 风格）' : '新建模板'
)

const rules: FormRules = {
  name: [
    { required: true, message: '请输入模板名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度为 2-50 个字符', trigger: 'blur' }
  ]
}

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await getTemplates()
    list.value = (res.data as TemplateItem[]) || []
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
  createForm.styleDescription = ''
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
    const resp = await axios.post('/api/v1/templates/analyze', form, {
      headers: {
        'Content-Type': 'multipart/form-data',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      timeout: 120000
    })
    const payload = resp.data?.data ?? resp.data
    const styleDesc =
      (typeof payload === 'string' ? payload : payload?.styleDescription || payload?.style || payload?.description) || ''
    createForm.styleDescription = styleDesc
    createForm.name = createForm.name || (file.name?.replace(/\.[^.]+$/, '') ?? '')
    showCreateDialog.value = true
    ElMessage.success('风格分析完成，已填充到新建模板表单')
  } catch (e: any) {
    console.error('分析风格失败:', e)
    ElMessage.error(e?.response?.data?.message || '分析风格失败')
  } finally {
    analyzing.value = false
  }
  // 阻止 ElUpload 自动上传
  return false
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createTemplate({
      name: createForm.name,
      description: createForm.description,
      style: createForm.styleDescription,
      structure: createForm.content
    } as any)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    fetchList()
  } catch (e) {
    console.error('创建模板失败:', e)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(tpl: TemplateItem) {
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

function previewTemplate(tpl: TemplateItem) {
  previewTpl.value = tpl
  showPreviewDialog.value = true
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
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.tpl-card {
  margin-bottom: 16px;
  border-radius: 8px;
  transition: all 0.25s ease;
  min-height: 200px;
}
.tpl-card:hover {
  transform: translateY(-2px);
}
.tpl-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.tpl-icon {
  font-size: 28px;
  color: #409eff;
}
.tpl-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 6px 0;
}
.tpl-desc {
  color: #909399;
  font-size: 13px;
  min-height: 40px;
  line-height: 1.6;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tpl-meta {
  margin-bottom: 12px;
}
.tpl-actions {
  display: flex;
  gap: 8px;
}
.preview-block {
  margin-bottom: 14px;
}
.preview-label {
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.preview-content {
  color: #606266;
  font-size: 14px;
  line-height: 1.7;
  background: #f7f8fa;
  border-radius: 6px;
  padding: 10px 12px;
  max-height: 240px;
  overflow: auto;
}
.preview-pre {
  white-space: pre-wrap;
  font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
  font-size: 13px;
}
</style>
