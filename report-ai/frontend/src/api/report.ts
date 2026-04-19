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
  versionNum: number
  title?: string
  content: string
  sourceMode?: string
  wordCount?: number
  changeSummary?: string
  createdBy?: number
  createdAt?: string
}

export interface CreateReportParams {
  title: string
  topic?: string
  keyPoints?: string
  kbId?: number | null
  templateId?: number | null
  /** 赛题 2.3：手动补充的检索关键词，多个词用空格/逗号分隔 */
  includeKeywords?: string
  /** 赛题 2.3：需要排除的关键词 */
  excludeKeywords?: string
}

export interface UpdateReportParams {
  title?: string
  content?: string
}

export type RewriteMode =
  | 'DATA_UPDATE'
  | 'ANGLE_SHIFT'
  | 'EXPAND'
  | 'STYLE_SHIFT'
  | 'CONTINUATION'

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

export const restoreVersion = (reportId: number, versionNum: number) =>
  request.post(`/v1/reports/${reportId}/versions/${versionNum}/restore`)

export const getVersionDiffByNum = (reportId: number, fromVersion: number, toVersion: number) =>
  request.get(`/v1/reports/${reportId}/versions/${fromVersion}/diff/${toVersion}`)

// Export
// 与 ExportController 的 @RequestMapping("/api/v1/reports/{id}/export") 对齐；
// 之前前端用的 `/v1/export/report/{id}/docx` 不存在对应路由，会直接 404。
export const exportDocx = (reportId: number) =>
  request.get(`/v1/reports/${reportId}/export/docx`, { responseType: 'blob' })

// Quality check — 赛题 3.4 三维度（覆盖度 / 引用准确性 / 事实性）
export interface QualityCitationIssue {
  citedIndex: number
  sentence: string
  reason: string
}
export interface QualityFactualityIssue {
  sentence: string
  reason: string
  suggestion: 'mark' | 'fix' | 'soften' | string
}
export interface QualityReport {
  overallScore?: number | null
  summary?: string
  coverageScore?: number | null
  missingKeyPoints?: string[]
  citationAccuracyScore?: number | null
  citationIssues?: QualityCitationIssue[]
  factualityScore?: number | null
  factualityIssues?: QualityFactualityIssue[]
}
export const checkReportQuality = (reportId: number) =>
  request.get<QualityReport>(`/v1/reports/${reportId}/quality/check`)

// Dashboard stats
export interface DashboardStats {
  totalReports: number
  totalKnowledgeBases: number
  totalUsers: number
  todayGenerated: number
}

export const getDashboardStats = () =>
  request.get('/v1/dashboard/stats')

// MCP 舆情数据
export const mcpSearchArticles = (keyword: string, page = 1, pageSize = 10) =>
  request.get('/v1/mcp/search/articles', { params: { keyword, page, pageSize } })

export const mcpGetArticleDetail = (articleId: string) =>
  request.get(`/v1/mcp/search/article/${articleId}`)

export const mcpOverview = (topic: string, startDate?: string, endDate?: string) =>
  request.get('/v1/mcp/analysis/overview', { params: { topic, startDate, endDate } })

export const mcpHotArticle = (topic: string, startDate?: string, endDate?: string, topN = 10) =>
  request.get('/v1/mcp/analysis/hot-article', { params: { topic, startDate, endDate, topN } })

export const mcpEmotionalDistribution = (topic: string, startDate?: string, endDate?: string) =>
  request.get('/v1/mcp/analysis/emotional-distribution', { params: { topic, startDate, endDate } })

export const mcpDatasourceSound = (topic: string, startDate?: string, endDate?: string) =>
  request.get('/v1/mcp/analysis/datasource-sound', { params: { topic, startDate, endDate } })

export const mcpHotWords = (topic: string, startDate?: string, endDate?: string) =>
  request.get('/v1/mcp/analysis/hot-words', { params: { topic, startDate, endDate } })

// Tavily Web 搜索
export const tavilySearch = (query: string, maxResults = 5) =>
  request.get('/v1/mcp/web/search', { params: { query, maxResults } })

export const tavilyExtract = (url: string) =>
  request.post('/v1/mcp/web/extract', { url })

// Fetch URL 抓取
export const fetchUrl = (url: string) =>
  request.get('/v1/mcp/fetch', { params: { url } })
