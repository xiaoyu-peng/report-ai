import request from '@/utils/request'

export interface Report {
  id: number
  title: string
  content?: string
  topic?: string
  keyPoints?: string
  kbId?: number | null
  templateId?: number | null
  status?: string
  wordCount?: number
  version?: number
  createdAt?: string
  updatedAt?: string
}

export interface Template {
  id: number
  name: string
  description?: string
  category?: string
  structure?: string
  style?: string
  createdAt?: string
}

export interface ReportVersion {
  id: number
  reportId: number
  version: number
  content: string
  mode?: string
  createdAt?: string
}

export interface CreateReportParams {
  title: string
  topic?: string
  keyPoints?: string
  kbId?: number | null
  templateId?: number | null
}

export interface UpdateReportParams {
  title?: string
  content?: string
}

export type RewriteMode = 'POLISH' | 'EXPAND' | 'CONDENSE' | 'CUSTOM'

export interface RewriteParams {
  mode: RewriteMode
  targetParagraph?: string
  instruction?: string
}

// Reports CRUD
export const getReports = () =>
  request.get('/v1/reports')

export const getReport = (id: number) =>
  request.get(`/v1/reports/${id}`)

export const createReport = (data: CreateReportParams) =>
  request.post('/v1/reports', data)

export const updateReport = (id: number, data: UpdateReportParams) =>
  request.put(`/v1/reports/${id}`, data)

export const deleteReport = (id: number) =>
  request.delete(`/v1/reports/${id}`)

// Templates CRUD
export const getTemplates = () =>
  request.get('/v1/templates')

export const createTemplate = (data: Partial<Template>) =>
  request.post('/v1/templates', data)

export const deleteTemplate = (id: number) =>
  request.delete(`/v1/templates/${id}`)

// Versions & diff
export const getReportVersions = (reportId: number) =>
  request.get(`/v1/reports/${reportId}/versions`)

export const getVersionDiff = (v1: number, v2: number) =>
  request.get(`/v1/reports/versions/diff`, { params: { from: v1, to: v2 } })

// Dashboard stats
export interface DashboardStats {
  totalReports: number
  totalKnowledgeBases: number
  totalUsers: number
  todayGenerated: number
}

export const getDashboardStats = () =>
  request.get('/v1/dashboard/stats')
