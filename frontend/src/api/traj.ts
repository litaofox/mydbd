import http from './http'

export interface VehicleOption {
  identityCode: string
  plateNo: string
}

export interface GpsPoint {
  id: number
  identityCode: string
  plateNo: string
  gpsTime: string
  lng: number
  lat: number
  speed: number
  direction: number
  altitude: number
  alarmFlag: number
  mileage: number
}

export function getVehicles(): Promise<VehicleOption[]> {
  return http.get('/api/traj/vehicles')
}

export function getLatestPoints(): Promise<GpsPoint[]> {
  return http.get('/api/traj/latest')
}

export function getTrack(params: {
  identityCode?: string
  plateNo?: string
  start?: string
  end?: string
}): Promise<GpsPoint[]> {
  return http.get('/api/traj/track', { params })
}

export interface LoadSampleResult {
  file: string
  identityCode: string
  timeRange: [string, string]
  insertedPoints: number
  insertedEvents: number
}

export function loadSample(file = 'vehicle_gps_20260901.csv'): Promise<LoadSampleResult> {
  return http.post('/api/ingest/load-sample', { file })
}

// ===== 标准测试数据集（四省市 10 车队 500 车） =====
export interface DatasetStatus {
  running: boolean
  mode: string | null
  stage: string
  percent: number
  counts: { vehicles?: number; points?: number; events?: number; warns?: number; scores?: number }
  error: string | null
  startedAt?: string | null
  finishedAt?: string | null
}

export function datasetLoad(mode: 'standard' | 'dense'): Promise<DatasetStatus> {
  return http.post('/api/ingest/dataset/load', { mode })
}

export function datasetClear(): Promise<{ cleared: boolean }> {
  return http.post('/api/ingest/dataset/clear')
}

export function datasetStatus(): Promise<DatasetStatus> {
  return http.get('/api/ingest/dataset/status')
}

export interface SimVehicle {
  identityCode: string
  plateNo: string
  mode: string
  city?: string | null
}

export interface SimulatorStatus {
  running: boolean
  startedAt?: string | null
  ticks: number
  vehicleCount: number
  demoEvents?: number
  vehicles?: SimVehicle[]
}

export function simulatorStart(): Promise<any> {
  return http.post('/api/simulator/start')
}

export function simulatorStop(): Promise<any> {
  return http.post('/api/simulator/stop')
}

export function simulatorStatus(): Promise<SimulatorStatus> {
  return http.get('/api/simulator/status')
}

/** mock 仿真投递状态（gateway_runner.status 的子集，仅测试模式） */
export interface MockDeliveryStatus {
  mockRunning: boolean
  mockStartedAt?: string | null
  mockStoppedAt?: string | null
  mockDeliveryEnabled?: string
  mockStats?: { ticks: number; warns: number }
}

export function mockDeliveryStart(): Promise<MockDeliveryStatus> {
  return http.post('/api/simulator/mock/start')
}

export function mockDeliveryStop(): Promise<MockDeliveryStatus> {
  return http.post('/api/simulator/mock/stop')
}

/** 清除模拟运行数据（保留基础数据），仅演示/测试模式 */
export function clearRuntimeData(): Promise<{ cleared: boolean }> {
  return http.post('/api/ingest/dataset/clear-runtime')
}
