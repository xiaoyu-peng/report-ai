import { request, ApiResponse } from '@/utils/request'

export interface PermissionApplyParams {
  skillId: string | number
  userId: string | number
  accessLevel: string
  reason?: string
}

export interface SkillAccess {
  id: string | number
  skillId: string | number
  userId: string | number
  deptId?: string | number
  accessLevel: string
  status: string
  appliedAt: string
  approvedBy?: string | number
  approvedAt?: string
  rejectedReason?: string
  expiresAt?: string
}

export function applyPermission(data: PermissionApplyParams): Promise<ApiResponse<SkillAccess>> {
  return request.post('/v1/permissions/apply', data)
}

export function getMyApplications(userId: string | number): Promise<ApiResponse<SkillAccess[]>> {
  return request.get('/v1/permissions/my-applications', { params: { userId } })
}

export function getPendingApprovals(deptId?: string | number): Promise<ApiResponse<SkillAccess[]>> {
  return request.get('/v1/permissions/pending', { params: { deptId } })
}

export function approvePermission(id: string | number, approvedBy: string | number): Promise<ApiResponse<void>> {
  return request.post(`/v1/permissions/${id}/approve`, null, { params: { approvedBy } })
}

export function rejectPermission(id: string | number, approvedBy: string | number, reason: string): Promise<ApiResponse<void>> {
  return request.post(`/v1/permissions/${id}/reject`, null, { params: { approvedBy, reason } })
}
