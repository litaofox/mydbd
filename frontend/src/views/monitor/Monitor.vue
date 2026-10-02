<template>
  <div class="page">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-button type="primary" :loading="sampleLoading" @click="onLoadSample">
        加载样例数据
      </el-button>
      <el-button type="success" @click="onSimStart">启动模拟器</el-button>
      <el-button type="danger" plain @click="onSimStop">停止模拟器</el-button>
      <el-tag v-if="sim" :type="sim.running ? 'success' : 'info'">
        模拟器：{{ sim.running ? `运行中（${sim.ticks} ticks）` : '已停止' }}
      </el-tag>
      <el-button link @click="refresh">刷新</el-button>
    </div>

    <!-- 总览指标 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value">{{ overview.activeVehicles ?? '-' }}</div>
          <div class="label">在途车辆</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value">{{ overview.todayRisks ?? '-' }}</div>
          <div class="label">今日风险事件</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value">{{ overview.pendingRisks ?? '-' }}</div>
          <div class="label">待处置风险</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value">{{ overview.todayWarnings ?? '-' }}</div>
          <div class="label">今日终端报警</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 地图 + 车辆列表 -->
    <el-row :gutter="12">
      <el-col :span="17">
        <el-card>
          <div ref="mapRef" class="map-box"></div>
        </el-card>
      </el-col>
      <el-col :span="7">
        <el-card>
          <template #header>车辆实时状态</template>
          <el-table :data="points" height="520" size="small">
            <el-table-column prop="plateNo" label="车牌" width="92" />
            <el-table-column prop="speed" label="速度" width="64">
              <template #default="{ row }">{{ row.speed }} km/h</template>
            </el-table-column>
            <el-table-column prop="direction" label="方向" width="58">
              <template #default="{ row }">{{ row.direction }}°</template>
            </el-table-column>
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag v-if="row.alarmFlag === 1" type="danger">报警</el-tag>
                <el-tag v-else type="success">正常</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
  getLatestPoints,
  loadSample,
  simulatorStart,
  simulatorStatus,
  simulatorStop,
  type GpsPoint
} from '@/api/traj'
import { getOverview, type Overview } from '@/api/monitor'

const mapRef = ref<HTMLDivElement>()
const points = ref<GpsPoint[]>([])
const overview = ref<Partial<Overview>>({})
const sampleLoading = ref(false)
const sim = ref<{ running: boolean; ticks: number }>()

let map: L.Map
let markerLayer: L.LayerGroup
let timer: number

function makeIcon(point: GpsPoint): L.DivIcon {
  return L.divIcon({
    className: '',
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    html: `<div class="vehicle-marker ${point.alarmFlag === 1 ? 'alarm' : ''}">
             <span style="display:inline-block;transform:rotate(${point.direction ?? 0}deg)">&#10148;</span>
           </div>`
  })
}

function renderMarkers() {
  markerLayer.clearLayers()
  const latLngs: L.LatLngExpression[] = []
  for (const p of points.value) {
    const marker = L.marker([p.lat, p.lng], { icon: makeIcon(p) })
      .bindTooltip(`${p.plateNo}　${p.speed} km/h`, { direction: 'top' })
    markerLayer.addLayer(marker)
    latLngs.push([p.lat, p.lng])
  }
  if (latLngs.length > 0) {
    map.fitBounds(L.latLngBounds(latLngs).pad(0.2))
  }
}

async function refresh() {
  const [ov, latest] = await Promise.all([getOverview(), getLatestPoints()])
  overview.value = ov
  points.value = latest
  if (map) {
    renderMarkers()
  }
}

async function refreshSimStatus() {
  sim.value = await simulatorStatus()
}

async function onLoadSample() {
  sampleLoading.value = true
  try {
    const result = await loadSample()
    ElMessage.success(
      `导入完成：${result.insertedPoints} 个轨迹点，${result.insertedEvents} 个风险事件`
    )
    await refresh()
  } finally {
    sampleLoading.value = false
  }
}

async function onSimStart() {
  await simulatorStart()
  ElMessage.success('模拟器已启动')
  await refreshSimStatus()
}

async function onSimStop() {
  await simulatorStop()
  ElMessage.success('模拟器已停止')
  await refreshSimStatus()
}

onMounted(async () => {
  map = L.map(mapRef.value!, { zoomControl: true }).setView([39.91, 116.4], 11)
  // 高德栅格瓦片（免费、无需 Key）
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18, attribution: '&copy; 高德地图' }
  ).addTo(map)
  markerLayer = L.layerGroup().addTo(map)

  await Promise.all([refresh(), refreshSimStatus()])
  timer = window.setInterval(refresh, 5000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  if (map) map.remove()
})
</script>
