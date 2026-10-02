<template>
  <div class="page">
    <el-card>
      <!-- 查询条件 -->
      <div class="toolbar" style="margin-bottom: 16px">
        <el-select v-model="identityCode" placeholder="选择车辆" style="width: 220px">
          <el-option
            v-for="v in vehicles"
            :key="v.identityCode"
            :label="`${v.plateNo}（${v.identityCode}）`"
            :value="v.identityCode"
          />
        </el-select>

        <el-date-picker v-model="start" type="datetime" placeholder="开始时间"
                        value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
        <el-date-picker v-model="end" type="datetime" placeholder="结束时间"
                        value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
        <el-button type="primary" :loading="loading" @click="onQuery">查询轨迹</el-button>
      </div>

      <!-- 地图 -->
      <div ref="mapRef" class="map-box"></div>

      <!-- 回放控制条 -->
      <div class="playback-bar">
        <el-button :type="playing ? 'warning' : 'primary'" :disabled="track.length === 0" @click="togglePlay">
          {{ playing ? '暂停' : '播放' }}
        </el-button>
        <el-select v-model="speed" style="width: 90px">
          <el-option :value="1" label="1x" />
          <el-option :value="2" label="2x" />
          <el-option :value="4" label="4x" />
          <el-option :value="8" label="8x" />
        </el-select>

        <el-slider
          v-model="index"
          :max="maxIndex"
          :disabled="track.length === 0"
          style="flex: 1; margin: 0 12px"
        />

        <div class="current">
          <div>{{ current ? current.gpsTime : '--' }}</div>
          <div>{{ current ? current.speed + ' km/h' : '' }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getTrack, getVehicles, type GpsPoint, type VehicleOption } from '@/api/traj'

const mapRef = ref<HTMLDivElement>()
const vehicles = ref<VehicleOption[]>([])
const identityCode = ref('')
const start = ref('')
const end = ref('')
const loading = ref(false)

const track = ref<GpsPoint[]>([])
const index = ref(0)
const speed = ref(1)
const playing = ref(false)

let map: L.Map
let polyline: L.Polyline
let movingMarker: L.Marker
let playTimer: number

const maxIndex = computed(() => Math.max(0, track.value.length - 1))
const current = computed(() => track.value[index.value])

const playbackIcon = L.divIcon({
  className: '',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
  html: '<div class="playback-marker"></div>'
})

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
  movingMarker = L.marker(latLngs[0], { icon: playbackIcon }).addTo(map)
  map.fitBounds(polyline.getBounds().pad(0.1))
}

function moveMarker() {
  const p = current.value
  if (movingMarker && p) {
    movingMarker.setLatLng([p.lat, p.lng])
  }
}

function togglePlay() {
  playing.value = !playing.value
}

watch(index, moveMarker)

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
})

onBeforeUnmount(() => {
  if (playTimer) window.clearInterval(playTimer)
  if (map) map.remove()
})
</script>

<style scoped>
.playback-bar {
  display: flex;
  align-items: center;
  margin-top: 14px;
}

.current {
  width: 170px;
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
}
</style>
