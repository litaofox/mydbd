import http from './http'
import type { PageResult } from './monitor'

// ===== 风控规则 =====

export interface RiskRule {
  id: number
  ruleCode: string
  ruleName: string
  ruleType: 'SPEED' | 'FATIGUE' | 'SIGNAL' | 'COMBO' | string
  eventCode: string
  riskLevel: number
  params: Record<string, number> | null
  cooldownSec: number
  status: number
  builtIn: number
  validMark?: number
  remark?: string | null
  createDate?: string
}

export interface RuleSaveBody {
  ruleCode: string
  ruleName: string
  ruleType: string
  riskLevel: number
  params: Record<string, number>
  cooldownSec: number
  status: number
  remark?: string | null
}

export interface EngineSummary {
  ruleTotal: number
  ruleEnabled: number
  fenceTotal: number
  fenceEnabled: number
  todayEventTotal: number
  todayByLevel: { level: number | string; cnt: number }[]
  todayTopRules: { rule_id: number | string; title: string; cnt: number }[]
}

export function pageRules(params: {
  page?: number
  size?: number
  ruleCode?: string
  ruleName?: string
  ruleType?: string
  status?: number
}): Promise<PageResult<RiskRule>> {
  return http.get('/api/risk/rules', { params })
}

export function listAllRules(): Promise<RiskRule[]> {
  return http.get('/api/risk/rules/all')
}

export function getRule(id: number): Promise<RiskRule> {
  return http.get(`/api/risk/rules/${id}`)
}

export function createRule(body: RuleSaveBody): Promise<string> {
  return http.post('/api/risk/rules', body)
}

export function updateRule(id: number, body: RuleSaveBody): Promise<void> {
  return http.put(`/api/risk/rules/${id}`, body)
}

export function changeRuleStatus(id: number, status: number): Promise<void> {
  return http.put(`/api/risk/rules/${id}/status`, { status })
}

export function deleteRule(id: number): Promise<void> {
  return http.delete(`/api/risk/rules/${id}`)
}

export function getEngineSummary(): Promise<EngineSummary> {
  return http.get('/api/risk/engine/summary')
}

// ===== 电子围栏 =====

/** 顶点 [lng, lat] */
export type LngLat = [number, number]

export interface GeoFence {
  id: number
  fenceName: string
  fenceType: 'CIRCLE' | 'POLYGON' | string
  centerLng: number | null
  centerLat: number | null
  radiusM: number | null
  points?: LngLat[] | null
  triggerDir: number
  riskLevel: number
  cooldownSec: number
  status: number
  remark?: string | null
  createDate?: string
}

export interface FenceSaveBody {
  fenceName: string
  fenceType: string
  centerLng?: number | null
  centerLat?: number | null
  radiusM?: number | null
  points?: LngLat[] | null
  triggerDir: number
  riskLevel: number
  cooldownSec: number
  status: number
  remark?: string | null
}

export function pageFences(params: {
  page?: number
  size?: number
  fenceName?: string
  fenceType?: string
  status?: number
}): Promise<PageResult<GeoFence>> {
  return http.get('/api/risk/fences', { params })
}

export function listAllFences(): Promise<GeoFence[]> {
  return http.get('/api/risk/fences/all')
}

export function getFence(id: number): Promise<GeoFence> {
  return http.get(`/api/risk/fences/${id}`)
}

export function createFence(body: FenceSaveBody): Promise<string> {
  return http.post('/api/risk/fences', body)
}

export function updateFence(id: number, body: FenceSaveBody): Promise<void> {
  return http.put(`/api/risk/fences/${id}`, body)
}

export function changeFenceStatus(id: number, status: number): Promise<void> {
  return http.put(`/api/risk/fences/${id}/status`, { status })
}

export function deleteFence(id: number): Promise<void> {
  return http.delete(`/api/risk/fences/${id}`)
}

// ===== F20 处置工单 =====

export type OrderStatus = 'PENDING' | 'PROCESSING' | 'CLOSED'
export type CloseResult = 'PHONE_REMIND' | 'EDUCATION' | 'REPORT_PENALTY' | 'TRAFFIC_VIOLATION' | 'FALSE_ALARM'

export interface RiskWorkOrder {
  id: number
  orderNo: string
  eventId: number
  eventTitle: string | null
  eventCode: string | null
  eventSource: string | null
  plateNo: string | null
  identityCode: string | null
  riskLevel: number
  eventTime: string | null
  status: OrderStatus
  assigneeId: string | null
  assigneeName: string | null
  assignTime: string | null
  claimTime: string | null
  closeTime: string | null
  closeResult: CloseResult | null
  closeRemark: string | null
  deadline: string | null
  slaLimitMin: number
  graceMin: number
  overdue: number
  escalated: number
  escalateTime: string | null
  reopenCount: number
  createDate: string
}

export interface RiskOrderLog {
  id: number
  orderId: number
  action: 'CREATE' | 'ASSIGN' | 'CLAIM' | 'TRANSFER' | 'CLOSE' | 'REOPEN' | 'ESCALATE'
  fromStatus: string | null
  toStatus: string | null
  fromUserId: string | null
  toUserId: string | null
  fromUserName: string | null
  toUserName: string | null
  remark: string | null
  operatorId: string | null
  operatorName: string | null
  createDate: string
}

export interface OrderDetail {
  order: RiskWorkOrder
  event: {
    id: number
    eventCode: string
    eventSource: string
    ruleId: number | null
    fenceId: number | null
    title: string | null
    plateNo: string | null
    identityCode: string | null
    eventTime: string | null
    lng: number | null
    lat: number | null
    speed: number | null
    riskLevel: number
    confidence: number | null
    mediaUrl: string | null
    handleStatus: number
    handleRemark: string | null
  } | null
  logs: RiskOrderLog[]
}

export interface OrderStats {
  pendingCount: number
  processingCount: number
  overdueCount: number
  escalatedCount: number
  closedTodayCount: number
  avgCloseSeconds: number
  avgCloseMinutes: number
}

export interface AssignableUser {
  id: string
  name: string
  username: string
}

export interface OrderSla {
  riskLevel: number
  limitMin: number
  graceMin: number
}

export function pageOrders(params: {
  page?: number
  size?: number
  keyword?: string
  status?: OrderStatus
  riskLevel?: number
  closeResult?: CloseResult
  timeFlag?: 'due' | 'overdue' | 'escalated'
  assigneeId?: string
  beginTime?: string
  endTime?: string
}): Promise<PageResult<RiskWorkOrder>> {
  return http.get('/api/risk/orders', { params })
}

export function getOrder(id: number): Promise<OrderDetail> {
  return http.get(`/api/risk/orders/${id}`)
}

export function getOrderStats(): Promise<OrderStats> {
  return http.get('/api/risk/orders/stats')
}

export function listAssignableUsers(): Promise<AssignableUser[]> {
  return http.get('/api/risk/orders/assignable-users')
}

export function ensureOrder(eventId: number): Promise<RiskWorkOrder> {
  return http.post(`/api/risk/orders/ensure/${eventId}`)
}

export function claimOrder(id: number): Promise<void> {
  return http.post(`/api/risk/orders/${id}/claim`)
}

export function assignOrder(id: number, body: { userId: string; remark?: string }): Promise<void> {
  return http.post(`/api/risk/orders/${id}/assign`, body)
}

export function transferOrder(id: number, body: { userId: string; remark: string }): Promise<void> {
  return http.post(`/api/risk/orders/${id}/transfer`, body)
}

export function closeOrder(id: number, body: { result: CloseResult; remark: string }): Promise<void> {
  return http.post(`/api/risk/orders/${id}/close`, body)
}

export function reopenOrder(id: number, body: { remark: string }): Promise<void> {
  return http.post(`/api/risk/orders/${id}/reopen`, body)
}

export function getOrderSla(): Promise<OrderSla[]> {
  return http.get('/api/risk/orders/sla')
}

export function updateOrderSla(items: OrderSla[]): Promise<void> {
  return http.put('/api/risk/orders/sla', { items })
}

// ===== F21 坐席干预记录 =====

export type InterventionAction = 'PHONE_REMIND' | 'EDUCATION' | 'STOP' | 'PENALTY' | 'OTHER'
export type InterventionResult = 'SUCCESS' | 'FAILED' | 'NO_ANSWER' | 'PENDING'

export interface RiskIntervention {
  id: number
  orderId: number
  eventId: number | null
  plateNo: string | null
  identityCode: string | null
  actionType: InterventionAction
  actionResult: InterventionResult
  operatorId: number | null
  operatorName: string | null
  source: 'MANUAL' | 'SYSTEM'
  remark: string | null
  createDate: string
}

export interface OrderFunnel {
  eventTotal: number
  orderTotal: number
  interventionTotal: number
  closedTotal: number
  interventionRate: number
  closeRate: number
}

export function getInterventions(orderId: number): Promise<RiskIntervention[]> {
  return http.get(`/api/risk/orders/${orderId}/interventions`)
}

export function addIntervention(body: {
  orderId: number
  actionType: InterventionAction
  actionResult: InterventionResult
  remark?: string
}): Promise<void> {
  return http.post('/api/risk/orders/interventions', body)
}

export function getOrderFunnel(start: string, end: string): Promise<OrderFunnel> {
  return http.get('/api/risk/orders/funnel', { params: { start, end } })
}
