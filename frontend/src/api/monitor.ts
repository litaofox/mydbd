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
  ruleId: number | null
  fenceId: number | null
  title: string | null
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
  ruleId?: number
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

// ===== F16 车辆详情聚合面板（MOD-MON-003 §3.2；id 类字段全部 string） =====

export interface PanelVehicle {
  id: string
  deptId: string | null
  deptName: string | null
  vehicleNo: string
  vehiclePlateColor: string | null
  vehicleType: string | null
  vehicleBrand: string | null
  vin: string | null
  operationType: number | null
  ownerName: string | null
  ownerPhone: string | null
  roadLicenseNo: string | null
  remark: string | null
}

export interface PanelTerminal {
  id: string
  identityCode: string
  tlModel: string | null
  simAccount: string | null
  protocolType: string | null
  equipmentType: string | null
  videoChannel: number | null
  status: number
  bindTime: string | null
}

export interface PanelDriver {
  id: string
  driverName: string
  sex: number | null
  contactPhone: string | null
  licenceCategory: string | null
  driverType: number
  status: number
  bindTime: string | null
}

export interface PanelLatestPoint {
  identityCode: string
  plateNo: string | null
  lng: number
  lat: number
  speed: number | null
  direction: number | null
  gpsTime: string
  alarmFlag: number | null
  online: boolean
}

export interface PanelTrackPoint {
  gpsTime: string
  lng: number
  lat: number
  speed: number | null
}

export interface PanelTodayTrack {
  date: string
  totalPoints: number
  sampled: boolean
  maxPoints: number
  points: PanelTrackPoint[]
}

export interface PanelAlarm {
  id: string
  typeId: number | null
  typeName: string | null
  startWarnTime: string | null
  handleStatus: number
  startLng: string | null
  startLat: string | null
}

export interface VehiclePanel {
  vehicle: PanelVehicle
  terminal: PanelTerminal | null
  drivers: PanelDriver[]
  latestPoint: PanelLatestPoint | null
  todayTrack: PanelTodayTrack
  latestAlarms: PanelAlarm[]
  todayAlarmCount: number
  todayRiskCount: number
}

export function getVehiclePanel(vehicleId: string): Promise<VehiclePanel> {
  return http.get(`/api/monitor/vehicle-panel/${vehicleId}`)
}

// ===== F15 监控总览大屏（MOD-MON-002 §4.1；deptId string，其余数值直出） =====

export interface DashboardOnline {
  vehicleTotal: number
  onlineCount: number
  onlineRate: number | null
  windowMinutes: number
}

export interface DashboardWorkOrder {
  pending: number
  processing: number
  overdue: number
  closed7d: number
  created7d: number
  closeRate: number | null
}

export interface DashboardFleetStat {
  deptId: string
  deptName: string
  total: number
  online: number
}

export interface DashboardRegionStat {
  cityCode: string
  cityName: string
  vehicleCount: number
  riskCount: number
}

export interface DashboardHeatCell {
  lng: number
  lat: number
  count: number
  maxLevel: number
}

export interface DashboardSummary {
  online: DashboardOnline
  mileage: { todayMileage: number }
  workOrder: DashboardWorkOrder
  fleetStats: DashboardFleetStat[]
  regionStats: DashboardRegionStat[]
  riskHeat: DashboardHeatCell[]
  serverTime: string
}

export function getDashboardSummary(): Promise<DashboardSummary> {
  return http.get('/api/monitor/dashboard/summary')
}
