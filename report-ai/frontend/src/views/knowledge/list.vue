<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h1 class="page-title">知识库管理</h1>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        新建知识库
      </el-button>
    </div>

    <div class="category-tabs">
      <el-radio-group v-model="activeCategory" size="default" @change="fetchList">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="policy">政策法规</el-radio-button>
        <el-radio-button value="industry">行业报告</el-radio-button>
        <el-radio-button value="history">历史报告</el-radio-button>
        <el-radio-button value="media">媒体资讯</el-radio-button>
        <el-radio-button value="other">其他</el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="kb in list" :key="kb.id" :span="8">
        <el-card class="kb-card" shadow="hover" @click="goDetail(kb.id)">
          <div class="kb-icon" :class="getCategoryClass(kb.category)">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="kb-name">{{ kb.name }}</div>
          <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
          <div class="kb-meta">
            <el-tag size="small" :type="getCategoryTagType(kb.category)" effect="light">
              {{ getCategoryLabel(kb.category) }}
            </el-tag>
            <el-tag size="small" effect="light">{{ kb.docCount ?? 0 }} 文档</el-tag>
            <el-tag size="small" type="info" effect="light">{{ kb.chunkCount ?? 0 }} 分块</el-tag>
          </div>
          <div class="kb-actions" @click.stop>
            <el-button size="small" type="danger" plain @click="handleDelete(kb)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col v-if="!loading && list.length === 0" :span="24">
        <el-empty description="暂无知识库，点击右上角新建" />
      </el-col>
    </el-row>

    <el-dialog v-model="showCreateDialog" title="新建知识库" width="460px" @closed="resetForm">
      <el-form ref="formRef" :model="createForm" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入知识库名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="createForm.category" placeholder="选择知识库分类" style="width: 100%">
            <el-option label="政策法规" value="policy" />
            <el-option label="行业报告" value="industry" />
            <el-option label="历史报告" value="history" />
            <el-option label="媒体资讯" value="media" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库用途描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getKnowledgeBases,
  createKnowledgeBase,
  deleteKnowledgeBase,
  type KnowledgeBase
} from '@/api/knowledge'

const router = useRouter()
const list = ref<KnowledgeBase[]>([])
const loading = ref(false)
const submitting = ref(false)
const showCreateDialog = ref(false)
const formRef = ref<FormInstance>()
const activeCategory = ref('')

const createForm = reactive({
  name: '',
  description: '',
  category: 'other'
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度为 2-50 个字符', trigger: 'blur' }
  ]
}

const categoryMap: Record<string, string> = {
  policy: '政策法规',
  industry: '行业报告',
  history: '历史报告',
  media: '媒体资讯',
  other: '其他'
}

function getCategoryLabel(cat?: string): string {
  return categoryMap[cat || 'other'] || '其他'
}

function getCategoryTagType(cat?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    policy: 'danger',
    industry: 'warning',
    history: 'primary',
    media: 'success',
    other: 'info'
  }
  return map[cat || 'other'] || 'info'
}

function getCategoryClass(cat?: string): string {
  return cat || 'other'
}

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    // 把分类筛选下沉到后端（WHERE category = ?），避免全量拉回来再前端过滤
    const params = activeCategory.value ? { category: activeCategory.value } : undefined
    const res = await getKnowledgeBases(params)
    const raw = (res as any).data
    const all: KnowledgeBase[] = Array.isArray(raw) ? raw : Array.isArray(raw?.records) ? raw.records : []
    list.value = all
  } catch (e) {
    console.error('加载知识库列表失败:', e)
  } finally {
    loading.value = false
  }
}

function goDetail(id: number | undefined | null) {
  // 防御：列表项偶尔会出现 id 为空（创建中、接口异常），点击后旧逻辑会跳到 /knowledge/undefined → 详情页 NaN
  if (id == null || !Number.isFinite(Number(id))) {
    ElMessage.warning('该知识库尚未就绪，请稍后再试')
    return
  }
  router.push(`/knowledge/${id}`)
}

function resetForm() {
  createForm.name = ''
  createForm.description = ''
  createForm.category = 'other'
  formRef.value?.clearValidate()
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createKnowledgeBase({
      name: createForm.name,
      description: createForm.description,
      category: createForm.category
    } as any)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    fetchList()
  } catch (e) {
    console.error('创建知识库失败:', e)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(kb: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(
      `确认删除知识库「${kb.name}」？其下所有文档与分块将一并删除，此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
    await deleteKnowledgeBase(kb.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    if (e !== 'cancel') console.error('删除知识库失败:', e)
  }
}
</script>

<style scoped>
.knowledge-list {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
}
.category-tabs {
  margin-bottom: 20px;
}
.kb-card {
  cursor: pointer;
  transition: all 0.25s ease;
  margin-bottom: 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}
.kb-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #c7d2fe;
}
.kb-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 12px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}
.kb-icon.policy {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.kb-icon.industry {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}
.kb-icon.history {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}
.kb-icon.media {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.kb-icon.other {
  background: rgba(100, 116, 139, 0.1);
  color: #64748b;
}
.kb-name {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  margin: 8px 0 6px;
}
.kb-desc {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 14px;
  min-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 8px;
}
.kb-meta {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.kb-actions {
  display: flex;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s;
}
.kb-card:hover .kb-actions {
  opacity: 1;
}
</style>
