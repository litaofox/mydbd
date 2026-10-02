import http from './http'

export interface Overview {
  activeVehicles: number
  todayRisks: number
  pendingRisks: number
  todayWarnings: number
}

export interface RiskEvent {
  id: number
  eventCode: string
  eventSource: string
  plateNo: string
  identityCode: string
  eventTime: string
  lng: number
  lat: number
  speed: number
  riskLevel: number
  confidence: number
  mediaUrl: string | null
  handleStatus: number
  handleRemark: string | null
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface VideoAnalysis {
  id: number
  taskId: string
  plateNo: string
  channel: string
  clipUrl: string | null
  status: string
  resultSummary: string | null
  eventCount: number
  createDate: string
}

export function getOverview(): Promise<Overview> {
  return http.get('/api/monitor/overview')
}

export function getRisks(params: {
  page?: number
  size?: number
  eventSource?: string
  riskLevel?: number
  handleStatus?: number
}): Promise<PageResult<RiskEvent>> {
  return http.get('/api/monitor/risks', { params })
}

export function handleRisk(id: number, remark: string): Promise<void> {
  return http.post(`/api/monitor/risks/${id}/handle`, { remark })
}

export function getRiskTypeStats(): Promise<{ name: string; value: number }[]> {
  return http.get('/api/monitor/risks/type-stats')
}

export function getVideoAnalyses(): Promise<VideoAnalysis[]> {
  return http.get('/api/monitor/video-analyses')
}
