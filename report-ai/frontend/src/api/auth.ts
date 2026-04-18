import { request, ApiResponse } from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  username: string
  userId: string
  tenantId: string
  roles: string[]
  permissions: string[]
}

export interface UserInfo {
  id: string
  username: string
  tenantId: string
  deptId?: string
  avatar: string | null
}

export function login(data: LoginParams): Promise<ApiResponse<LoginResult>> {
  return request.post('/v1/login', data)
}

export function getUserInfo(): Promise<ApiResponse<UserInfo>> {
  return request.get('/v1/userinfo')
}

export function logout(): Promise<ApiResponse<void>> {
  return request.post('/v1/logout')
}
