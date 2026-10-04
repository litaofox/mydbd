<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    :with-header="false"
    :size="drawerSize"
    append-to-body
    @update:model-value="close"
    @opened="onOpened"
  >
    <div class="panel-wrap">
      <!-- loading -->
      <el-skeleton v-if="state === 'loading'" :rows="8" animated class="panel-skeleton" />

      <!-- error -->
      <div v-else-if="state === 'error'" class="panel-error">
        <el-empty :description="errorMsg" />
        <el-button @click="close()">关闭</el-button>
      </div>

      <!-- ready -->
      <template v-else-if="state === 'ready' && panel">
        <!-- 头部：车牌 + 颜色 + 在线状态 + 实时位置卡 -->
        <div class="p-head">
          <div class="head-row">
            <span class="plate">{{ panel.vehicle.vehicleNo }}</span>
            <el-tag v-if="panel.vehicle.vehiclePlateColor" size="small" effect="plain">
              {{ panel.vehicle.vehiclePlateColor }}
            </el-tag>
            <el-tag :type="online ? 'success' : 'info'" size="small">{{ online ? '在线' : '离线' }}</el-tag>
            <span class="spacer"></span>
            <el-button link @click="load()">刷新</el-button>
            <el-button link @click="close()">✕</el-button>
          </div>
          <div v-if="posPoint" class="pos-card">
            <div class="pos-item"><b>{{ posPoint.speed ?? '--' }}</b><span>km/h</span></div>
            <div class="pos-item"><b>{{ posPoint.direction ?? '--' }}</b><span>方向°</span></div>
            <div class="pos-item wide">
              <b>{{ fmt(posPoint.gpsTime) }}</b><span>定位时间</span>
            </div>
          </div>
          <div v-else class="pos-card empty">暂无定位数据</div>
        </div>

        <!-- 车辆档案 -->
        <div class="p-sec">
          <div class="sec-title">
            <span>车辆档案</span>
            <el-link type="primary" :underline="false" @click="goArchive">前往车辆档案</el-link>
          </div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="车型">{{ panel.vehicle.vehicleType || '--' }}</el-descriptions-item>
            <el-descriptions-item label="品牌">{{ panel.vehicle.vehicleBrand || '--' }}</el-descriptions-item>
            <el-descriptions-item label="VIN">{{ panel.vehicle.vin || '--' }}</el-descriptions-item>
            <el-descriptions-item label="运营类型">
              {{ labelOf(OPERATION_TYPES, panel.vehicle.operationType) }}
            </el-descriptions-item>
            <el-descriptions-item label="所属组织" :span="2">{{ panel.vehicle.deptName || '--' }}</el-descriptions-item>
            <el-descriptions-item label="车主">{{ panel.vehicle.ownerName || '--' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ panel.vehicle.ownerPhone || '--' }}</el-descriptions-item>
            <el-descriptions-item label="运输证号" :span="2">{{ panel.vehicle.roadLicenseNo || '--' }}</el-descriptions-item>
            <el-descriptions-item v-if="panel.vehicle.remark" label="备注" :span="2">
              {{ panel.vehicle.remark }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 终端与司机 -->
        <div class="p-sec">
          <div class="sec-title"><span>终端与司机</span></div>
          <template v-if="panel.terminal">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="终端识别码">{{ panel.terminal.identityCode }}</el-descriptions-item>
              <el-descriptions-item label="型号">{{ panel.terminal.tlModel || '--' }}</el-descriptions-item>
              <el-descriptions-item label="SIM 卡号">{{ panel.terminal.simAccount || '--' }}</el-descriptions-item>
              <el-descriptions-item label="协议">{{ panel.terminal.protocolType || '--' }}</el-descriptions-item>
              <el-descriptions-item label="设备类型">
                {{ labelOf(EQUIPMENT_TYPES, panel.terminal.equipmentType) }}
              </el-descriptions-item>
              <el-descriptions-item label="视频通道">{{ panel.terminal.videoChannel ?? 0 }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <el-empty v-else description="未绑定终端" :image-size="48" />
          <div v-if="panel.drivers.length" class="driver-list">
            <div v-for="d in panel.drivers" :key="d.id" class="driver-row">
              <el-tag :type="tagOf(DRIVER_TYPES, d.driverType)" size="small">
                {{ labelOf(DRIVER_TYPES, d.driverType) }}
              </el-tag>
              <span class="d-name">{{ d.driverName }}</span>
              <span class="d-meta">
                {{ labelOf(DRIVER_SEX, d.sex) }} · {{ d.licenceCategory || '--' }} · {{ d.contactPhone || '--' }}
              </span>
            </div>
          </div>
          <div v-else class="driver-empty">当前无在班司机</div>
        </div>

        <!-- 当日轨迹 -->
        <div class="p-sec">
          <div class="sec-title">
            <span>当日轨迹（{{ panel.todayTrack.date }}）</span>
            <span class="track-stat">
              {{ panel.todayTrack.totalPoints }} 个里程点
              <template v-if="panel.todayTrack.sampled">，抽稀展示 {{ panel.todayTrack.points.length }} 点</template>
            </span>
          </div>
          <template v-if="panel.todayTrack.points.length">
            <div ref="trackMapRef" class="track-map"></div>
          </template>
          <div v-else class="track-empty">未绑定终端，暂无定位数据</div>
        </div>

        <!-- 最新报警 + 计数 -->
        <div class="p-sec">
          <div class="sec-title">
            <span>最新报警</span>
            <el-link type="primary" :underline="false" @click="goAlarms">查看全部报警</el-link>
          </div>
          <el-row :gutter="8" class="count-row">
            <el-col :span="12">
              <div class="count-card">
                <div class="num">{{ panel.todayAlarmCount }}</div>
                <div class="cap">今日终端报警</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="count-card">
                <div class="num">{{ panel.todayRiskCount }}</div>
                <div class="cap">今日风险事件</div>
              </div>
            </el-col>
          </el-row>
          <div v-if="panel.latestAlarms.length" class="alarm-list">
            <div v-for="a in panel.latestAlarms" :key="a.id" class="alarm-row">
              <span class="a-type">{{ a.typeName || '--' }}</span>
              <span class="a-time">{{ fmt(a.startWarnTime) }}</span>
              <el-tag :type="handleTag(a.handleStatus)" size="small">{{ handleText(a.handleStatus) }}</el-tag>
            </div>
          </div>
          <div v-else class="alarm-empty">当日暂无终端报警</div>
        </div>

        <!-- 底部操作 -->
        <div class="p-foot">
          <el-button type="primary" @click="goMonitor">实时监控</el-button>
          <el-button @click="goPlayback" :disabled="!panel.terminal">轨迹回放</el-button>
          <el-tooltip
            :disabled="hasVideo"
            content="该终端无视频通道"
            placement="top"
          >
            <span>
              <el-button type="primary" plain :disabled="!hasVideo" @click="onVideo">视频调阅</el-button>
            </span>
          </el-tooltip>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getVehiclePanel, type VehiclePanel } from '@/api/monitor'
import type { GpsPoint } from '@/api/traj'
import {
  OPERATION_TYPES,
  DRIVER_SEX,
  DRIVER_TYPES,
  EQUIPMENT_TYPES,
  labelOf,
  tagOf
} from '@/constants/dict'

const props = defineProps<{
  modelValue: boolean
  vehicleId: string | null
  livePoint?: GpsPoint | null
  /** 页面统一口径的在线状态；不传时回退为按定位时间 5 分钟判断 */
  online?: boolean
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const router = useRouter()

const state = ref<'loading' | 'ready' | 'error'>('loading')
const errorMsg = ref('')
const panel = ref<VehiclePanel | null>(null)
const trackMapRef = ref<HTMLDivElement>()
const loadedId = ref<string | null>(null)

/** 桌面 560px；窄视口全屏（E2E 避坑：456×304 下固定宽度会 offscreen） */
const drawerSize = ref(window.innerWidth < 640 ? '100%' : '560px')
function onResize() {
  drawerSize.value = window.innerWidth < 640 ? '100%' : '560px'
}
window.addEventListener('resize', onResize)

/** livePoint（WS 帧）优先，否则聚合接口快照 */
const posPoint = computed(() => {
  if (props.livePoint) {
    return {
      lng: props.livePoint.lng,
      lat: props.livePoint.lat,
      speed: props.livePoint.speed,
      direction: props.livePoint.direction,
      gpsTime: props.livePoint.gpsTime
    }
  }
  const p = panel.value?.latestPoint
  return p ?? null
})

/** 页面已给出统一口径在线状态时直接使用（数据时基可能滞后于墙钟） */
const localOnline = computed(() => {
  const p = posPoint.value
  if (!p) return false
  const t = new Date(String(p.gpsTime).replace('T', ' ').replace(/-/g, '/')).getTime()
  return Date.now() - t <= 5 * 60 * 1000
})
const online = computed(() => props.online ?? localOnline.value)

const hasVideo = computed(
  () => !!panel.value?.terminal && (panel.value.terminal.videoChannel ?? 0) > 0
)

function fmt(v: string | null | undefined): string {
  if (!v) return '--'
  return v.replace('T', ' ').slice(0, 19)
}

function handleText(s: number): string {
  return s === 1 ? '已确认' : s === 2 ? '已解除' : '待处理'
}
function handleTag(s: number): 'danger' | 'warning' | 'success' {
  return s === 1 ? 'warning' : s === 2 ? 'success' : 'danger'
}

function close() {
  emit('update:modelValue', false)
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  destroyMap()
})

async function load() {
  if (!props.vehicleId) return
  state.value = 'loading'
  errorMsg.value = ''
  try {
    panel.value = await getVehiclePanel(props.vehicleId)
    loadedId.value = props.vehicleId
    state.value = 'ready'
    await nextTick()
    renderTrackMap()
  } catch (err) {
    errorMsg.value = (err as Error).message || '加载失败'
    state.value = 'error'
  }
}

watch(
  () => [props.modelValue, props.vehicleId],
  ([visible, id]) => {
    if (visible && id && id !== loadedId.value) {
      load()
    }
    if (!visible) {
      destroyMap()
    }
  }
)

/** 抽屉打开动画完成后修正尺寸（Leaflet 经典坑：动画中容器宽 0） */
function onOpened() {
  if (map && panel.value) {
    map.invalidateSize()
  } else {
    renderTrackMap()
  }
}

// ===== mini Leaflet =====
let map: L.Map | null = null
let liveMarker: L.Marker | null = null

function destroyMap() {
  if (map) {
    map.remove()
    map = null
    liveMarker = null
  }
}

function renderTrackMap() {
  destroyMap()
  const pts = panel.value?.todayTrack.points ?? []
  if (!trackMapRef.value || pts.length === 0) return
  map = L.map(trackMapRef.value, {
    zoomControl: false,
    scrollWheelZoom: false,
    attributionControl: false
  })
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18 }
  ).addTo(map)
  const latLngs = pts.map((p) => [p.lat, p.lng] as [number, number])
  L.polyline(latLngs, { color: '#1f5fbf', weight: 3 }).addTo(map)
  const endIcon = (color: string) =>
    L.divIcon({
      className: '',
      iconSize: [12, 12],
      iconAnchor: [6, 6],
      html: `<div style="width:12px;height:12px;border-radius:50%;background:${color};border:2px solid #fff;box-shadow:0 0 4px rgba(0,0,0,.4)"></div>`
    })
  L.marker(latLngs[0], { icon: endIcon('#15803d') }).addTo(map)
  L.marker(latLngs[latLngs.length - 1], { icon: endIcon('#b91c1c') }).addTo(map)
  syncLiveMarker()
  map.fitBounds(L.latLngBounds(latLngs).pad(0.1))
}

function syncLiveMarker() {
  if (!map || !posPoint.value) return
  const ll = [posPoint.value.lat, posPoint.value.lng] as [number, number]
  if (!liveMarker) {
    liveMarker = L.marker(ll, {
      icon: L.divIcon({
        className: '',
        iconSize: [14, 14],
        iconAnchor: [7, 7],
        html: '<div style="width:14px;height:14px;border-radius:50%;background:#1f5fbf;border:3px solid #fff;box-shadow:0 0 6px rgba(0,0,0,.5)"></div>'
      })
    }).addTo(map)
  } else {
    liveMarker.setLatLng(ll)
  }
}

watch(posPoint, () => {
  if (map) syncLiveMarker()
})

// ===== 跳转与占位 =====
function goArchive() {
  router.push(`/mdm/vehicles?keyword=${encodeURIComponent(panel.value?.vehicle.vehicleNo ?? '')}`)
}
function goAlarms() {
  router.push(`/alarms?plateNo=${encodeURIComponent(panel.value?.vehicle.vehicleNo ?? '')}`)
}
function goMonitor() {
  // 监控页支持 ?plate= 自动定位 + 打开气泡
  router.push(`/monitor?plate=${encodeURIComponent(panel.value?.vehicle.vehicleNo ?? '')}`)
}
function goPlayback() {
  // 同时携带车牌与终端识别码：select 显示车牌，查询走识别码
  const plate = panel.value?.vehicle.vehicleNo ?? ''
  const identity = panel.value?.terminal?.identityCode ?? ''
  router.push(`/playback?plateNo=${encodeURIComponent(plate)}&identityCode=${encodeURIComponent(identity)}`)
}
function onVideo() {
  ElMessage.info('视频调阅建设中，将随 F25（第三波）上线')
}
</script>

<style scoped>
.panel-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 2px 16px;
}
.panel-skeleton {
  padding: 12px 4px;
}
.panel-error {
  padding: 40px 0;
  text-align: center;
}
.p-head {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--el-bg-color);
  padding: 4px 0 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.head-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plate {
  font-size: 20px;
  font-weight: 700;
  color: #16345f;
}
.spacer {
  flex: 1;
}
.pos-card {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.pos-card.empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.pos-item {
  flex: 1;
  background: #eef4ff;
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pos-item.wide {
  flex: 1.6;
}
.pos-item b {
  font-size: 15px;
  color: #174a96;
}
.pos-item span {
  font-size: 12px;
  color: #667085;
  margin-top: 2px;
}
.p-sec {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
}
.sec-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  color: #28456e;
  margin-bottom: 10px;
}
.track-stat {
  font-weight: 400;
  font-size: 12px;
  color: #667085;
}
.track-map {
  height: 220px;
  border-radius: 8px;
  overflow: hidden;
}
.track-empty,
.alarm-empty,
.driver-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 12px 0;
  text-align: center;
}
.count-row {
  margin-bottom: 10px;
}
.count-card {
  background: #f7f9fd;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  text-align: center;
  padding: 8px 0;
}
.count-card .num {
  font-size: 20px;
  font-weight: 700;
  color: #174a96;
}
.count-card .cap {
  font-size: 12px;
  color: #667085;
}
.alarm-list {
  display: flex;
  flex-direction: column;
}
.alarm-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  font-size: 13px;
}
.alarm-row:last-child {
  border-bottom: none;
}
.a-type {
  font-weight: 600;
  color: #1f2733;
}
.a-time {
  flex: 1;
  color: #667085;
}
.driver-list {
  margin-top: 10px;
}
.driver-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  font-size: 13px;
}
.d-name {
  font-weight: 600;
}
.d-meta {
  color: #667085;
}
.p-foot {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 4px;
}
</style>
