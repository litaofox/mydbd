import http from './http'
import type { PageResult } from './mdm'

/** 审计日志列表行（id 为雪花大数，后端序列化为字符串） */
export interface AuditLog {
  id: string
  traceId?: string
  userName?: string | null
  module: string
  action: string
  actionName?: string | null
  objectType?: string | null
  objectId?: string | null
  requestMethod?: string
  requestUri?: string
  status: number
  resultCode?: number | null
  errorMsg?: string | null
  costMs?: number | null
  clientIp?: string | null
  createTime: string
  bodyPreview?: string | null
}

/** 审计日志详情（含全字段） */
export interface AuditLogDetail extends AuditLog {
  queryString?: string | null
  requestBody?: string | null
  userAgent?: string | null
  contentHash?: string | null
}

export interface NameCount {
  name: string
  count: number
}

export interface AuditStats {
  total: number
  successCount: number
  successRate: number
  loginFailCount: number
  todayCount: number
  actionDist: NameCount[]
  topUsers: NameCount[]
}

export interface AuditQuery {
  startTime?: string
  endTime?: string
  userName?: string
  module?: string
  action?: string
  status?: number | null
  keyword?: string
  page?: number
  size?: number
}

/** 分页检索审计日志 */
export function pageAuditLogs(params: AuditQuery) {
  return http.get<unknown, PageResult<AuditLog>>('/api/audit/logs', { params })
}

/** 审计日志详情 */
export function getAuditLog(id: string | number) {
  return http.get<unknown, AuditLogDetail>(`/api/audit/logs/${id}`)
}

/** 区间概览统计 */
export function getAuditStats(params: { startTime?: string; endTime?: string }) {
  return http.get<unknown, AuditStats>('/api/audit/stats', { params })
}
