import { request } from '@/utils/request'

export interface ReportQuality {
  reportId: number
  coverageRate?: number | null
  citationsTotal?: number
  paragraphsTotal?: number
  paragraphsCited?: number
  /** JSON string {"行业报告":12,"政策法规":4} */
  kbDistribution?: string | Record<string, number>
  /** JSON string [{text,reason,severity,suggestion}] */
  suspiciousFacts?: string | any[]
  checkedAt?: string
}

export const getQuality = (reportId: number) =>
  request.get<ReportQuality>(`/v1/reports/${reportId}/quality`)

export const recheckQuality = (reportId: number) =>
  request.post<ReportQuality>(`/v1/reports/${reportId}/quality/recheck`)
