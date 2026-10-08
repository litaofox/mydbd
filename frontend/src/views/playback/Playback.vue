<template>
  <div class="page">
    <!-- 查询条件 -->
    <div class="toolbar">
      <el-select v-model="identityCode" placeholder="选择车辆" style="width: 230px">
        <el-option
          v-for="v in vehicles"
          :key="v.identityCode"
          :label="`${v.plateNo}（${v.identityCode}）`"
          :value="v.identityCode"
        />
      </el-select>

      <el-date-picker v-model="start" type="datetime" placeholder="开始时间"
                      value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
      <span class="t-sep">至</span>
      <el-date-picker v-model="end" type="datetime" placeholder="结束时间"
                      value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
      <el-button type="primary" :loading="loading" @click="onQuery">
        <el-icon><Search /></el-icon>&nbsp;查询轨迹
      </el-button>
      <span v-if="track.length" class="q-summary">共 {{ track.length }} 个轨迹点 · 总里程 {{ totalMileage }} km</span>
    </div>

    <!-- 地图 -->
    <div ref="mapRef" class="map-box"></div>

    <!-- 回放控制条 -->
    <div class="playback-bar">
      <el-button circle :type="playing ? 'warning' : 'primary'" :disabled="track.length === 0" @click="togglePlay">
        {{ playing ? '❚❚' : '▶' }}
      </el-button>
      <el-button circle type="danger" :disabled="track.length === 0" @click="stopPlay">■</el-button>
      <el-select v-model="speed" style="width: 84px">
        <el-option v-for="s in speedOptions" :key="s" :value="s" :label="s + '×'" />
      </el-select>

      <el-slider
        v-model="index"
        :max="maxIndex"
        :disabled="track.length === 0"
        style="flex: 1; margin: 0 12px"
      />

      <div class="current">
        <div>{{ currentTime }}</div>
        <div>速度 {{ current ? current.speed + ' km/h' : '' }} · 里程 {{ currentMileage }} km</div>
      </div>
    </div>

    <!-- 底部查询结果面板 -->
    <div class="result-panel" :class="{ collapsed: panelCollapsed }">
      <div class="panel-head">
        <div
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ active: activeTab === t.key }"
          @click="pickTab(t.key)"
        >
          {{ t.name }} <span class="tab-n">({{ panel[t.key].total }})</span>
        </div>
        <div class="panel-tools">
          <el-checkbox v-model="matchLocation" size="small">匹配位置</el-checkbox>
          <el-button size="small" text :title="panelCollapsed ? '展开面板' : '折叠面板'" @click="togglePanel">
            {{ panelCollapsed ? '▴' : '−' }}
          </el-button>
        </div>
      </div>

      <div v-show="!panelCollapsed" class="panel-body">
        <!-- 轨迹 -->
        <el-table
          v-if="activeTab === 'track'"
          ref="trackTableRef"
          v-loading="panel.track.loading"
          :data="panel.track.rows"
          height="100%"
          size="small"
          row-key="id"
          :row-class-name="trackRowClass"
          @row-click="onTrackRowClick"
        >
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column label="车牌号" width="100">
            <template #default="{ row }"><span class="cell-plate">{{ row.plateNo }}</span></template>
          </el-table-column>
          <el-table-column label="定位时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.gpsTime) }}</template>
          </el-table-column>
          <el-table-column label="上点时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.receiveTime) }}</template>
          </el-table-column>
          <el-table-column label="速度(km/h)" width="80">
            <template #default="{ row }">{{ row.speed ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="里程(km)" width="90">
            <template #default="{ row }">{{ fmtNum(row.mileage, 2) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="72">
            <template #default="{ row }">
              <span class="state-tag" :class="'st-' + trackStatus(row)">{{ trackStatusText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="驾驶员" width="72">
            <template #default="{ row }">{{ row.driverName || '—' }}</template>
          </el-table-column>
          <el-table-column label="位置" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ locText('track', row.id, row) }}</template>
          </el-table-column>
          <template #empty>暂无数据</template>
        </el-table>

        <!-- 事件 -->
        <el-table
          v-if="activeTab === 'events'"
          ref="eventTableRef"
          v-loading="panel.events.loading"
          :data="panel.events.rows"
          height="100%"
          size="small"
          @row-click="onEventRowClick"
        >
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column label="车牌号" width="100">
            <template #default="{ row }"><span class="cell-plate">{{ row.plateNo }}</span></template>
          </el-table-column>
          <el-table-column label="事件时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.eventTime) }}</template>
          </el-table-column>
          <el-table-column label="事件类型" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.eventCode }}</template>
          </el-table-column>
          <el-table-column label="风险等级" width="72">
            <template #default="{ row }">
              <span class="level-tag" :class="'lv-' + row.riskLevel">{{ levelText(row.riskLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="速度(km/h)" width="80">
            <template #default="{ row }">{{ row.speed ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="位置" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ locText('events', row.id, row) }}</template>
          </el-table-column>
          <template #empty>暂无数据</template>
        </el-table>

        <!-- 停车 -->
        <el-table
          v-if="activeTab === 'stops'"
          ref="stopTableRef"
          v-loading="panel.stops.loading"
          :data="panel.stops.rows"
          height="100%"
          size="small"
          @row-click="onStopRowClick"
        >
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column label="车牌号" width="100">
            <template #default="{ row }"><span class="cell-plate">{{ row.plateNo }}</span></template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="150">
            <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="停车时长" width="100">
            <template #default="{ row }">{{ fmtDuration(row.durationSec) }}</template>
          </el-table-column>
          <el-table-column label="位置" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ locText('stops', row.startTime + row.plateNo, row) }}</template>
          </el-table-column>
          <template #empty>暂无数据</template>
        </el-table>
      </div>

      <div v-show="!panelCollapsed" class="panel-foot">
        <template v-if="panel[activeTab].loadingMore">加载中…</template>
        <template v-else-if="panel[activeTab].hasMore">
          已加载 {{ panel[activeTab].rows.length }} / {{ panel[activeTab].total }} 条 · 滚动到列表底部自动加载下一页（每页 {{ PAGE_SIZE }} 条）
        </template>
        <template v-else-if="panel[activeTab].rows.length > 0">已加载全部 {{ panel[activeTab].total }} 条</template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import dayjs from 'dayjs'
import {
  getTrack,
  getVehicles,
  getTrackPage,
  getEventsPage,
  getStopsPage,
  type GpsPoint,
  type VehicleOption,
  type TrackPointRow,
  type EventRow,
  type StopSeg,
  type PageData
} from '@/api/traj'
import { reverseGeocode } from '@/api/terminal'
import { vehicleIconSvg, VEHICLE_ICON_COLOR } from '@/config/vehicleIcons'

const route = useRoute()

const mapRef = ref<HTMLDivElement>()
const vehicles = ref<VehicleOption[]>([])
const identityCode = ref('')
const start = ref('')
const end = ref('')
const loading = ref(false)

const track = ref<GpsPoint[]>([])
const index = ref(0)
const speedOptions = [1, 4, 8, 16, 32]
const speed = ref(1)
const playing = ref(false)

let map: L.Map
let polyline: L.Polyline
let movingMarker: L.Marker
let playTimer: number
let lastDir = -999

const maxIndex = computed(() => Math.max(0, track.value.length - 1))

function makeIcon(dir: number): L.DivIcon {
  return L.divIcon({
    className: '',
    iconSize: [56, 56],
    iconAnchor: [28, 28],
    html: `<div class="vm-mk"><span class="vm-mk-rot" style="transform:rotate(${dir}deg)">${vehicleIconSvg(null, 32, VEHICLE_ICON_COLOR)}</span></div>`
  })
}
const current = computed(() => track.value[index.value])
// 轨迹接口部分链路返回 ISO 串，统一展示为 yyyy-MM-dd HH:mm:ss
const currentTime = computed(() => {
  const t = current.value?.gpsTime
  if (!t) return '--'
  const d = dayjs(t)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : t
})
const currentMileage = computed(() => (current.value ? fmtNum(current.value.mileage, 1) : '0'))
const totalMileage = computed(() => {
  let m = 0
  for (const p of track.value) m = Math.max(m, Number(p.mileage || 0))
  return m.toFixed(1)
})

function fmtNum(v: unknown, digits = 2): string {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n.toFixed(digits) : (0).toFixed(digits)
}

/** 统一时间格式化：ISO/字符串 → yyyy-MM-dd HH:mm:ss */
function fmtTime(v?: string | null): string {
  if (!v) return '—'
  const d = dayjs(v)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : v
}

// ========================= 底部结果面板 =========================
const PAGE_SIZE = 50
type TabKey = 'track' | 'events' | 'stops'
const tabs: Array<{ key: TabKey; name: string }> = [
  { key: 'track', name: '轨迹' },
  { key: 'events', name: '事件' },
  { key: 'stops', name: '停车' }
]
const activeTab = ref<TabKey>('track')
const panelCollapsed = ref(false)
const matchLocation = ref(false)

interface ListState<T> {
  rows: T[]
  page: number
  total: number
  hasMore: boolean
  loading: boolean
  loadingMore: boolean
}
function emptyList<T>(): ListState<T> {
  return { rows: [], page: 0, total: 0, hasMore: false, loading: false, loadingMore: false }
}
const panel = reactive<{
  track: ListState<TrackPointRow>
  events: ListState<EventRow>
  stops: ListState<StopSeg>
}>({
  track: emptyList(),
  events: emptyList(),
  stops: emptyList()
})

const trackTableRef = ref<any>()
const eventTableRef = ref<any>()
const stopTableRef = ref<any>()
const tableRefs = { track: trackTableRef, events: eventTableRef, stops: stopTableRef }

// ----- 滚动加载 -----
const boundEls = new Set<HTMLElement>()

/** el-table 实际产生滚动的元素（Element Plus 内部为 el-scrollbar，旧版回退 body-wrapper） */
function scrollElOf(key: TabKey): HTMLElement | null {
  const root = tableRefs[key].value?.$el as HTMLElement | undefined
  if (!root) return null
  return root.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')
    || root.querySelector('.el-table__body-wrapper')
}

function onTableScroll(ev: Event) {
  const el = ev.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 80) {
    void loadMore(activeTab.value)
  }
}

function bindScroll(key: TabKey) {
  const el = scrollElOf(key)
  if (!el || boundEls.has(el)) return
  el.addEventListener('scroll', onTableScroll)
  boundEls.add(el)
}

/** 首屏数据不足一屏时自动续载，直到撑满或无更多 */
function maybeFillViewport(key: TabKey) {
  const st = panel[key]
  if (!st.hasMore || st.loadingMore) return
  const el = scrollElOf(key)
  if (el && el.scrollHeight <= el.clientHeight + 4) void loadMore(key)
}

async function loadMore(key: TabKey) {
  const st = panel[key]
  if (!st.hasMore || st.loadingMore || st.loading) return
  st.loadingMore = true
  try {
    const nextPage = st.page + 1
    const params = {
      identityCode: identityCode.value,
      start: start.value || undefined,
      end: end.value || undefined,
      page: nextPage,
      size: PAGE_SIZE
    }
    const res: PageData<any> = key === 'track'
      ? await getTrackPage(params)
      : key === 'events'
        ? await getEventsPage(params)
        : await getStopsPage(params)
    st.rows.push(...res.records)
    st.page = nextPage
    st.total = Number(res.total)
    st.hasMore = st.rows.length < st.total
    if (matchLocation.value) resolveAddresses(key)
  } finally {
    st.loadingMore = false
    nextTick(() => maybeFillViewport(key))
  }
}

/** 查询后加载三个标签的第 1 页（并行；标签计数依赖 total） */
async function loadPanelFirstPage() {
  for (const k of tabs.map((t) => t.key)) Object.assign(panel[k], emptyList())
  const params = {
    identityCode: identityCode.value,
    start: start.value || undefined,
    end: end.value || undefined,
    page: 1,
    size: PAGE_SIZE
  }
  panel.track.loading = true
  panel.events.loading = true
  panel.stops.loading = true
  const apply = (key: TabKey, res: PageData<any>) => {
    const st = panel[key]
    st.rows = res.records
    st.page = 1
    st.total = Number(res.total)
    st.hasMore = st.rows.length < st.total
    st.loading = false
  }
  const results = await Promise.allSettled([
    getTrackPage(params),
    getEventsPage(params),
    getStopsPage(params)
  ])
  const keys: TabKey[] = ['track', 'events', 'stops']
  results.forEach((r, i) => {
    if (r.status === 'fulfilled') apply(keys[i], r.value)
    else panel[keys[i]].loading = false
  })
  if (matchLocation.value) resolveAddresses(activeTab.value)
  nextTick(() => {
    bindScroll(activeTab.value)
    maybeFillViewport(activeTab.value)
  })
}

function pickTab(key: TabKey) {
  activeTab.value = key
}

watch(activeTab, (key) => {
  nextTick(() => {
    bindScroll(key)
    maybeFillViewport(key)
    if (matchLocation.value) resolveAddresses(key)
  })
})

function togglePanel() {
  panelCollapsed.value = !panelCollapsed.value
  if (!panelCollapsed.value) setTimeout(() => map?.invalidateSize(), 220)
  else setTimeout(() => map?.invalidateSize(), 60)
}

// ----- 行展示派生 -----
function trackStatus(row: TrackPointRow): 'drive' | 'stop' | 'alarm' {
  return row.alarmFlag === 1 ? 'alarm' : (row.speed ?? 0) > 0 ? 'drive' : 'stop'
}
function trackStatusText(row: TrackPointRow): string {
  const s = trackStatus(row)
  return s === 'drive' ? '行驶' : s === 'alarm' ? '报警' : '停车'
}
function levelText(lv: number): string {
  return lv === 3 ? '高' : lv === 1 ? '低' : '中'
}
function fmtDuration(secs: number): string {
  const m = Math.floor(secs / 60)
  if (m < 60) return `${m} 分钟`
  return `${Math.floor(m / 60)} 小时 ${m % 60} 分`
}

// ----- 逆地理（匹配位置） -----
const addrMap = reactive(new Map<string, string>())
const addrPending = new Set<string>()

function coordKey(lng: number, lat: number): string {
  return `${Number(lng).toFixed(3)},${Number(lat).toFixed(3)}`
}
function locText(prefix: string, id: string | number, row: any): string {
  const key = `${prefix}:${id}`
  if (matchLocation.value) {
    const addr = addrMap.get(key)
    if (addr) return addr
  }
  return coordKey(row.lng, row.lat)
}

function resolveAddresses(key: TabKey) {
  const st = panel[key]
  for (const row of st.rows as Array<any>) {
    const id = key === 'stops' ? row.startTime + row.plateNo : row.id
    const mapKey = `${key}:${id}`
    if (addrMap.has(mapKey)) continue
    const ck = coordKey(row.lng, row.lat)
    if (addrPending.has(ck)) continue
    addrPending.add(ck)
    reverseGeocode(Number(row.lng), Number(row.lat))
      .then((r) => { if (r?.address) addrMap.set(mapKey, r.address) })
      .catch(() => { /* 静默：位置列回退坐标 */ })
      .finally(() => addrPending.delete(ck))
  }
}

watch(matchLocation, (on) => {
  if (on) nextTick(() => resolveAddresses(activeTab.value))
})

// ----- 地图联动 -----
function gotoPoint(p: { lng: number; lat: number; gpsTime?: string }) {
  if (!map) return
  map.setView([p.lat, p.lng], 14, { animate: true })
  movingMarker?.setLatLng([p.lat, p.lng])
  if (p.gpsTime) {
    const i = track.value.findIndex((x) => x.gpsTime === p.gpsTime)
    if (i >= 0) index.value = i
  }
}
function onTrackRowClick(row: TrackPointRow) {
  gotoPoint({ lng: Number(row.lng), lat: Number(row.lat), gpsTime: row.gpsTime })
}
function onEventRowClick(row: EventRow) {
  gotoPoint({ lng: Number(row.lng), lat: Number(row.lat), gpsTime: row.eventTime })
}
function onStopRowClick(row: StopSeg) {
  gotoPoint({ lng: Number(row.lng), lat: Number(row.lat), gpsTime: row.startTime })
}

/** 播放当前点对应行高亮样式 */
function trackRowClass({ row }: { row: TrackPointRow }) {
  return current.value && row.id === current.value.id ? 'pb-cur-row' : ''
}

/** 播放推进时，若当前点已加载则高亮行并跟随滚动（不触发翻页） */
function syncCurrentRowInView() {
  if (activeTab.value !== 'track' || !current.value || !trackTableRef.value) return
  const i = panel.track.rows.findIndex((r) => r.id === current.value!.id)
  if (i < 0) return
  const tbody = trackTableRef.value.$el.querySelector('.el-table__body tbody') as HTMLElement
  const tr = tbody?.children[i] as HTMLElement | undefined
  tr?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}

// ========================= 查询 / 绘制 / 播放 =========================
async function onQuery() {
  if (!identityCode.value) {
    ElMessage.warning('请先选择车辆')
    return
  }
  loading.value = true
  playing.value = false
  try {
    const data = await getTrack({
      identityCode: identityCode.value,
      start: start.value || undefined,
      end: end.value || undefined
    })
    track.value = data
    index.value = 0
    drawTrack()
    await loadPanelFirstPage()
    ElMessage.success(`共 ${data.length} 个轨迹点`)
  } finally {
    loading.value = false
  }
}

function drawTrack() {
  if (polyline) map.removeLayer(polyline)
  if (movingMarker) map.removeLayer(movingMarker)

  const latLngs = track.value.map((p) => [p.lat, p.lng] as [number, number])
  if (latLngs.length === 0) {
    ElMessage.info('该条件下无轨迹数据')
    return
  }
  polyline = L.polyline(latLngs, { color: '#2563eb', weight: 4, opacity: 0.8 }).addTo(map)
  movingMarker = L.marker(latLngs[0], { icon: makeIcon(0) }).addTo(map)
  lastDir = 0
  map.fitBounds(polyline.getBounds().pad(0.1))
}

function moveMarker() {
  const p = current.value
  if (!movingMarker || !p) return
  movingMarker.setLatLng([p.lat, p.lng])
  const dir = p.direction ?? 0
  if (Math.abs(dir - lastDir) > 15) {
    movingMarker.setIcon(makeIcon(dir))
    lastDir = dir
  }
}

function togglePlay() {
  playing.value = !playing.value
}

function stopPlay() {
  playing.value = false
  index.value = 0
}

watch(index, () => {
  moveMarker()
  syncCurrentRowInView()
})

watch(playing, (val) => {
  if (val) {
    playTimer = window.setInterval(() => {
      if (index.value >= maxIndex.value) {
        playing.value = false
        return
      }
      index.value += 1
    }, 600 / speed.value)
  } else if (playTimer) {
    window.clearInterval(playTimer)
  }
})

watch(speed, () => {
  if (playing.value) {
    playing.value = false
    playing.value = true
  }
})

onMounted(async () => {
  map = L.map(mapRef.value!).setView([39.91, 116.4], 11)
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18, attribution: '&copy; 高德地图' }
  ).addTo(map)

  vehicles.value = await getVehicles()
  applyRouteQuery()
})

/**
 * 应用路由参数（支持从实时监控/报警中心等页带参跳入）：
 * - plateNo / identityCode：自动选中车辆（identityCode 精确匹配优先，车牌兜底）
 * - date=YYYY-MM-DD：时间窗自动填当天 00:00:00 ~ 23:59:59 并查询
 * - 带车参但无 date：沿用本页"不填时间 = 查询全部轨迹"的既有语义
 * - 无任何参数：保持打开本页的初始状态，不自动查询
 */
function applyRouteQuery() {
  const qIdentity = typeof route.query.identityCode === 'string' ? route.query.identityCode : ''
  const qPlate = typeof route.query.plateNo === 'string' ? route.query.plateNo : ''
  const qDate = typeof route.query.date === 'string' ? route.query.date : ''

  const matched =
    vehicles.value.find((v) => v.identityCode === qIdentity) ||
    vehicles.value.find((v) => v.plateNo === qPlate)
  if (matched) {
    identityCode.value = matched.identityCode
  } else if (qIdentity) {
    identityCode.value = qIdentity
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(qDate)) {
    start.value = `${qDate} 00:00:00`
    end.value = `${qDate} 23:59:59`
  } else if (qIdentity || qPlate) {
    start.value = ''
    end.value = ''
  }

  if (identityCode.value) onQuery()
}

// 同标签换车：path 不变仅 query 变化时（多标签模式切换车辆）重新应用参数
watch(() => route.query, applyRouteQuery)

onBeforeUnmount(() => {
  if (playTimer) window.clearInterval(playTimer)
  for (const el of boundEls) el.removeEventListener('scroll', onTableScroll)
  boundEls.clear()
  if (map) map.remove()
})
</script>

<style scoped>
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 查询条件 */
.toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.t-sep { color: #909399; font-size: 13px; }
.q-summary { margin-left: 8px; font-size: 13px; color: #909399; }

/* 地图 */
.map-box {
  flex: 1 1 auto;
  min-height: 220px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

/* 播放控制条 */
.playback-bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}
.current {
  width: 210px;
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  text-align: right;
}

/* 底部结果面板 */
.result-panel {
  flex: 0 0 auto;
  height: 200px;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.result-panel.collapsed { height: 41px; }
.panel-head {
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border-bottom: 1px solid #ebeef5;
}
.tab {
  height: 40px;
  line-height: 40px;
  margin-right: 28px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  position: relative;
}
.tab.active { color: #409eff; font-weight: 600; }
.tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: #409eff;
}
.tab-n { font-weight: 400; font-size: 13px; }
.panel-tools { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.panel-body { flex: 1 1 auto; min-height: 0; height: 0; overflow: hidden; padding: 0; }
.panel-foot {
  flex: 0 0 28px;
  line-height: 28px;
  padding: 0 14px;
  font-size: 12px;
  color: #909399;
  background: #fafcff;
  border-top: 1px solid #f0f2f5;
}

.cell-plate { color: #303133; font-weight: 500; }

/* 状态标签 */
.state-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 3px;
  font-size: 12px;
  line-height: 1.6;
}
.st-drive { color: #409eff; background: #ecf5ff; border: 1px solid #d9ecff; }
.st-stop  { color: #909399; background: #f4f4f5; border: 1px solid #e9e9eb; }
.st-alarm { color: #f56c6c; background: #fef0f0; border: 1px solid #fbc4c4; }

/* 风险等级 */
.level-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 3px;
  font-size: 12px;
  line-height: 1.6;
}
.lv-1 { color: #67c23a; background: #f0f9eb; border: 1px solid #e1f3d8; }
.lv-2 { color: #e6a23c; background: #fdf6ec; border: 1px solid #faecd8; }
.lv-3 { color: #f56c6c; background: #fef0f0; border: 1px solid #fbc4c4; }

/* 播放当前行高亮 */
:deep(.pb-cur-row) { background: #ecf5ff !important; }
:deep(.pb-cur-row td:first-child) { box-shadow: inset 3px 0 0 #409eff; }
</style>
