import http from './http'

export interface AlarmVO {
  id: string
  plateNo: string
  identityCode: string | null
  typeId: number
  typeName: string | null
  gradeLevel: number | null
  startWarnTime: string | null
  endWarnTime: string | null
  startLng: string | null
  startLat: string | null
  endLng: string | null
  endLat: string | null
  startSpeed: number | null
  endSpeed: number | null
  warnContinueMark: number | null
  handleStatus: number
  handleResultCode: string | null
  handleResultMsg: string | null
  handler: string | null
  updateDate: string | null
  relatedRisks?: RelatedRisk[]
}

export interface RelatedRisk {
  id: number
  eventCode: string | null
  title: string | null
  riskLevel: number | null
  eventTime: string | null
}

export interface AlarmType {
  typeId: number
  typeName: string
  gradeLevel: number
}

export interface AlarmStats {
  total: number
  byType: { typeId: number; typeName: string; gradeLevel: number; count: number }[]
  byStatus: { handleStatus: number; count: number }[]
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface AlarmPageParams {
  page?: number
  size?: number
  plateNo?: string
  typeId?: number
  handleStatus?: number
  beginTime?: string
  endTime?: string
}

export function getAlarmPage(params: AlarmPageParams): Promise<PageResult<AlarmVO>> {
  return http.get('/api/alarm/page', { params })
}

export function getAlarmLatest(limit = 10): Promise<AlarmVO[]> {
  return http.get('/api/alarm/latest', { params: { limit } })
}

export function getAlarmStats(start?: string, end?: string): Promise<AlarmStats> {
  return http.get('/api/alarm/stats', { params: { start, end } })
}

export function getAlarmDetail(id: string): Promise<AlarmVO> {
  return http.get(`/api/alarm/${id}`)
}

export function getAlarmTypes(): Promise<AlarmType[]> {
  return http.get('/api/alarm/types')
}

export function confirmAlarm(id: string): Promise<AlarmVO> {
  return http.post(`/api/alarm/${id}/confirm`)
}

export function resolveAlarm(id: string, resultCode: string, resultMsg?: string): Promise<AlarmVO> {
  return http.post(`/api/alarm/${id}/resolve`, { resultCode, resultMsg })
}
