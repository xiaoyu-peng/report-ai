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
        multiple
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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openViewer(row, 'view')">查看</el-button>
            <el-button link type="primary" @click="openViewer(row, 'edit')">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无文档，点击右上角上传" />
        </template>
      </el-table>
    </el-card>

    <!-- 文档查看 / 编辑对话框：同一个 Dialog 按 viewerMode 切换 -->
    <el-dialog
      v-model="viewerVisible"
      :title="(viewerMode === 'edit' ? '编辑文档：' : '查看文档：') + (viewerDoc?.filename || '')"
      width="900px"
      top="5vh"
      :close-on-click-modal="false"
    >
      <div v-loading="viewerLoading">
        <el-form label-width="70px" class="viewer-form">
          <el-form-item label="文件名">
            <el-input v-model="viewerFilename" :disabled="viewerMode === 'view'" />
          </el-form-item>
          <el-form-item label="正文">
            <el-input
              v-model="viewerContent"
              type="textarea"
              :rows="22"
              :readonly="viewerMode === 'view'"
              resize="vertical"
              placeholder="（文档正文）"
            />
          </el-form-item>
          <div class="viewer-meta" v-if="viewerDoc">
            <el-tag size="small" effect="plain">分块 {{ viewerDoc.chunkCount }}</el-tag>
            <el-tag size="small" effect="plain">{{ formatSize(viewerDoc.fileSize) }}</el-tag>
            <span class="viewer-hint" v-if="viewerMode === 'edit'">
              保存时若正文变更将重新分块，检索结果会随之更新。
            </span>
          </div>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="viewerVisible = false">关闭</el-button>
        <el-button
          v-if="viewerMode === 'edit'"
          type="primary"
          :loading="saving"
          @click="saveViewer"
        >保存</el-button>
      </template>
    </el-dialog>
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
  getDocument,
  updateDocument,
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

// 查看 / 编辑 Dialog 状态：共用一个 Dialog，用 viewerMode 区分只读还是可编辑
const viewerVisible = ref(false)
const viewerLoading = ref(false)
const viewerMode = ref<'view' | 'edit'>('view')
const viewerDoc = ref<KnowledgeDocument | null>(null)
const viewerFilename = ref('')
const viewerContent = ref('')
const saving = ref(false)

async function openViewer(row: KnowledgeDocument, mode: 'view' | 'edit') {
  viewerMode.value = mode
  viewerDoc.value = row
  viewerFilename.value = row.filename
  viewerContent.value = ''
  viewerVisible.value = true
  viewerLoading.value = true
  try {
    const res = await getDocument(row.id)
    const full = (res as any).data as KnowledgeDocument
    if (full) {
      viewerDoc.value = full
      viewerFilename.value = full.filename
      viewerContent.value = full.content ?? ''
    }
  } catch (e) {
    console.error('加载文档失败:', e)
    ElMessage.error('加载文档失败')
  } finally {
    viewerLoading.value = false
  }
}

async function saveViewer() {
  if (!viewerDoc.value) return
  if (!viewerFilename.value.trim()) {
    ElMessage.warning('文件名不能为空')
    return
  }
  saving.value = true
  try {
    const payload: { filename?: string; content?: string } = {
      filename: viewerFilename.value.trim()
    }
    // 正文和原始不一致才一并提交，避免大 content 白白往返
    if (viewerContent.value !== (viewerDoc.value.content ?? '')) {
      payload.content = viewerContent.value
    }
    await updateDocument(viewerDoc.value.id, payload)
    ElMessage.success('保存成功')
    viewerVisible.value = false
    await loadDocuments()
  } catch (e) {
    console.error('保存文档失败:', e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // route.params.id 非数字（直链 /knowledge/undefined、回退到脏 URL 等）会让后续请求带上 NaN
  if (!Number.isFinite(kbId) || kbId <= 0) {
    ElMessage.error('知识库 ID 无效，已返回列表')
    router.replace('/knowledge/list')
    return
  }
  await Promise.all([loadKb(), loadDocuments()])
})

async function loadKb() {
  try {
    const res = await getKnowledgeBases()
    const raw = (res as any).data
    const bases: KnowledgeBase[] = Array.isArray(raw) ? raw : Array.isArray(raw?.records) ? raw.records : []
    kb.value = bases.find((b) => Number(b.id) === kbId) || null
  } catch (e) {
    console.error('加载知识库信息失败:', e)
  }
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getDocuments(kbId)
    const raw = (res as any).data
    documents.value = Array.isArray(raw) ? raw : Array.isArray(raw?.records) ? raw.records : []
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
  color: #0f172a;
}
.kb-desc-bar {
  background: #f5f3ff;
  border-left: 3px solid #6366f1;
  padding: 10px 14px;
  color: #475569;
  font-size: 13px;
  border-radius: 4px;
  margin-bottom: 16px;
}
.table-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}
.file-icon {
  color: #6366f1;
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
  color: #475569;
}
.results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}
.result-idx {
  color: #6366f1;
  font-weight: 700;
}
.result-file {
  color: #475569;
  flex: 1;
}
.result-score {
  color: #94a3b8;
  font-size: 11px;
}
.result-body {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}
.viewer-form :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.7;
}
.viewer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 70px;
  color: #64748b;
  font-size: 12px;
}
.viewer-hint {
  color: #94a3b8;
}
</style>
