import http from './http'

/** 运行模式页网关状态（GATEWAY-PLAN-001，菜单 925「运行模式」） */
export interface GatewayTopicStat {
  topic: string
  msgCount: number
  errCount: number
  lastTime: string | null
}

export interface GatewayStatus {
  mode: string
  enabled: boolean
  running: boolean
  startedAt: string | null
  lastError: string | null
  groupId: string | null
  topics: string[]
  stats: GatewayTopicStat[]
  dlqCount: number
  unknownCount: number
  bootstrapServers?: string
  mediaStrategy?: string
  fileBaseUrl?: string
  /** 各参数启动时的来源：db 系统参数 / env 环境变量 / default 默认 */
  configSource?: Record<string, 'db' | 'env' | 'default'>
  /** mock 仿真投递（测试模式）：运行时启停，状态持久化于 sys_config */
  mockDeliveryEnabled?: 'on' | 'off' | string
  mockRunning?: boolean
  mockStartedAt?: string | null
  mockStoppedAt?: string | null
  mockStats?: { ticks: number; warns: number }
}

export interface UnknownTerminal {
  id: number
  phoneNumber: string | null
  truckId: string | null
  plateNo: string | null
  msgCount: number
  firstSeen: string | null
  lastSeen: string | null
}

export function getGatewayStatus(): Promise<GatewayStatus> {
  return http.get('/api/monitor/gateway/status')
}

/** 未登记终端清单（后端可能直接返回数组，此处统一归一为数组） */
export async function getUnknownTerminals(): Promise<UnknownTerminal[]> {
  const res: any = await http.get('/api/monitor/gateway/unknown-terminals')
  if (Array.isArray(res)) return res
  return res?.items ?? []
}
