import request from '@/utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description?: string
  category?: string
  docCount: number
  chunkCount: number
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeDocument {
  id: number
  kbId: number
  filename: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: 'ready' | 'failed' | 'processing' | 'success'
  content?: string
  createdAt: string
}

// 支持按 category 筛选（policy / industry / history / media / other），以及关键词 + 分页
export const getKnowledgeBases = (params?: { category?: string; keyword?: string; current?: number; size?: number }) =>
  request.get('/v1/knowledge/bases', { params })

export const createKnowledgeBase = (data: { name: string; description: string; category?: string }) =>
  request.post('/v1/knowledge/bases', data)

export const deleteKnowledgeBase = (id: number) =>
  request.delete(`/v1/knowledge/bases/${id}`)

export const getDocuments = (kbId: number) =>
  request.get(`/v1/knowledge/bases/${kbId}/documents`)

export const uploadDocument = (kbId: number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/v1/knowledge/bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deleteDocument = (docId: number) =>
  request.delete(`/v1/knowledge/documents/${docId}`)

// 文档详情含全文 content，用于查看/编辑
export const getDocument = (docId: number) =>
  request.get(`/v1/knowledge/documents/${docId}`)

// rename 或替换正文；content 传 null/undefined 时只 rename
export const updateDocument = (docId: number, data: { filename?: string; content?: string }) =>
  request.put(`/v1/knowledge/documents/${docId}`, data)

export const searchKnowledge = (kbId: number, query: string, topK = 8) =>
  // 后端路由：GET /api/v1/knowledge/search?kbId=&q=&topK=（query 参数是 q 不是 query）
  request.get(`/v1/knowledge/search`, { params: { kbId, q: query, topK } })
