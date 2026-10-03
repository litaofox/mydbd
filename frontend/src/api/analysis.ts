import http from './http'

export interface FeatureItem {
  count: number
  deduct: number
}

export interface ScoreRow {
  id: string
  scoreDate: string
  driverId: string
  driverName: string
  identityCode: string
  plateNo: string
  deptId: string
  score: number
  level: string
  samplePoints: number
  eventCount: number
  features: Record<string, FeatureItem | number>
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface TrendPoint {
  scoreDate: string
  score: number
  level: string
}

export interface LevelCount {
  level: string
  count: number
}

export interface ScoreSummary {
  avgScore: number
  recordCount: number
  driverCount: number
  levelDist: LevelCount[]
}

export interface ScorePageParams {
  page?: number
  size?: number
  startDate?: string
  endDate?: string
  driverId?: string
  plateNo?: string
  level?: string
}

export function getScorePage(params: ScorePageParams): Promise<PageResult<ScoreRow>> {
  return http.get('/api/analysis/score/page', { params })
}

export function getScoreTrend(params: {
  driverId: string
  startDate?: string
  endDate?: string
}): Promise<TrendPoint[]> {
  return http.get('/api/analysis/score/trend', { params })
}

export function getScoreSummary(params: {
  startDate?: string
  endDate?: string
}): Promise<ScoreSummary> {
  return http.get('/api/analysis/score/summary', { params })
}

export function recalcScore(data: {
  start: string
  end: string
  driverId?: string
}): Promise<{ accepted: boolean; start: string; end: string }> {
  return http.post('/api/analysis/score/recalc', data)
}

// ===== F23 风险趋势与画像（追加区，getProfile* 前缀防撞名）=====

export interface ProfileTrend {
  dim: string
  id: string | null
  granularity: string
  buckets: string[]
  total: number[]
  byLevel: { high: number[]; mid: number[]; low: number[] }
  topCodes: { code: string; name: string; series: number[] }[]
}

export interface ProfileObjectCard {
  type: string
  id: string
  name: string
  sub: string | null
  eventTotal: number
  highCnt: number
  scoreReady: boolean
  scoreCurve: { dates: string[]; scores: number[]; levels: (string | null)[] } | null
  latestScore: { score: number; level: string } | null
  composition: { code: string; name: string; cnt: number; high: number; mid: number; low: number }[]
  funnel: {
    eventTotal: number
    orderTotal: number
    interventionTotal: number
    closedTotal: number
    interventionRate: number | null
    closeRate: number | null
  } | null
  topOrders: {
    id: string
    orderNo: string
    title: string
    riskLevel: number
    status: string
    overdue: number
    eventTime: string
  }[]
}

export interface ProfileHotspots {
  radiusM: number
  approx: boolean
  note: string
  items: {
    rank: number
    lng: number
    lat: number
    eventCnt: number
    weightedScore: number
    plateCnt: number
    topPlate: string
    topCode: string
  }[]
}

export interface ProfileRanking {
  dim: string
  items: {
    rank: number
    id: string
    name: string
    sub: string | null
    eventCnt: number
    highCnt: number
    weightedScore: number
    score: number | null
    level: string | null
  }[]
}

export function getProfileTrend(params: {
  dim: string
  id?: string
  start: string
  end: string
  granularity: string
}): Promise<ProfileTrend> {
  return http.get('/api/analysis/profile/trend', { params })
}

export function getProfileObjectCard(params: {
  type: string
  id: string
  start: string
  end: string
}): Promise<ProfileObjectCard> {
  return http.get('/api/analysis/profile/object-card', { params })
}

export function getProfileHotspots(params: {
  start: string
  end: string
  radius_m?: number
  limit?: number
}): Promise<ProfileHotspots> {
  return http.get('/api/analysis/profile/hotspots', { params })
}

export function getProfileRanking(params: {
  dim: string
  start: string
  end: string
  limit?: number
}): Promise<ProfileRanking> {
  return http.get('/api/analysis/profile/ranking', { params })
}
