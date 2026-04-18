import { request, ApiResponse, PageResult } from '@/utils/request'

export interface User {
  id: string | number
  username: string
  email: string
  tenantId?: string | number
  deptId?: string | number
  roleId?: string | number
  status: string
  avatar?: string
  phone?: string
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export interface UserCreateParams {
  username: string
  email: string
  password: string
  deptId?: string | number
  roleId?: string | number
  phone?: string
}

export interface UserUpdateParams {
  email?: string
  deptId?: string | number
  roleId?: string | number
  phone?: string
  status?: string
}

export interface UserQueryParams {
  keyword?: string
  deptId?: string
  status?: string
  current?: number
  size?: number
}

export function getUserList(params: UserQueryParams): Promise<ApiResponse<PageResult<User>>> {
  return request.get('/v1/users', { params })
}

export function getUserDetail(id: string | number): Promise<ApiResponse<User>> {
  return request.get(`/v1/users/${id}`)
}

export function createUser(data: UserCreateParams): Promise<ApiResponse<User>> {
  return request.post('/v1/users', data)
}

export function updateUser(id: string | number, data: UserUpdateParams): Promise<ApiResponse<User>> {
  return request.put(`/v1/users/${id}`, data)
}

export function deleteUser(id: string | number): Promise<ApiResponse<void>> {
  return request.delete(`/v1/users/${id}`)
}

export function resetUserPassword(id: string | number, newPassword: string = '123456'): Promise<ApiResponse<void>> {
  return request.put(`/v1/users/${id}/reset-password`, { newPassword })
}

export function enableUser(id: string | number): Promise<ApiResponse<void>> {
  return request.put(`/v1/users/${id}/enable`)
}

export function disableUser(id: string | number): Promise<ApiResponse<void>> {
  return request.put(`/v1/users/${id}/disable`)
}

export function lockUser(id: string | number): Promise<ApiResponse<void>> {
  return request.put(`/v1/users/${id}/lock`)
}

export function unlockUser(id: string | number): Promise<ApiResponse<void>> {
  return request.put(`/v1/users/${id}/unlock`)
}

export function updateUserStatus(id: string | number, status: string): Promise<ApiResponse<void>> {
  switch (status) {
    case 'active':
      return enableUser(id)
    case 'inactive':
    case 'disabled':
      return disableUser(id)
    case 'locked':
      return lockUser(id)
    default:
      return enableUser(id)
  }
}
