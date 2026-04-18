import { request, ApiResponse, PageResult } from '@/utils/request'

export interface Role {
  id: string | number
  name: string
  code: string
  description?: string
  isSystem: boolean
  tenantId: string | number
  createdAt: string
  updatedAt: string
}

export interface RoleQueryParams {
  keyword?: string
  current?: number
  size?: number
}

export interface RoleCreateParams {
  name: string
  code: string
  description?: string
}

export function getRoleList(params: RoleQueryParams): Promise<ApiResponse<PageResult<Role>>> {
  return request.get('/v1/roles', { params })
}

export function getRoleDetail(id: string | number): Promise<ApiResponse<Role>> {
  return request.get(`/v1/roles/${id}`)
}

export function createRole(data: RoleCreateParams): Promise<ApiResponse<Role>> {
  return request.post('/v1/roles', data)
}

export function updateRole(id: string | number, data: Partial<RoleCreateParams>): Promise<ApiResponse<Role>> {
  return request.put(`/v1/roles/${id}`, data)
}

export function deleteRole(id: string | number): Promise<ApiResponse<void>> {
  return request.delete(`/v1/roles/${id}`)
}

export function getRolesByTenant(tenantId: string | number): Promise<ApiResponse<Role[]>> {
  return request.get(`/v1/roles/tenant/${tenantId}`)
}
