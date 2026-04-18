<template>
  <div class="knowledge-list">
    <div class="page-header">
      <h1 class="page-title">知识库管理</h1>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        新建知识库
      </el-button>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="kb in list" :key="kb.id" :span="8">
        <el-card class="kb-card" shadow="hover" @click="goDetail(kb.id)">
          <div class="kb-icon">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="kb-name">{{ kb.name }}</div>
          <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
          <div class="kb-stats">
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

const createForm = reactive({
  name: '',
  description: ''
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度为 2-50 个字符', trigger: 'blur' }
  ]
}

onMounted(fetchList)

async function fetchList() {
  loading.value = true
  try {
    const res = await getKnowledgeBases()
    list.value = (res.data as KnowledgeBase[]) || []
  } catch (e) {
    console.error('加载知识库列表失败:', e)
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  router.push(`/knowledge/${id}`)
}

function resetForm() {
  createForm.name = ''
  createForm.description = ''
  formRef.value?.clearValidate()
}

async function handleCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createKnowledgeBase({
      name: createForm.name,
      description: createForm.description
    })
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
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.kb-card {
  cursor: pointer;
  transition: all 0.25s ease;
  margin-bottom: 16px;
  text-align: center;
  border-radius: 8px;
}
.kb-card:hover {
  transform: translateY(-2px);
}
.kb-icon {
  font-size: 36px;
  color: #409eff;
  margin: 4px 0 8px;
}
.kb-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 8px 0 6px;
}
.kb-desc {
  color: #909399;
  font-size: 13px;
  margin-bottom: 14px;
  min-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 8px;
}
.kb-stats {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 14px;
}
.kb-actions {
  display: flex;
  justify-content: center;
}
</style>
