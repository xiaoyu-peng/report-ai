import { request, ApiResponse, PageResult } from '@/utils/request'

export interface InvocationLog {
  id: string
  tenantId: string
  traceId: string
  spanId: string
  parentSpanId: string
  userId: string
  username?: string
  agentId: string
  agentName: string
  skillId: string
  skillName: string
  skillVersion: string
  callType: string
  inputParams: string
  outputResult: string
  permissionPassed: boolean
  permissionLevel: string
  permissionCheckMs: number
  executionStartAt: string
  executionEndAt: string
  executionDurationMs: number
  duration?: number
  executionStatus: string
  retryCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  tokensUsed?: number
  mcpProtocolVersion: string
  mcpTransport: string
  mcpEndpoint: string
  errorCode: string
  errorMessage: string
  createdAt: string
}

export interface OperationLog {
  id: string
  userId: string
  username: string
  action: string
  actionName: string
  resourceType: string
  resourceId: string
  resourceName: string
  details: string
  beforeData?: string
  afterData?: string
  ipAddress: string
  userAgent: string
  sessionId?: string
  status: string
  errorMessage?: string
  durationMs: number
  createdAt: string
}

export interface OperationLogQueryParams {
  current?: number
  size?: number
  username?: string
  action?: string
  status?: string
}

export interface InvocationLogQueryParams {
  current?: number
  size?: number
  skillName?: string
  status?: string
}

export function getInvocationLogs(params: InvocationLogQueryParams): Promise<ApiResponse<PageResult<InvocationLog>>> {
  return request.get('/v1/logs/invocations', { params })
}

export function getOperationLogs(params: OperationLogQueryParams): Promise<ApiResponse<PageResult<OperationLog>>> {
  return request.get('/v1/logs/operations', { params })
}
