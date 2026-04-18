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
  status: 'ready' | 'failed' | 'processing'
  createdAt: string
}

export const getKnowledgeBases = () =>
  request.get('/v1/knowledge/bases')

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

export const searchKnowledge = (kbId: number, query: string, topK = 8) =>
  request.get(`/v1/knowledge/bases/${kbId}/search`, { params: { query, topK } })
