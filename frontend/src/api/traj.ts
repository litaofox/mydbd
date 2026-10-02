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

export function simulatorStart(): Promise<any> {
  return http.post('/api/simulator/start')
}

export function simulatorStop(): Promise<any> {
  return http.post('/api/simulator/stop')
}

export function simulatorStatus(): Promise<{ running: boolean; ticks: number; vehicleCount: number }> {
  return http.get('/api/simulator/status')
}
