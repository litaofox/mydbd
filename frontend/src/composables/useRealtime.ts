import { onMounted, onBeforeUnmount, ref } from 'vue'
import type { GpsPoint } from '@/api/traj'
import { getLatestPoints } from '@/api/traj'
import { getOverview, type Overview } from '@/api/monitor'

export type WsType = 'POINTS' | 'RISK' | 'ALARM'

export interface WsMessage {
  type: WsType
  data: any[]
}

export interface RiskBrief {
  id: number
  eventCode: string
  plateNo: string
  riskLevel: number
  eventTime: string
  lng: number
  lat: number
}

export interface AlarmBrief {
  id: number
  plateNo: string
  typeId: number
  startWarnTime: string
  startLng: string
  startLat: string
}

interface UseRealtimeOptions {
  onPoints?: (points: GpsPoint[]) => void
  onRisk?: (risks: RiskBrief[]) => void
  onAlarm?: (alarms: AlarmBrief[]) => void
  onOverview?: (ov: Overview) => void
}

const TOKEN_KEY = 'mydbd-token'
const MAX_RETRY = 30000

/**
 * 实时消息推送 composable：建立 WebSocket 接收位置/风险/报警，
 * 断连指数退避重连，期间降级回 5s 轮询。
 */
export function useRealtime(options: UseRealtimeOptions = {}) {
  const connected = ref(false)
  let ws: WebSocket | null = null
  let retry = 0
  let reconnectTimer: number | null = null
  let pollTimer: number | null = null
  let closedByUser = false

  function getToken(): string {
    return localStorage.getItem(TOKEN_KEY) || ''
  }

  function connect() {
    if (closedByUser) return
    const token = getToken()
    if (!token) return
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.host}/ws/realtime?token=${encodeURIComponent(token)}`
    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      retry = 0
      stopPollFallback()
    }

    ws.onmessage = (e) => {
      try {
        const msg: WsMessage = JSON.parse(e.data)
        if (msg.type === 'POINTS' && options.onPoints) {
          options.onPoints(msg.data as GpsPoint[])
        } else if (msg.type === 'RISK' && options.onRisk) {
          options.onRisk(msg.data as RiskBrief[])
        } else if (msg.type === 'ALARM' && options.onAlarm) {
          options.onAlarm(msg.data as AlarmBrief[])
        }
      } catch {
        // ignore malformed
      }
    }

    ws.onclose = () => {
      connected.value = false
      if (closedByUser) return
      startPollFallback()
      scheduleReconnect()
    }

    ws.onerror = () => {
      try {
        ws?.close()
      } catch {
        /* noop */
      }
    }
  }

  function scheduleReconnect() {
    const delay = Math.min(1000 * Math.pow(2, retry), MAX_RETRY)
    retry++
    reconnectTimer = window.setTimeout(connect, delay)
  }

  async function pollOnce() {
    try {
      const [ov, latest] = await Promise.all([getOverview(), getLatestPoints()])
      options.onOverview?.(ov)
      options.onPoints?.(latest)
    } catch {
      /* ignore, next tick */
    }
  }

  function startPollFallback() {
    if (pollTimer) return
    pollOnce()
    pollTimer = window.setInterval(pollOnce, 5000)
  }

  function stopPollFallback() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  onMounted(() => {
    // 首屏先用轮询拿一次数据，同时建立 WS
    pollOnce()
    connect()
  })

  onBeforeUnmount(() => {
    closedByUser = true
    try {
      ws?.close()
    } catch {
      /* noop */
    }
    stopPollFallback()
    if (reconnectTimer) clearTimeout(reconnectTimer)
  })

  return { connected }
}
