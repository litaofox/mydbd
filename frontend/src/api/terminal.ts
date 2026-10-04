import http from './http'

/**
 * 终端扩展能力接口（预留）
 * ---------------------------------------------------------------
 * 实时监控页按真实契约调用以下接口；后端服务尚未实现（当前返回 404/501），
 * 前端统一静默降级并提示"下一版本提供"。后端按本文件契约补齐后即可点亮：
 *
 * 1. reverseGeocode   逆地理编码（经纬度 -> 文字地址），建议对接高德 REST
 *    GET  /api/terminal/geocode/reverse?lng=&lat=
 *    -> { address: string }
 *
 * 2. listVideoChannels 车载视频通道（JT/T 1078）
 *    GET  /api/terminal/{identityCode}/channels
 *    -> VideoChannel[] （通道号 1~N，含音视频类型与在线状态）
 *
 * 3. playVideoChannel 获取指定通道实时视频的播放地址（JT/T 1078 实时音视频传输 0x9101）
 *    POST /api/terminal/{identityCode}/channels/{channelNo}/play
 *    -> { playUrl: string, protocol: 'ws-flv'|'hls'|'webrtc' }
 *
 * 4. sendCommand  JT/T 808 终端指令统一下发
 *    POST /api/terminal/{identityCode}/command  { command, params? }
 *    预定义 command：
 *      - PHOTO          立即拍照（0x8801 存储介质影像采集，可带 channel/audioFlag）
 *      - TAP_MONITOR    监听/对讲（0x8400 电话回拨，params: { type: 'monitor'|'talk' }）
 *      - TEXT_DISPATCH  文本信息下发（0x8300，params: { content, urgent }）
 *      - TRACK          持续跟踪（平台侧也可纯前端实现，无需下发）
 *    -> { commandSerial: number, accepted: boolean }
 */

// 全部预留接口静默处理：不触发全局错误弹窗，由调用方做友好降级
const silent = { skipErrorMessage: true } as any

export interface VideoChannel {
  channelNo: number
  channelName: string | null
  mediaType: number // 0音视频 1音频 2视频
}

export interface CommandResult {
  commandSerial?: number
  accepted?: boolean
  [k: string]: unknown
}

/** 逆地理编码：经纬度转文字地址 */
export function reverseGeocode(lng: number, lat: number): Promise<{ address: string }> {
  return http.get('/api/terminal/geocode/reverse', { params: { lng, lat }, ...silent })
}

/** 地址检索（关键字 -> 候选坐标点），用于地图右上角地址搜索框 */
export function placeSearch(
  keyword: string,
  city?: string
): Promise<Array<{ name: string; address: string; lng: number; lat: number }>> {
  return http.get('/api/terminal/geocode/search', { params: { keyword, city }, ...silent })
}

/** 查询终端视频通道列表（JT/T 1078） */
export function listVideoChannels(identityCode: string): Promise<VideoChannel[]> {
  return http.get(`/api/terminal/${encodeURIComponent(identityCode)}/channels`, silent)
}

/** 申请通道实时视频播放地址 */
export function playVideoChannel(
  identityCode: string,
  channelNo: number
): Promise<{ playUrl: string; protocol: string }> {
  return http.post(
    `/api/terminal/${encodeURIComponent(identityCode)}/channels/${channelNo}/play`,
    {},
    silent
  )
}

/** JT/T 808 终端指令下发（拍照/监听对讲/文本调度等） */
export function sendCommand(
  identityCode: string,
  command: 'PHOTO' | 'TAP_MONITOR' | 'TEXT_DISPATCH' | string,
  params?: Record<string, unknown>
): Promise<CommandResult> {
  return http.post(
    `/api/terminal/${encodeURIComponent(identityCode)}/command`,
    { command, params },
    silent
  )
}
