<template>
  <div class="kb-detail">
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" plain @click="router.back()">返回</el-button>
        <span class="kb-title">{{ kb?.name || '知识库详情' }}</span>
        <el-tag v-if="kb" size="small" type="info" effect="light">
          {{ documents.length }} 篇文档
        </el-tag>
      </div>
      <el-upload
        :show-file-list="false"
        :before-upload="handleUpload"
        accept=".pdf,.doc,.docx,.txt,.md"
      >
        <el-button type="primary">
          <el-icon><Upload /></el-icon>
          上传文档
        </el-button>
      </el-upload>
    </div>

    <div v-if="kb?.description" class="kb-desc-bar">
      {{ kb.description }}
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索知识库内容..."
        clearable
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch" :loading="searching">搜索</el-button>
        </template>
      </el-input>
    </div>

    <div v-if="searchResults.length > 0" class="search-results">
      <div class="results-header">
        <span>搜索结果（{{ searchResults.length }} 条）</span>
        <el-button size="small" text @click="clearSearch">清除搜索</el-button>
      </div>
      <div class="results-list">
        <div v-for="(hit, i) in searchResults" :key="i" class="result-card">
          <div class="result-head">
            <span class="result-idx">[{{ i + 1 }}]</span>
            <span class="result-file">{{ hit.filename || '文档' }}</span>
            <el-tag size="small" effect="plain">第 {{ hit.chunkIndex ?? 0 }} 段</el-tag>
            <span class="result-score">相关度 {{ hit.score?.toFixed(2) }}</span>
          </div>
          <div class="result-body">{{ hit.content }}</div>
        </div>
      </div>
    </div>

    <el-card v-if="searchResults.length === 0" shadow="never" class="table-card">
      <el-table :data="documents" v-loading="loading" style="width: 100%">
        <el-table-column prop="filename" label="文件名" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ row.filename }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType?.toUpperCase() || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="light">{{ row.chunkCount ?? 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无文档，点击右上角上传" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getKnowledgeBases,
  getDocuments,
  uploadDocument,
  deleteDocument,
  searchKnowledge,
  type KnowledgeBase,
  type KnowledgeDocument
} from '@/api/knowledge'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)

const kb = ref<KnowledgeBase | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)

const searchQuery = ref('')
const searching = ref(false)
const searchResults = ref<any[]>([])

onMounted(async () => {
  await Promise.all([loadKb(), loadDocuments()])
})

async function loadKb() {
  try {
    const res = await getKnowledgeBases()
    const bases = (res.data as KnowledgeBase[]) || []
    kb.value = bases.find((b) => Number(b.id) === kbId) || null
  } catch (e) {
    console.error('加载知识库信息失败:', e)
  }
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getDocuments(kbId)
    documents.value = (res.data as KnowledgeDocument[]) || []
  } catch (e) {
    console.error('加载文档列表失败:', e)
  } finally {
    loading.value = false
  }
}

function formatSize(bytes: number) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  return `${(kb / 1024).toFixed(2)} MB`
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'ready') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'processing') return 'warning'
  return 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    ready: '就绪',
    failed: '失败',
    processing: '处理中'
  }
  return map[status] || status || '-'
}

async function handleUpload(file: File) {
  ElMessage.info('文档上传中，正在解析...')
  try {
    await uploadDocument(kbId, file)
    ElMessage.success('上传成功，后台正在解析分块')
    setTimeout(() => {
      loadDocuments()
    }, 2000)
  } catch (e) {
    console.error('上传失败:', e)
  }
  // 返回 false 阻止 el-upload 的默认 xhr 上传流程
  return false
}

async function handleDelete(row: KnowledgeDocument) {
  try {
    await deleteDocument(row.id)
    documents.value = documents.value.filter((d) => d.id !== row.id)
    ElMessage.success('删除成功')
  } catch (e) {
    console.error('删除文档失败:', e)
  }
}

async function handleSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  try {
    const res = await searchKnowledge(kbId, searchQuery.value.trim())
    const data = (res as any).data
    searchResults.value = data?.hits || data || []
  } catch (e) {
    console.error('搜索失败:', e)
    ElMessage.error('搜索失败')
  } finally {
    searching.value = false
  }
}

function clearSearch() {
  searchQuery.value = ''
  searchResults.value = []
}
</script>

<style scoped>
.kb-detail {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kb-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.kb-desc-bar {
  background: #f5f7fa;
  border-left: 3px solid #409eff;
  padding: 10px 14px;
  color: #606266;
  font-size: 13px;
  border-radius: 4px;
  margin-bottom: 16px;
}
.table-card {
  border-radius: 8px;
}
.file-icon {
  color: #409eff;
  margin-right: 6px;
  vertical-align: middle;
}
.search-bar {
  margin-bottom: 16px;
}
.search-results {
  margin-bottom: 16px;
}
.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}
.results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafbfc;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}
.result-idx {
  color: #409eff;
  font-weight: 700;
}
.result-file {
  color: #606266;
  flex: 1;
}
.result-score {
  color: #909399;
  font-size: 11px;
}
.result-body {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
}
</style>
