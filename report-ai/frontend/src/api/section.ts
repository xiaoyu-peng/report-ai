import { request } from '@/utils/request'

export interface ReportSection {
  id: number
  reportId: number
  sectionIndex: number
  title?: string
  prompt?: string
  status: 'pending' | 'generating' | 'done' | 'failed'
  content?: string
  wordCount?: number
  citationCount?: number
  startedAt?: string
  finishedAt?: string
}

export interface OutlineItem {
  title: string
  prompt?: string
  /** 导入原报告章节时携带；后端以 status=done 入库，不再重新生成。 */
  content?: string
  status?: 'pending' | 'done'
}

export const initSections = (reportId: number, outline: OutlineItem[]) =>
  request.post<ReportSection[]>(`/v1/reports/${reportId}/sections/init`, outline)

export const listSections = (reportId: number) =>
  request.get<ReportSection[]>(`/v1/reports/${reportId}/sections`)

export const assembleSections = (reportId: number) =>
  request.post<string>(`/v1/reports/${reportId}/sections/assemble`)

/**
 * 章节流式 EventSource。注意：直接走原生 EventSource，路径要带 /api 前缀
 * （Vite 代理已配置 /api → 后端 8081）。
 * EventSource 不支持 Authorization header，通过 URL 参数传递 token。
 */
export const streamSection = (reportId: number, idx: number, kbIds: number[] = []): EventSource => {
  const token = localStorage.getItem('token') || ''
  const kbParam = kbIds.length ? `&kbIds=${kbIds.join(',')}` : ''
  return new EventSource(`/api/v1/reports/${reportId}/sections/${idx}/stream?token=${encodeURIComponent(token)}${kbParam}`)
}
