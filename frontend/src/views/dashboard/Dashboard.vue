<template>
  <div class="dash">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">北斗导航数据业务平台 · 监控总览大屏</div>
      <div class="top-mid">
        <span class="scope-chip" title="数据范围随登录用户权限自动过滤">
          <i class="dot"></i>
          当前视角：{{ perspectiveName }} · {{ perspectiveDept }}（{{ perspectiveScope }}）
        </span>
        <span class="clock">{{ clock }}</span>
        <span class="ws-tag" :class="connected ? 'ok' : 'warn'">
          {{ connected ? '实时推送' : '降级轮询' }}
        </span>
      </div>
      <div class="top-ops">
        <button class="op-btn" @click="toggleFullscreen">全屏</button>
        <button class="op-btn exit" @click="exitDash">退出大屏</button>
      </div>
    </header>

    <!-- KPI 指标带 -->
    <div class="kpis">
      <section class="kpi drill" title="查看车辆档案" @click="drill('/mdm/vehicles')">
        <div class="lb">有效车辆<span class="hint">车辆档案 ›</span></div>
        <div class="num">{{ summary?.online.vehicleTotal ?? '--' }}<small>辆</small></div>
        <div class="sub">覆盖 {{ summary?.fleetStats.length ?? '--' }} 个车队</div>
      </section>
      <section class="kpi drill" title="查看实时监控" @click="drill('/monitor')">
        <div class="lb">在线车辆<span class="hint">实时监控 ›</span></div>
        <div class="num good">{{ summary?.online.onlineCount ?? '--' }}<small>辆</small></div>
        <div class="sub">在线率 {{ summary ? fmtRate(summary.online.onlineRate) : '--' }} · 窗口 {{ summary?.online.windowMinutes ?? 5 }} 分钟</div>
      </section>
      <section class="kpi drill" title="查看轨迹回放" @click="drill('/playback')">
        <div class="lb">今日里程<span class="hint">轨迹回放 ›</span></div>
        <div class="num">{{ summary ? fmtKm(summary.mileage.todayMileage) : '--' }}<small>km</small></div>
        <div class="sub">里程表差值近似口径</div>
      </section>
      <section class="kpi drill" title="查看报警中心" @click="drill('/alarms', todayAlarmQuery())">
        <div class="lb">今日报警<span class="hint">报警中心 ›</span></div>
        <div class="num">{{ alarmStats?.total ?? '--' }}<small>起</small></div>
        <div class="sub">待处理 <em>{{ pendingAlarmCount }}</em></div>
      </section>
      <section class="kpi drill" title="查看未处置风险" @click="drill('/risk', { handleStatus: 0 })">
        <div class="lb">未处置风险<span class="hint">风险预警 ›</span></div>
        <div class="num warn">{{ overview?.pendingRisks ?? '--' }}<small>起</small></div>
        <div class="sub">今日风险 {{ overview?.todayRisks ?? '--' }} 起</div>
      </section>
      <section class="kpi drill" title="查看风险事件" @click="drill('/risk')">
        <div class="lb">今日风险事件<span class="hint">风险事件 ›</span></div>
        <div class="num">{{ overview?.todayRisks ?? '--' }}<small>起</small></div>
        <div class="sub">CEP 规则命中</div>
      </section>
    </div>

    <div class="body">
      <!-- 左列 -->
      <aside class="col left">
        <section class="card drill" title="点击查看实时监控" @click="drill('/monitor')">
          <div class="card-title">车辆在线率<span class="drill-hint">实时监控 ›</span></div>
          <div class="online-row">
            <div ref="onlineRingRef" class="ring"></div>
            <div class="online-nums">
              <div class="big">{{ summary?.online.onlineCount ?? '--' }}<span class="unit">辆在线</span></div>
              <div class="sub">有效车辆 {{ summary?.online.vehicleTotal ?? '--' }}</div>
              <div class="sub">窗口 {{ summary?.online.windowMinutes ?? 5 }} 分钟</div>
            </div>
          </div>
        </section>

        <section v-if="(summary?.fleetStats.length ?? 0) >= 2" class="card grow drill" title="点击车辆名称查看该车队档案" @click="drill('/mdm/vehicles')">
          <div class="card-title">我的车队在线 TOP5<span class="drill-hint">车辆档案 ›</span></div>
          <div class="rank">
            <div
              v-for="f in topFleets"
              :key="f.deptId"
              class="rank-row drill"
              :title="`${f.deptName}：在线 ${f.online} / 共 ${f.total} 辆，点击查看车辆档案`"
              @click.stop="drill('/mdm/vehicles', { deptId: f.deptId })"
            >
              <span class="name">{{ f.deptName }}</span>
              <div class="track"><div class="fill" :style="{ width: fleetPct(f) + '%' }"></div></div>
              <span class="val">{{ f.online }}<small>/{{ f.total }}</small></span>
            </div>
          </div>
        </section>

        <section class="card speed-card">
          <div class="card-title">在线车速分布<span class="drill-hint">实时聚合</span></div>
          <div v-if="hasLivePoints" class="speed">
            <div v-for="(b, i) in SPEED_BUCKETS" :key="b.label" class="speed-row">
              <span class="name">{{ b.label }}</span>
              <div class="track"><div class="fill" :class="'sp' + i" :style="{ width: speedPct(i) + '%' }"></div></div>
              <span class="val">{{ speedBuckets[i] }}</span>
            </div>
            <div class="speed-foot">样本 {{ livePointCount }} 辆 · 来自实时定位</div>
          </div>
          <div v-else class="empty">等待实时数据…</div>
        </section>
      </aside>

      <!-- 中央：车队全景 + 实时报警 -->
      <main class="col center">
        <section class="card">
          <div class="card-title">我的车队全景<span class="drill-hint">点击卡片 → 车辆档案（按部门）›</span></div>
          <div v-if="(summary?.fleetStats.length ?? 0) > 0" class="fleet-grid">
            <div
              v-for="f in summary!.fleetStats"
              :key="f.deptId"
              class="fcard"
              :title="`${f.deptName}：在线 ${f.online} / 共 ${f.total} 辆，点击查看车辆档案`"
              @click="drill('/mdm/vehicles', { deptId: f.deptId })"
            >
              <div class="fname">{{ f.deptName }}<span class="go">›</span></div>
              <div class="fnum">{{ f.online }} <small>/ {{ f.total }} 在线</small></div>
              <div class="track"><div class="fill" :style="{ width: fleetPct(f) + '%' }"></div></div>
              <div class="fsub">在线率 {{ fleetPct(f).toFixed(1) }}%</div>
            </div>
          </div>
          <div v-else class="empty">暂无可视车队，请联系管理员分配数据权限</div>
          <div v-if="(summary?.fleetStats.length ?? 0) > 0" class="fleet-foot">
            共 <b>{{ summary!.fleetStats.length }}</b> 个车队 · 数据范围随登录用户权限自动过滤
          </div>
        </section>

        <section class="card grow ticker-card">
          <div class="card-title">实时报警<span class="drill-hint" @click.stop="drill('/alarms', todayAlarmQuery())">报警中心 ›</span></div>
          <AlarmTicker :items="alarmList" @pick="goAlarmCenter" />
        </section>
      </main>

      <!-- 右列 -->
      <aside class="col right">
        <section class="card drill" title="点击查看报警中心" @click="drill('/alarms', todayAlarmQuery())">
          <div class="card-title">今日报警态势<span class="drill-hint">报警中心 ›</span></div>
          <div class="alarm-row">
            <div ref="alarmRingRef" class="ring"></div>
            <div class="alarm-nums">
              <div class="big">{{ alarmStats?.total ?? '--' }}<span class="unit">今日总数</span></div>
              <div class="sub danger-text" title="查看待处理报警">待处理 {{ pendingAlarmCount }}</div>
              <div class="grade-legend">
                <span><i class="dot g3"></i>高 {{ gradeCount(3) }}</span>
                <span><i class="dot g2"></i>中 {{ gradeCount(2) }}</span>
                <span><i class="dot g1"></i>低 {{ gradeCount(1) }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="card grow drill" title="点击查看未处置风险" @click="drill('/risk', { handleStatus: 0 })">
          <div class="card-title">区域分布 TOP8<span class="drill-hint">风险预警 ›</span></div>
          <div v-if="(summary?.regionStats.length ?? 0) > 0" class="region">
            <div
              v-for="r in summary!.regionStats"
              :key="r.cityCode"
              class="region-row drill"
              :title="`${r.cityName}：${r.vehicleCount} 辆车 · 今日 ${r.riskCount} 起风险，点击按该市过滤未处置风险`"
              @click.stop="drill('/risk', { handleStatus: 0, cityCode: r.cityCode, cityName: r.cityName })"
            >
              <span class="name">{{ r.cityName }}</span>
              <div class="track"><div class="fill" :style="{ width: regionPct(r) + '%' }"></div></div>
              <span class="risk">{{ r.riskCount }} 风险</span>
            </div>
          </div>
          <div v-else class="empty">暂无可视区域数据</div>
        </section>

        <section class="card">
          <div class="card-title">工单处置<span class="drill-hint" @click="drill('/risk/orders')">风险工单 ›</span></div>
          <div class="wo-grid">
            <div class="wo-cell blue drill" title="查看待处理工单" @click="drill('/risk/orders', { status: 'PENDING' })">
              <b>{{ summary?.workOrder.pending ?? '--' }}</b><span>待处理</span>
            </div>
            <div class="wo-cell drill" title="查看处理中工单" @click="drill('/risk/orders', { status: 'PROCESSING' })">
              <b>{{ summary?.workOrder.processing ?? '--' }}</b><span>处理中</span>
            </div>
            <div class="wo-cell danger drill" title="查看逾期工单" @click="drill('/risk/orders', { timeFlag: 'overdue' })">
              <b>{{ summary?.workOrder.overdue ?? '--' }}</b><span>逾期</span>
            </div>
          </div>
          <div class="wo-rate drill" title="查看已闭环工单" @click="drill('/risk/orders', { status: 'CLOSED' })">
            近 7 日闭环率
            <b>{{ summary ? fmtRate(summary.workOrder.closeRate) : '--' }}</b>
            <span class="sub">（{{ summary?.workOrder.closed7d ?? 0 }}/{{ summary?.workOrder.created7d ?? 0 }}）</span>
          </div>
        </section>
      </aside>
    </div>

    <!-- 风险飘条（自绘 div，同屏 ≤3 条、5s 自动消失，点击查看该车详情） -->
    <div class="toasts">
      <div
        v-for="t in toasts"
        :key="t.key"
        class="toast"
        title="点击查看车辆详情"
        @click="openPanelByPlate(t.plateNo)"
      >
        新风险：{{ t.plateNo }} {{ t.title }}（等级 {{ t.level }}）
      </div>
    </div>

    <!-- F16 共享车辆详情抽屉 -->
    <VehicleDetailDrawer
      v-model="panelVisible"
      :vehicle-id="panelVehicleId"
      :live-point="panelLivePoint"
      :online="panelOnline"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElLoading, ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { GpsPoint } from '@/api/traj'
import { getVehicleOptions } from '@/api/mdm'
import { getDashboardSummary, getOverview, type DashboardSummary, type DashboardFleetStat, type DashboardRegionStat, type Overview } from '@/api/monitor'
import { getAlarmLatest, getAlarmStats, type AlarmVO, type AlarmStats } from '@/api/alarm'
import { useRealtime, type RiskBrief, type AlarmBrief } from '@/composables/useRealtime'
import { useAuthStore } from '@/store/auth'
import VehicleDetailDrawer from '@/components/VehicleDetailDrawer.vue'
import AlarmTicker from './components/AlarmTicker.vue'
import { useAdaptive } from './components/useAdaptive'

const router = useRouter()
const auth = useAuthStore()

// ===== 响应式状态 =====
const summary = ref<DashboardSummary | null>(null)
const overview = ref<Overview | null>(null)
const alarmStats = ref<AlarmStats | null>(null)
const alarmList = ref<AlarmVO[]>([])
const clock = ref('')

// ===== 当前用户视角（数据本身由后端按 deptScope 裁剪） =====
const perspectiveName = computed(() => auth.realName || auth.username || '--')
const perspectiveDept = computed(() => auth.profile?.deptName || '未分配部门')
const perspectiveScope = computed(() =>
  auth.roles.includes('SUPER_ADMIN') ? '全部数据' : '本部门及下级'
)

const pendingAlarmCount = computed(() => {
  const s = alarmStats.value?.byStatus.find((x) => x.handleStatus === 0)
  return s ? s.count : alarmList.value.length
})
function gradeCount(level: number): number {
  return (alarmStats.value?.byType ?? [])
    .filter((t) => t.gradeLevel === level)
    .reduce((a, t) => a + t.count, 0)
}

function fmtKm(v: number): string {
  return v >= 10000 ? (v / 10000).toFixed(1) + '万' : v.toFixed(1)
}
function fmtRate(v: number | null): string {
  return v == null ? '—' : (v * 100).toFixed(1) + '%'
}

// ===== 时钟 =====
let clockTimer: number | null = null
function tickClock() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// ===== 实时定位存储（无地图：仅用于车速分布聚合与详情抽屉） =====
const pointsStore = reactive(new Map<string, GpsPoint>())

const SPEED_BUCKETS = [
  { label: '<20 km/h', min: 0, max: 20 },
  { label: '20-40', min: 20, max: 40 },
  { label: '40-60', min: 40, max: 60 },
  { label: '60-80', min: 60, max: 80 },
  { label: '≥80', min: 80, max: Infinity }
]
const speedBuckets = computed(() => {
  const counts = [0, 0, 0, 0, 0]
  for (const p of pointsStore.values()) {
    const v = p.speed ?? 0
    const i = v < 20 ? 0 : v < 40 ? 1 : v < 60 ? 2 : v < 80 ? 3 : 4
    counts[i]++
  }
  return counts
})
const speedMax = computed(() => Math.max(1, ...speedBuckets.value))
const hasLivePoints = computed(() => pointsStore.size > 0)
const livePointCount = computed(() => pointsStore.size)
function speedPct(i: number): number {
  return (speedBuckets.value[i] / speedMax.value) * 100
}

// ===== 实时链路（F14 复用，零改动） =====
const { connected } = useRealtime({
  onPoints: handlePoints,
  onRisk: handleRisk,
  onAlarm: handleAlarm,
  onOverview: (ov) => { overview.value = ov }
})

function handlePoints(pts: GpsPoint[]) {
  for (const p of pts) pointsStore.set(p.identityCode, p)
}

// 风险飘条
interface Toast { key: number; plateNo: string; title: string; level: number }
const toasts = ref<Toast[]>([])
let toastSeq = 0
function handleRisk(risks: RiskBrief[]) {
  if (!risks.length) return
  for (const r of risks.slice(-3)) {
    const t: Toast = { key: ++toastSeq, plateNo: r.plateNo, title: r.eventCode, level: r.riskLevel }
    toasts.value.push(t)
    if (toasts.value.length > 3) toasts.value.shift()
    setTimeout(() => {
      toasts.value = toasts.value.filter((x) => x.key !== t.key)
    }, 5000)
  }
}

// ALARM 增量：滚动置顶（id 去重，缓存最近 100）+ 态势卡本地 +1（§5.4）
const seenAlarmIds = new Set<string>()
const alarmIdQueue: string[] = []
function handleAlarm(alarms: AlarmBrief[]) {
  if (!alarms.length) return
  for (const a of alarms) {
    const id = String(a.id)
    if (seenAlarmIds.has(id)) continue
    seenAlarmIds.add(id)
    alarmIdQueue.push(id)
    if (alarmIdQueue.length > 100) {
      const drop = alarmIdQueue.shift()!
      seenAlarmIds.delete(drop)
    }
    alarmList.value.unshift({
      id,
      plateNo: a.plateNo,
      identityCode: null,
      typeId: a.typeId,
      typeName: null,
      gradeLevel: null,
      startWarnTime: a.startWarnTime,
      endWarnTime: null,
      startLng: a.startLng,
      startLat: a.startLat,
      endLng: null,
      endLat: null,
      startSpeed: null,
      endSpeed: null,
      warnContinueMark: null,
      handleStatus: 0,
      handleResultCode: null,
      handleResultMsg: null,
      handler: null,
      updateDate: null
    })
    if (alarmList.value.length > 50) alarmList.value.length = 50
  }
  if (alarmStats.value) {
    alarmStats.value = {
      ...alarmStats.value,
      total: alarmStats.value.total + alarms.length,
      byStatus: alarmStats.value.byStatus.map((s) =>
        s.handleStatus === 0 ? { ...s, count: s.count + alarms.length } : s
      )
    }
  }
}

function goAlarmCenter(a: AlarmVO) {
  drill('/alarms', { plateNo: a.plateNo })
}

// ===== 钻取跳转（携带查询条件 + Loading）=====
function todayAlarmQuery(): { beginTime: string; endTime: string } {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  const day = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  return { beginTime: `${day}T00:00:00`, endTime: `${day}T23:59:59` }
}

function drill(path: string, query: Record<string, string | number> = {}) {
  const loading = ElLoading.service({
    lock: true,
    text: '正在跳转…',
    background: 'rgba(11, 18, 32, 0.6)'
  })
  router.push({ path, query }).finally(() => {
    window.setTimeout(() => loading.close(), 300)
  })
}

// ===== 车队 / 区域排行 =====
const topFleets = computed(() => (summary.value?.fleetStats ?? []).slice(0, 5))
function fleetPct(f: DashboardFleetStat): number {
  return f.total === 0 ? 0 : (f.online / f.total) * 100
}
function regionPct(r: DashboardRegionStat): number {
  const rows = summary.value?.regionStats ?? []
  const max = Math.max(1, ...rows.map((x) => x.vehicleCount))
  return (r.vehicleCount / max) * 100
}

// ===== 30s 统计轮询 =====
let pollTimer: number | null = null
async function loadStats() {
  try {
    summary.value = await getDashboardSummary()
  } catch {
    /* 下一轮再试 */
  }
  try {
    overview.value = await getOverview()
  } catch {
    /* ignore */
  }
}
async function loadAlarmStats() {
  try {
    alarmStats.value = await getAlarmStats()
    renderAlarmRing()
  } catch {
    /* ignore */
  }
}

// ===== ECharts（在线率环 / 报警等级环） =====
const onlineRingRef = ref<HTMLDivElement>()
const alarmRingRef = ref<HTMLDivElement>()
let onlineChart: echarts.ECharts | null = null
let alarmChart: echarts.ECharts | null = null

const AXIS_TEXT = '#94a3b8'

function renderOnlineRing() {
  if (!onlineRingRef.value) return
  onlineChart = onlineChart ?? echarts.init(onlineRingRef.value)
  const rate = summary.value?.online.onlineRate
  onlineChart.setOption({
    series: [{
      type: 'pie',
      radius: ['68%', '88%'],
      silent: true,
      label: {
        show: true,
        position: 'center',
        formatter: rate == null ? '—' : (rate * 100).toFixed(1) + '%',
        color: '#e2e8f0',
        fontSize: 16,
        fontWeight: 700
      },
      data: [
        { value: rate ?? 0, itemStyle: { color: '#22c55e' } },
        { value: 1 - (rate ?? 0), itemStyle: { color: '#334155' } }
      ]
    }]
  })
}

function renderAlarmRing() {
  if (!alarmRingRef.value) return
  alarmChart = alarmChart ?? echarts.init(alarmRingRef.value)
  const data = [
    { name: '高', value: gradeCount(3), itemStyle: { color: '#dc2626' } },
    { name: '中', value: gradeCount(2), itemStyle: { color: '#f97316' } },
    { name: '低', value: gradeCount(1), itemStyle: { color: '#eab308' } }
  ]
  const allZero = data.every((d) => d.value === 0)
  alarmChart.setOption({
    series: [{
      type: 'pie',
      radius: ['68%', '88%'],
      silent: true,
      label: {
        show: true,
        position: 'center',
        formatter: allZero ? '无报警' : '等级分布',
        color: AXIS_TEXT,
        fontSize: 12
      },
      data: allZero ? [{ value: 1, itemStyle: { color: '#334155' } }] : data
    }]
  })
}

useAdaptive(() => {
  onlineChart?.resize()
  alarmChart?.resize()
})

// ===== 全屏 / 退出 =====
function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => { /* ignore */ })
  } else {
    document.documentElement.requestFullscreen().catch(() => { /* 拦截不影响布局 */ })
  }
}
function exitDash() {
  router.push('/monitor')
}

// ===== F16 抽屉接入（同 Monitor.vue 模式） =====
const plateNoToId = new Map<string, string>()
const panelVisible = ref(false)
const panelVehicleId = ref<string | null>(null)
const panelPlateNo = ref<string | null>(null)
const panelLivePoint = computed(() =>
  panelPlateNo.value
    ? [...pointsStore.values()].find((x) => x.plateNo === panelPlateNo.value) ?? null
    : null
)

/** 抽屉在线状态：与监控页同口径——以实时点集中最新定位时间为基准，10 分钟窗口内视为在线（数据时基可能滞后墙钟） */
const panelOnline = computed(() => {
  if (!pointsStore.size) return undefined
  const ts = (v: string) => new Date(String(v).replace('T', ' ').replace(/-/g, '/')).getTime()
  let refTime = 0
  for (const p of pointsStore.values()) refTime = Math.max(refTime, ts(p.gpsTime))
  if (!refTime) return undefined
  const p = panelPlateNo.value
    ? [...pointsStore.values()].find((x) => x.plateNo === panelPlateNo.value)
    : null
  if (!p) return undefined
  return refTime - ts(p.gpsTime) <= 10 * 60 * 1000
})

function openPanelByPlate(plateNo: string) {
  const id = plateNoToId.get(plateNo)
  if (!id) {
    ElMessage.warning('无权查看该车辆或车辆未建档')
    return
  }
  panelVehicleId.value = id
  panelPlateNo.value = plateNo
  panelVisible.value = true
}

// ===== 生命周期 =====
onMounted(async () => {
  tickClock()
  clockTimer = window.setInterval(tickClock, 1000)

  try {
    const opts = await getVehicleOptions()
    for (const o of opts) plateNoToId.set(o.label, String(o.id))
  } catch {
    /* 映射缺失时点选提示未建档，不阻塞 */
  }

  await nextTick()
  loadStats()
  loadAlarmStats()
  try {
    alarmList.value = (await getAlarmLatest(20)).slice(0, 50)
  } catch {
    /* ignore */
  }

  pollTimer = window.setInterval(() => {
    loadStats()
    loadAlarmStats()
  }, 30000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pollTimer) clearInterval(pollTimer)
  onlineChart?.dispose()
  alarmChart?.dispose()
})
</script>

<style scoped>
.dash {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0b1220;
  color: #e2e8f0;
  overflow: hidden;
}

/* 顶栏 */
.topbar {
  height: 3.2rem;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.2rem;
  background: linear-gradient(90deg, #0f1c33, #12233f 50%, #0f1c33);
  border-bottom: 1px solid rgba(59, 130, 246, 0.35);
}
.brand { font-size: 1.15rem; font-weight: 700; color: #dbeafe; letter-spacing: 1px; }
.top-mid { display: flex; align-items: center; gap: 0.8rem; }
.scope-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.78rem;
  color: #93c5fd;
  background: rgba(59, 130, 246, 0.14);
  border: 1px solid rgba(59, 130, 246, 0.35);
  border-radius: 1rem;
  padding: 0.2rem 0.7rem;
  white-space: nowrap;
}
.scope-chip .dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: #4ade80;
}
.clock { font-size: 1rem; color: #93c5fd; font-variant-numeric: tabular-nums; }
.ws-tag {
  font-size: 0.75rem;
  padding: 0.15rem 0.6rem;
  border-radius: 0.8rem;
  border: 1px solid;
}
.ws-tag.ok { color: #4ade80; border-color: rgba(74, 222, 128, 0.5); }
.ws-tag.warn { color: #facc15; border-color: rgba(250, 204, 21, 0.5); }
.top-ops { display: flex; gap: 0.5rem; }
.op-btn {
  background: rgba(59, 130, 246, 0.15);
  color: #bfdbfe;
  border: 1px solid rgba(59, 130, 246, 0.4);
  border-radius: 0.4rem;
  padding: 0.3rem 0.8rem;
  font-size: 0.8rem;
  cursor: pointer;
}
.op-btn:hover { background: rgba(59, 130, 246, 0.3); }
.op-btn.exit { color: #fecaca; border-color: rgba(239, 68, 68, 0.4); background: rgba(239, 68, 68, 0.12); }

/* KPI 指标带 */
.kpis {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 0.7rem;
  padding: 0.7rem 0.7rem 0;
}
.kpi {
  background: rgba(15, 26, 46, 0.85);
  border: 1px solid rgba(59, 130, 246, 0.22);
  border-radius: 0.5rem;
  padding: 0.65rem 0.9rem;
}
.kpi .lb {
  font-size: 0.8rem;
  color: #94a3b8;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.kpi .num {
  font-size: 1.7rem;
  font-weight: 700;
  color: #f1f5f9;
  font-variant-numeric: tabular-nums;
  margin-top: 0.15rem;
}
.kpi .num.good { color: #4ade80; }
.kpi .num.warn { color: #fb923c; }
.kpi .num small { font-size: 0.75rem; font-weight: 400; color: #64748b; margin-left: 0.3rem; }
.kpi .sub { font-size: 0.72rem; color: #64748b; margin-top: 0.1rem; }
.kpi .sub em { font-style: normal; color: #f87171; font-weight: 600; }

/* 主体三栏 */
.body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 20rem minmax(0, 1fr) 20rem;
  gap: 0.7rem;
  padding: 0.7rem;
}
.col { display: flex; flex-direction: column; gap: 0.7rem; min-height: 0; }
.card {
  background: rgba(15, 26, 46, 0.85);
  border: 1px solid rgba(59, 130, 246, 0.22);
  border-radius: 0.5rem;
  padding: 0.7rem 0.9rem;
  display: flex;
  flex-direction: column;
}
.card.grow { flex: 1; min-height: 0; }
.card-title {
  font-size: 0.85rem;
  color: #93c5fd;
  font-weight: 600;
  margin-bottom: 0.5rem;
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

/* 在线率 */
.online-row { display: flex; align-items: center; gap: 0.8rem; }
.ring { width: 7rem; height: 7rem; flex-shrink: 0; }
.online-nums .big, .alarm-nums .big { font-size: 1.6rem; font-weight: 700; color: #f1f5f9; }
.unit { font-size: 0.75rem; color: #64748b; margin-left: 0.3rem; }
.sub { font-size: 0.75rem; color: #94a3b8; margin-top: 0.2rem; }

/* 车队在线排行 */
.rank { display: flex; flex-direction: column; gap: 0.45rem; }
.rank-row {
  display: grid;
  grid-template-columns: 4.6rem 1fr 3.2rem;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  padding: 0.15rem 0.25rem;
  border-radius: 0.3rem;
}
.rank-row .name { color: #cbd5e1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-row .track { height: 0.5rem; border-radius: 0.5rem; background: rgba(51, 65, 85, 0.6); overflow: hidden; }
.rank-row .fill { height: 100%; border-radius: 0.5rem; background: #3b82f6; }
.rank-row .val { text-align: right; color: #e2e8f0; font-variant-numeric: tabular-nums; }
.rank-row .val small { color: #64748b; }

/* 车速分布 */
.speed-card { flex-shrink: 0; }
.speed-row {
  display: grid;
  grid-template-columns: 4.6rem 1fr 2.6rem;
  gap: 0.5rem;
  align-items: center;
  font-size: 0.78rem;
  margin-bottom: 0.35rem;
}
.speed-row:last-of-type { margin-bottom: 0; }
.speed-row .name { color: #94a3b8; }
.speed-row .track { height: 0.5rem; border-radius: 0.5rem; background: rgba(51, 65, 85, 0.6); overflow: hidden; }
.speed-row .fill { height: 100%; border-radius: 0.5rem; background: #22c55e; }
.speed-row .fill.sp2 { background: #3b82f6; }
.speed-row .fill.sp3 { background: #f97316; }
.speed-row .fill.sp4 { background: #dc2626; }
.speed-row .val { text-align: right; color: #e2e8f0; font-variant-numeric: tabular-nums; }
.speed-foot { font-size: 0.7rem; color: #475569; margin-top: 0.45rem; }

/* 车队全景 */
.fleet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
  gap: 0.55rem;
}
.fcard {
  background: rgba(30, 58, 95, 0.45);
  border: 1px solid rgba(59, 130, 246, 0.22);
  border-radius: 0.4rem;
  padding: 0.6rem 0.7rem;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.fcard:hover { background: rgba(59, 130, 246, 0.2); border-color: rgba(59, 130, 246, 0.5); }
.fcard .fname {
  font-size: 0.78rem;
  color: #94a3b8;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.fcard .go { color: #64748b; }
.fcard:hover .go { color: #93c5fd; }
.fcard .fnum {
  font-size: 1.3rem;
  font-weight: 700;
  color: #f1f5f9;
  margin-top: 0.2rem;
  font-variant-numeric: tabular-nums;
}
.fcard .fnum small { font-size: 0.72rem; font-weight: 400; color: #64748b; }
.fcard .track { height: 0.4rem; border-radius: 0.4rem; background: rgba(51, 65, 85, 0.6); margin: 0.4rem 0 0.35rem; overflow: hidden; }
.fcard .fill { height: 100%; border-radius: 0.4rem; background: #22c55e; }
.fcard .fsub { font-size: 0.7rem; color: #94a3b8; }
.fleet-foot { font-size: 0.72rem; color: #475569; margin-top: 0.5rem; }
.fleet-foot b { color: #93c5fd; font-weight: 600; }

/* 实时报警 */
.ticker-card { padding-bottom: 0.4rem; }

/* 区域分布 */
.region { display: flex; flex-direction: column; gap: 0.45rem; }
.region-row {
  display: grid;
  grid-template-columns: 4.6rem 1fr 4.2rem;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  padding: 0.15rem 0.25rem;
  border-radius: 0.3rem;
}
.region-row .name { color: #cbd5e1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.region-row .track { height: 0.5rem; border-radius: 0.5rem; background: rgba(51, 65, 85, 0.6); overflow: hidden; }
.region-row .fill { height: 100%; border-radius: 0.5rem; background: #3b82f6; }
.region-row .risk { text-align: right; color: #fb923c; font-weight: 600; white-space: nowrap; font-variant-numeric: tabular-nums; }

/* 报警态势 */
.alarm-row { display: flex; align-items: center; gap: 0.8rem; }
.alarm-nums .big { font-size: 1.5rem; font-weight: 700; }
.danger-text { color: #f87171; }
.grade-legend { display: flex; gap: 0.6rem; margin-top: 0.4rem; font-size: 0.75rem; color: #94a3b8; }
.dot { display: inline-block; width: 0.55rem; height: 0.55rem; border-radius: 50%; margin-right: 0.2rem; }
.dot.g3 { background: #dc2626; }
.dot.g2 { background: #f97316; }
.dot.g1 { background: #eab308; }

/* 工单 */
.wo-grid { display: flex; gap: 0.6rem; }
.wo-cell {
  flex: 1;
  background: rgba(30, 58, 95, 0.45);
  border-radius: 0.4rem;
  padding: 0.5rem;
  text-align: center;
}
.wo-cell b { display: block; font-size: 1.4rem; color: #bfdbfe; }
.wo-cell.blue b { color: #60a5fa; }
.wo-cell span { font-size: 0.72rem; color: #94a3b8; }
.wo-cell.danger b { color: #f87171; }
.wo-rate { margin-top: 0.6rem; font-size: 0.8rem; color: #94a3b8; }
.wo-rate b { color: #4ade80; margin: 0 0.3rem; }

/* 空态 */
.empty {
  color: #64748b;
  font-size: 0.8rem;
  text-align: center;
  padding: 1rem 0;
}

/* 钻取交互 */
.drill { cursor: pointer; transition: background 0.18s, border-color 0.18s; }
.drill:hover { background: rgba(30, 58, 95, 0.55); border-color: rgba(59, 130, 246, 0.5); }
.rank-row.drill:hover, .region-row.drill:hover { background: rgba(59, 130, 246, 0.18); }
.drill-hint {
  font-size: 0.72rem;
  color: #64748b;
  font-weight: 400;
  cursor: pointer;
  transition: color 0.18s;
  flex-shrink: 0;
}
.drill:hover .drill-hint,
.drill-hint:hover { color: #93c5fd; }
.wo-cell.drill:hover { background: rgba(59, 130, 246, 0.2); }
.wo-cell.danger.drill:hover { background: rgba(239, 68, 68, 0.25); }

/* 飘条 */
.toasts {
  position: fixed;
  top: 4rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  z-index: 500;
  pointer-events: none;
}
.toast {
  background: rgba(185, 28, 28, 0.92);
  color: #fff;
  font-size: 0.85rem;
  padding: 0.45rem 1rem;
  border-radius: 0.4rem;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);
  pointer-events: auto;
  cursor: pointer;
}
.toast:hover { background: rgba(220, 38, 38, 0.95); }
</style>
