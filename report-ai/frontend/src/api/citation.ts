import { request } from '@/utils/request'

export interface ReportCitation {
  id: number
  reportId: number
  versionId?: number | null
  sectionIndex: number
  paragraphIndex: number
  citationMarker: number
  chunkId: number
  docId: number
  docTitle?: string
  pageStart?: number | null
  pageEnd?: number | null
  snippet?: string
  accepted: boolean
  createdAt?: string
}

/** 列出某报告的引用（accepted=true，按 marker 升序）。 */
export const listCitations = (reportId: number) =>
  request.get<ReportCitation[]>(`/v1/reports/${reportId}/citations`)

/** 用户在 popover 里点「排除此引用」。 */
export const excludeCitation = (reportId: number, marker: number) =>
  request.post(`/v1/reports/${reportId}/citations/${marker}/exclude`)

/** 撤销排除。 */
export const restoreCitation = (reportId: number, marker: number) =>
  request.post(`/v1/reports/${reportId}/citations/${marker}/restore`)
