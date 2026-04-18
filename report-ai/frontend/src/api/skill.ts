import { request, ApiResponse, PageResult } from '@/utils/request'
import type { Skill } from '@/types/skill'

export interface SkillQueryParams {
  keyword?: string
  type?: string
  permissionLevel?: string
  status?: string
  current?: number
  size?: number
}

export interface SkillCreateParams {
  name: string
  slug: string
  type: string
  platformVersions?: string
  skillFormatVersion?: string
  mcpProtocolVersion?: string
  config?: string
  metadata?: string
  permissionLevel: string
  deptId?: number
  description?: string
  tags?: string
  icon?: string
  sourceType?: string
  sourceUrl?: string
  status?: string
}

export function getSkillList(params: SkillQueryParams): Promise<ApiResponse<PageResult<Skill>>> {
  return request.get('/v1/skills', { params })
}

export function getSkillDetail(id: string): Promise<ApiResponse<Skill>> {
  return request.get(`/v1/skills/${id}`)
}

export function createSkill(data: SkillCreateParams): Promise<ApiResponse<Skill>> {
  return request.post('/v1/skills', data)
}

export function updateSkill(id: string | number, data: Partial<SkillCreateParams>): Promise<ApiResponse<Skill>> {
  return request.put(`/v1/skills/${id}`, data)
}

export function deleteSkill(id: string | number): Promise<ApiResponse<void>> {
  return request.delete(`/v1/skills/${id}`)
}

export function enableSkill(id: string | number): Promise<ApiResponse<void>> {
  return request.put(`/v1/skills/${id}/enable`)
}

export function disableSkill(id: string | number): Promise<ApiResponse<void>> {
  return request.put(`/v1/skills/${id}/disable`)
}

export function getHotSkills(limit: number = 10): Promise<ApiResponse<Skill[]>> {
  return request.get('/v1/skills/hot', { params: { limit } })
}

export function searchSkills(keyword: string): Promise<ApiResponse<Skill[]>> {
  return request.get('/v1/skills/search', { params: { keyword } })
}

export function getAvailableSkills(): Promise<ApiResponse<Skill[]>> {
  return request.get('/v1/skills/available')
}
