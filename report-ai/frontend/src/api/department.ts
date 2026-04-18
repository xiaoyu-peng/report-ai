import { request, ApiResponse, PageResult } from '@/utils/request'

export interface Department {
  id: string
  name: string
  tenantId: string
  parentId?: string
  managerId?: string
  path?: string
  level?: number
  description?: string
  managerName?: string
  memberCount?: number
  createdAt: string
  updatedAt: string
}

export interface DepartmentCreateParams {
  name: string
  parentId?: string
  managerId?: string
  description?: string
}

export interface DepartmentUpdateParams {
  name?: string
  parentId?: string
  managerId?: string
  description?: string
}

export interface DepartmentTree extends Department {
  children?: DepartmentTree[]
}

export interface DepartmentQueryParams {
  keyword?: string
  current?: number
  size?: number
}

export function getDepartmentList(params?: DepartmentQueryParams): Promise<ApiResponse<PageResult<Department>>> {
  return request.get('/v1/departments', { params })
}

export function getDepartmentTree(): Promise<ApiResponse<DepartmentTree[]>> {
  return request.get('/v1/departments/tree')
}

export function getDepartmentDetail(id: string): Promise<ApiResponse<Department>> {
  return request.get(`/v1/departments/${id}`)
}

export function createDepartment(data: DepartmentCreateParams): Promise<ApiResponse<Department>> {
  return request.post('/v1/departments', data)
}

export function updateDepartment(id: string, data: DepartmentUpdateParams): Promise<ApiResponse<Department>> {
  return request.put(`/v1/departments/${id}`, data)
}

export function deleteDepartment(id: string): Promise<ApiResponse<void>> {
  return request.delete(`/v1/departments/${id}`)
}

export function getDepartmentUsers(deptId: string): Promise<ApiResponse<any[]>> {
  return request.get(`/v1/departments/${deptId}/users`)
}
