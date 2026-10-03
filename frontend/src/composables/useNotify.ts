import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import {
  getNotifySettings,
  latestMessages,
  markRead,
  unreadCount,
  type NotifyMessage,
  type NotifySettings
} from '@/api/notify'

const TOKEN_KEY = 'mydbd-token'
const MAX_RETRY = 30000

/** WS 推送的 NOTIFY 载荷（与后端 NotifyService.pushWebsocket 字段一致） */
interface NotifyPayload {
  id: string
  title: string
  content: string
  level: number
  eventType: string
  bizType: string
  bizId: string
  orderNo: string
  plateNo: string
  createDate: string
}

// 模块级单例状态：MainLayout 全局挂载一次
const unread = ref(0)
const latest = ref<NotifyMessage[]>([])
const settings = ref<NotifySettings>({ popupMinLevel: 3, soundEnabled: true })
const wsConnected = ref(false)

let ws: WebSocket | null = null
let retry = 0
let reconnectTimer: number | null = null
let closedByUser = false
let started = false

function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/** Web Audio 蜂鸣提示音（无音频资源依赖，失败静默） */
function beep() {
  try {
    const Ctx = window.AudioContext || (window as any).webkitAudioContext
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = 880
    gain.gain.setValueAtTime(0.12, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.6)
    osc.connect(gain).connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + 0.6)
    osc.onended = () => ctx.close().catch(() => undefined)
  } catch {
    /* 浏览器策略限制时静默 */
  }
}

function levelTagType(level: number): 'danger' | 'warning' | 'info' {
  return level >= 3 ? 'danger' : level === 2 ? 'warning' : 'info'
}

function openOrder(router: ReturnType<typeof useRouter>, msg: NotifyPayload | NotifyMessage) {
  const bizId = (msg as NotifyPayload).bizId
  if (bizId) {
    router.push({ path: '/risk/orders', query: { openOrder: bizId } })
  } else {
    router.push('/risk/orders')
  }
}

function handleMessage(router: ReturnType<typeof useRouter>, msg: NotifyPayload) {
  unread.value += 1
  latest.value = [
    {
      id: msg.id,
      title: msg.title,
      content: msg.content,
      level: msg.level,
      eventType: msg.eventType,
      bizType: msg.bizType,
      bizId: msg.bizId,
      isRead: 0,
      createDate: msg.createDate,
      readDate: null
    } as NotifyMessage,
    ...latest.value
  ].slice(0, 10)

  const min = settings.value.popupMinLevel
  if (msg.level >= min) {
    if (settings.value.soundEnabled) beep()
    ElNotification({
      title: msg.title,
      message: msg.content,
      type: levelTagType(msg.level) === 'danger' ? 'error' : 'warning',
      duration: 0,
      position: 'bottom-right',
      onClick: () => openOrder(router, msg)
    })
  } else if (msg.level === 2 && 'Notification' in window && Notification.permission === 'granted') {
    try {
      const n = new Notification(msg.title, { body: msg.content })
      n.onclick = () => {
        window.focus()
        openOrder(router, msg)
      }
    } catch {
      /* 静默 */
    }
  }
}

function connect(router: ReturnType<typeof useRouter>) {
  if (closedByUser) return
  const token = getToken()
  if (!token) return
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${proto}://${window.location.host}/ws/realtime?token=${encodeURIComponent(token)}`
  ws = new WebSocket(url)

  ws.onopen = () => {
    wsConnected.value = true
    retry = 0
  }

  ws.onmessage = (e) => {
    try {
      const parsed = JSON.parse(e.data)
      if (parsed && parsed.type === 'NOTIFY') {
        // 后端单条推送 data 为对象
        const msg = parsed.data as NotifyPayload
        if (msg && msg.id) handleMessage(router, msg)
      }
    } catch {
      /* ignore malformed */
    }
  }

  ws.onclose = () => {
    wsConnected.value = false
    if (closedByUser) return
    scheduleReconnect(router)
  }

  ws.onerror = () => {
    try {
      ws?.close()
    } catch {
      /* noop */
    }
  }
}

function scheduleReconnect(router: ReturnType<typeof useRouter>) {
  const delay = Math.min(1000 * Math.pow(2, retry), MAX_RETRY)
  retry++
  reconnectTimer = window.setTimeout(() => connect(router), delay)
}

/**
 * F19 全局通知 composable：MainLayout 挂载一次。
 * 独立 WS 连接仅消费 NOTIFY 消息（与 Monitor 页 useRealtime 互不影响）。
 */
export function useNotify() {
  const router = useRouter()

  async function refresh() {
    try {
      const [cnt, list] = await Promise.all([unreadCount(), latestMessages(10)])
      unread.value = Number(cnt.count)
      latest.value = list
    } catch {
      /* 静默 */
    }
  }

  async function init() {
    if (started) return
    started = true
    try {
      settings.value = await getNotifySettings()
    } catch {
      /* 用默认值 */
    }
    await refresh()
    connect(router)
  }

  async function readOne(id: string) {
    await markRead(id)
    unread.value = Math.max(0, unread.value - 1)
    const m = latest.value.find((x) => x.id === id)
    if (m) m.isRead = 1
  }

  function dispose() {
    closedByUser = true
    started = false
    try {
      ws?.close()
    } catch {
      /* noop */
    }
    if (reconnectTimer) clearTimeout(reconnectTimer)
  }

  return { unread, latest, settings, wsConnected, init, refresh, readOne, dispose }
}
