<template>
  <div class="dash">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">北斗导航数据业务平台 · 监控总览大屏</div>
      <div class="top-mid">
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

    <div class="body">
      <!-- 左列 -->
      <aside class="col left">
        <section class="card">
          <div class="card-title">车辆在线率</div>
          <div class="online-row">
            <div ref="onlineRingRef" class="ring"></div>
            <div class="online-nums">
              <div class="big">{{ summary?.online.onlineCount ?? '--' }}<span class="unit">辆在线</span></div>
              <div class="sub">有效车辆 {{ summary?.online.vehicleTotal ?? '--' }}</div>
              <div class="sub">窗口 {{ summary?.online.windowMinutes ?? 5 }} 分钟</div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-title">今日里程（全网）</div>
          <div class="mileage">
            <span class="big">{{ summary ? fmtKm(summary.mileage.todayMileage) : '--' }}</span>
            <span class="unit">km</span>
          </div>
          <div class="tip-line">里程表差值近似口径</div>
        </section>

        <section class="card grow">
          <div class="card-title">处置工单积压</div>
          <div class="wo-grid">
            <div class="wo-cell"><b>{{ summary?.workOrder.pending ?? '--' }}</b><span>待处理</span></div>
            <div class="wo-cell"><b>{{ summary?.workOrder.processing ?? '--' }}</b><span>处理中</span></div>
            <div class="wo-cell danger"><b>{{ summary?.workOrder.overdue ?? '--' }}</b><span>逾期</span></div>
          </div>
          <div class="wo-rate">
            近 7 日闭环率
            <b>{{ summary ? fmtRate(summary.workOrder.closeRate) : '--' }}</b>
            <span class="sub">（{{ summary?.workOrder.closed7d ?? 0 }}/{{ summary?.workOrder.created7d ?? 0 }}）</span>
          </div>
        </section>
      </aside>

      <!-- 中央地图 + 底部条形 -->
      <main class="col center">
        <section class="card map-card">
          <div ref="mapRef" class="map-box"></div>
        </section>
        <div class="bottom-row">
          <section class="card half">
            <div class="card-title">车队分布 TOP8（辆）</div>
            <div ref="fleetChartRef" class="chart"></div>
          </section>
          <section class="card half">
            <div class="card-title">区域分布 TOP8（注册地）</div>
            <div ref="regionChartRef" class="chart"></div>
          </section>
        </div>
      </main>

      <!-- 右列 -->
      <aside class="col right">
        <section class="card">
          <div class="card-title">今日报警态势</div>
          <div class="alarm-row">
            <div ref="alarmRingRef" class="ring"></div>
            <div class="alarm-nums">
              <div class="big">{{ alarmStats?.total ?? '--' }}<span class="unit">今日总数</span></div>
              <div class="sub danger-text">待处理 {{ pendingAlarmCount }}</div>
              <div class="grade-legend">
                <span><i class="dot g3"></i>高 {{ gradeCount(3) }}</span>
                <span><i class="dot g2"></i>中 {{ gradeCount(2) }}</span>
                <span><i class="dot g1"></i>低 {{ gradeCount(1) }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="card grow ticker-card">
          <AlarmTicker :items="alarmList" @pick="goAlarmCenter" />
        </section>
      </aside>
    </div>

    <!-- 风险飘条（自绘 div，同屏 ≤3 条、5s 自动消失） -->
    <div class="toasts">
      <div v-for="t in toasts" :key="t.key" class="toast">
        新风险：{{ t.plateNo }} {{ t.title }}（等级 {{ t.level }}）
      </div>
    </div>

    <!-- F16 共享车辆详情抽屉 -->
    <VehicleDetailDrawer
      v-model="panelVisible"
      :vehicle-id="panelVehicleId"
      :live-point="panelLivePoint"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { GpsPoint } from '@/api/traj'
import { getVehicleOptions } from '@/api/mdm'
import { getDashboardSummary, type DashboardSummary } from '@/api/monitor'
import { getAlarmLatest, getAlarmStats, type AlarmVO, type AlarmStats } from '@/api/alarm'
import { useRealtime, type RiskBrief, type AlarmBrief } from '@/composables/useRealtime'
import VehicleDetailDrawer from '@/components/VehicleDetailDrawer.vue'
import AlarmTicker from './components/AlarmTicker.vue'
import { HeatLayer } from './components/HeatLayer'
import { useAdaptive } from './components/useAdaptive'

const router = useRouter()

// ===== 响应式状态 =====
const summary = ref<DashboardSummary | null>(null)
const alarmStats = ref<AlarmStats | null>(null)
const alarmList = ref<AlarmVO[]>([])
const points = ref<GpsPoint[]>([])
const clock = ref('')

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

// ===== 实时链路（F14 复用，零改动） =====
const { connected } = useRealtime({
  onPoints: handlePoints,
  onRisk: handleRisk,
  onAlarm: handleAlarm,
  onOverview: () => { /* 大屏统计走 30s summary，不使用 overview 降级载荷 */ }
})

// rAF 合帧：回调只暂存 + 置脏（§7.5-1）
let pendingPoints: GpsPoint[] | null = null
let dirty = false
let rafId = 0

function handlePoints(pts: GpsPoint[]) {
  pendingPoints = pts
  dirty = true
}

// 风险飘条
interface Toast { key: number; plateNo: string; title: string; level: number }
const toasts = ref<Toast[]>([])
let toastSeq = 0
function handleRisk(risks: RiskBrief[]) {
  if (!risks.length) return
  for (const r of risks) {
    if (r.lng != null && r.lat != null) heat?.bump(r.lng, r.lat, r.riskLevel)
  }
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
  router.push({ path: '/alarms', query: { plateNo: a.plateNo } })
}

// ===== 30s 统计轮询 =====
let pollTimer: number | null = null
async function loadStats() {
  try {
    summary.value = await getDashboardSummary()
    renderCharts()
    if (heat) heat.render(summary.value.riskHeat)
  } catch {
    /* 下一轮再试 */
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

// ===== 地图：marker 差分更新（§7.5-2） =====
const mapRef = ref<HTMLDivElement>()
let map: L.Map | null = null
let markerLayer: L.LayerGroup | null = null
let heat: HeatLayer | null = null
const markerMap = new Map<string, { marker: L.Marker; dir: number }>()
let firstFit = false

function makeIcon(p: GpsPoint): L.DivIcon {
  return L.divIcon({
    className: '',
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    html: `<div class="vehicle-marker ${p.alarmFlag === 1 ? 'alarm' : ''}">
             <span style="display:inline-block;transform:rotate(${p.direction ?? 0}deg)">&#10148;</span>
           </div>`
  })
}

function renderFrame() {
  rafId = requestAnimationFrame(renderFrame)
  if (!dirty || !map || !markerLayer) return
  dirty = false
  const pts = pendingPoints
  if (!pts) return
  points.value = pts

  const bounds: L.LatLngExpression[] = []
  const seen = new Set<string>()
  const view = map.getBounds()
  for (const p of pts) {
    seen.add(p.identityCode)
    bounds.push([p.lat, p.lng])
    let entry = markerMap.get(p.identityCode)
    if (!entry) {
      const marker = L.marker([p.lat, p.lng], { icon: makeIcon(p) })
        .bindTooltip(`${p.plateNo}　${p.speed} km/h`, { direction: 'top' })
      marker.on('click', () => openPanel(p))
      markerLayer.addLayer(marker)
      entry = { marker, dir: p.direction ?? -999 }
      markerMap.set(p.identityCode, entry)
    } else {
      entry.marker.setLatLng([p.lat, p.lng])
      const dir = p.direction ?? 0
      if (Math.abs(dir - entry.dir) > 15) {
        entry.marker.setIcon(makeIcon(p))
        entry.dir = dir
      }
    }
    if (view.contains([p.lat, p.lng])) {
      entry.marker.setTooltipContent(`${p.plateNo}　${p.speed} km/h`)
    }
  }
  for (const [code, entry] of markerMap) {
    if (!seen.has(code)) {
      markerLayer.removeLayer(entry.marker)
      markerMap.delete(code)
    }
  }
  if (!firstFit && bounds.length > 0) {
    map.fitBounds(L.latLngBounds(bounds).pad(0.2))
    firstFit = true
  }
}

// ===== F16 抽屉接入（同 Monitor.vue 模式） =====
const plateNoToId = new Map<string, string>()
const panelVisible = ref(false)
const panelVehicleId = ref<string | null>(null)
const panelPlateNo = ref<string | null>(null)
const panelLivePoint = computed(() =>
  panelPlateNo.value ? points.value.find((x) => x.plateNo === panelPlateNo.value) ?? null : null
)

function openPanel(p: GpsPoint) {
  const id = plateNoToId.get(p.plateNo)
  if (!id) {
    ElMessage.warning('无权查看该车辆或车辆未建档')
    return
  }
  panelVehicleId.value = id
  panelPlateNo.value = p.plateNo
  panelVisible.value = true
}

// ===== ECharts =====
const onlineRingRef = ref<HTMLDivElement>()
const alarmRingRef = ref<HTMLDivElement>()
const fleetChartRef = ref<HTMLDivElement>()
const regionChartRef = ref<HTMLDivElement>()
let onlineChart: echarts.ECharts | null = null
let alarmChart: echarts.ECharts | null = null
let fleetChart: echarts.ECharts | null = null
let regionChart: echarts.ECharts | null = null

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

function barOption(rows: { name: string; a: number; b: number; aName: string; bName: string }[]) {
  return {
    grid: { left: 8, right: 24, top: 22, bottom: 4, containLabel: true },
    legend: { top: 0, textStyle: { color: AXIS_TEXT, fontSize: 11 }, itemWidth: 12, itemHeight: 8 },
    xAxis: {
      type: 'value',
      axisLabel: { color: AXIS_TEXT, fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(148,163,184,.15)' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((r) => r.name),
      axisLabel: { color: '#cbd5e1', fontSize: 11, width: 90, overflow: 'truncate' },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      { name: rows[0]?.aName ?? '', type: 'bar', data: rows.map((r) => r.a), barWidth: 7, itemStyle: { color: '#3b82f6', borderRadius: 3 } },
      { name: rows[0]?.bName ?? '', type: 'bar', data: rows.map((r) => r.b), barWidth: 7, itemStyle: { color: '#22c55e', borderRadius: 3 } }
    ]
  }
}

function renderCharts() {
  renderOnlineRing()
  const s = summary.value
  if (!s) return
  if (fleetChartRef.value) {
    fleetChart = fleetChart ?? echarts.init(fleetChartRef.value)
    fleetChart.setOption(barOption(
      s.fleetStats.map((f) => ({ name: f.deptName, a: f.total, b: f.online, aName: '车辆数', bName: '在线数' }))
    ), true)
  }
  if (regionChartRef.value) {
    regionChart = regionChart ?? echarts.init(regionChartRef.value)
    regionChart.setOption(barOption(
      s.regionStats.map((r) => ({ name: r.cityName, a: r.vehicleCount, b: r.riskCount, aName: '车辆数', bName: '今日风险' }))
    ), true)
  }
}

useAdaptive(() => {
  onlineChart?.resize()
  alarmChart?.resize()
  fleetChart?.resize()
  regionChart?.resize()
  map?.invalidateSize()
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

// ===== 生命周期 =====
onMounted(async () => {
  tickClock()
  clockTimer = window.setInterval(tickClock, 1000)

  map = L.map(mapRef.value!, { zoomControl: true, attributionControl: false }).setView([39.91, 116.4], 11)
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18 }
  ).addTo(map)
  heat = new HeatLayer(map)
  markerLayer = L.layerGroup().addTo(map)
  rafId = requestAnimationFrame(renderFrame)

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
  cancelAnimationFrame(rafId)
  heat?.destroy()
  markerMap.clear()
  if (map) {
    map.remove()
    map = null
  }
  onlineChart?.dispose()
  alarmChart?.dispose()
  fleetChart?.dispose()
  regionChart?.dispose()
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

/* 主体三栏 */
.body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 21rem minmax(0, 1fr) 21rem;
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
}

/* 在线率 */
.online-row { display: flex; align-items: center; gap: 0.8rem; }
.ring { width: 7rem; height: 7rem; flex-shrink: 0; }
.online-nums .big, .mileage .big { font-size: 1.6rem; font-weight: 700; color: #f1f5f9; }
.unit { font-size: 0.75rem; color: #64748b; margin-left: 0.3rem; }
.sub { font-size: 0.75rem; color: #94a3b8; margin-top: 0.2rem; }

/* 里程 */
.mileage { display: flex; align-items: baseline; }
.tip-line { font-size: 0.7rem; color: #475569; margin-top: 0.3rem; }

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
.wo-cell span { font-size: 0.72rem; color: #94a3b8; }
.wo-cell.danger b { color: #f87171; }
.wo-rate { margin-top: 0.6rem; font-size: 0.8rem; color: #94a3b8; }
.wo-rate b { color: #4ade80; margin: 0 0.3rem; }

/* 地图 */
.center { min-width: 0; }
.map-card { flex: 1; min-height: 0; padding: 0; overflow: hidden; }
.map-box { width: 100%; height: 100%; }
.bottom-row { display: flex; gap: 0.7rem; height: 13rem; flex-shrink: 0; }
.half { flex: 1; min-width: 0; }
.chart { flex: 1; min-height: 0; }

/* 报警 */
.alarm-row { display: flex; align-items: center; gap: 0.8rem; }
.alarm-nums .big { font-size: 1.5rem; font-weight: 700; }
.danger-text { color: #f87171; }
.grade-legend { display: flex; gap: 0.6rem; margin-top: 0.4rem; font-size: 0.75rem; color: #94a3b8; }
.dot { display: inline-block; width: 0.55rem; height: 0.55rem; border-radius: 50%; margin-right: 0.2rem; }
.dot.g3 { background: #dc2626; }
.dot.g2 { background: #f97316; }
.dot.g1 { background: #eab308; }
.ticker-card { padding-bottom: 0.4rem; }

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
}

/* 车辆 marker（与 Monitor.vue 同款式） */
:deep(.vehicle-marker) {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #16a34a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}
:deep(.vehicle-marker.alarm) { background: #dc2626; }
</style>
