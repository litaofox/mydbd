<template>
  <div class="mdm-page">
    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.fenceName" placeholder="围栏名称" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="filters.fenceType" placeholder="围栏形状" clearable style="width: 130px">
          <el-option value="CIRCLE" label="圆形" />
          <el-option value="POLYGON" label="多边形" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'risk:fence:edit'" type="success" @click="openCreate('CIRCLE')">新建圆形围栏</el-button>
        <el-button v-perm="'risk:fence:edit'" type="success" plain @click="openCreate('POLYGON')">新建多边形围栏</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="fenceName" label="围栏名称" min-width="180" />
        <el-table-column label="形状" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.fenceType === 'CIRCLE' ? 'success' : 'primary'">
              {{ row.fenceType === 'CIRCLE' ? '圆形' : '多边形' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="范围" min-width="260">
          <template #default="{ row }">
            <span class="muted">{{ shapeText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="触发方向" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dirTag(row.triggerDir)">{{ dirText(row.triggerDir) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="72">
          <template #default="{ row }">
            <el-tag size="small" :type="levelType(row.riskLevel)">{{ levelText(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="冷却" width="90">
          <template #default="{ row }">{{ cooldownText(row.cooldownSec) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="76">
          <template #default="{ row }">
            <el-switch
              v-perm="'risk:fence:edit'"
              :model-value="row.status === 1"
              @change="(v: boolean) => onToggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'risk:fence:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'risk:fence:edit'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="filters.size"
        :current-page="filters.page"
        @current-change="(p: number) => { filters.page = p; load() }"
        @size-change="(s: number) => { filters.size = s; filters.page = 1; load() }"
      />
    </el-card>

    <!-- 新增/编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑电子围栏' : '新建电子围栏'" size="720px"
               @opened="onDrawerOpened">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="围栏名称" prop="fenceName">
          <el-input v-model="form.fenceName" maxlength="50" />
        </el-form-item>
        <el-form-item label="围栏形状">
          <el-radio-group v-model="form.fenceType" :disabled="!!form.id" @change="onTypeChange">
            <el-radio value="CIRCLE">圆形</el-radio>
            <el-radio value="POLYGON">多边形</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 圆形参数 -->
        <template v-if="form.fenceType === 'CIRCLE'">
          <el-form-item label="中心点">
            <el-input-number v-model="form.centerLng" :precision="6" :step="0.001" :controls="false"
                             placeholder="经度" style="width: 170px" />
            <el-input-number v-model="form.centerLat" :precision="6" :step="0.001" :controls="false"
                             placeholder="纬度" style="width: 170px; margin-left: 8px" />
            <el-button link size="small" style="margin-left: 8px"
                       :type="pickingCenter ? 'warning' : 'primary'" @click="togglePickCenter">
              {{ pickingCenter ? '拾取中…点击地图选点' : '地图选点' }}
            </el-button>
          </el-form-item>
          <el-form-item label="半径">
            <el-input-number v-model="form.radiusM" :min="50" :max="100000" :step="100" />
            <span class="muted" style="margin-left: 8px">米（50~100000）</span>
          </el-form-item>
        </template>

        <!-- 多边形工具条 -->
        <template v-else>
          <el-form-item label="顶点绘制">
            <el-button size="small" :type="drawing ? 'danger' : 'primary'" @click="toggleDrawing">
              {{ drawing ? '结束添加' : '开始绘制' }}
            </el-button>
            <el-button size="small" :disabled="!vertices.length" @click="undoVertex">撤销一点</el-button>
            <el-button size="small" :disabled="!vertices.length" @click="clearVertices">清空重画</el-button>
            <el-tag size="small" type="info" style="margin-left: 8px">{{ vertices.length }} 个顶点</el-tag>
          </el-form-item>
          <el-form-item v-if="!vertices.length">
            <el-alert type="info" :closable="false" show-icon
                      title="点击「开始绘制」后在地图上依次点击选取顶点（至少 3 个），系统自动闭合。" />
          </el-form-item>
        </template>

        <el-form-item label="触发方向" prop="triggerDir">
          <el-radio-group v-model="form.triggerDir">
            <el-radio :value="1">进入报警</el-radio>
            <el-radio :value="2">离开报警</el-radio>
            <el-radio :value="3">进出都报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="form.riskLevel">
            <el-radio :value="1">低风险</el-radio>
            <el-radio :value="2">中风险</el-radio>
            <el-radio :value="3">高风险</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="冷却时间" prop="cooldownSec">
          <el-input-number v-model="form.cooldownSec" :min="0" :max="86400" :step="60" />
          <span class="muted" style="margin-left: 8px">秒，同车同围栏冷却期内不重复告警</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>

      <!-- 绘制地图 -->
      <div ref="mapRef" class="draw-map"></div>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
  pageFences,
  getFence,
  createFence,
  updateFence,
  changeFenceStatus,
  deleteFence,
  type GeoFence,
  type LngLat
} from '@/api/risk'

const loading = ref(false)
const records = ref<GeoFence[]>([])
const total = ref(0)
const filters = reactive({
  page: 1,
  size: 10,
  fenceName: '',
  fenceType: '',
  status: undefined as number | undefined
})

async function load() {
  loading.value = true
  try {
    const result = await pageFences({
      page: filters.page,
      size: filters.size,
      fenceName: filters.fenceName || undefined,
      fenceType: filters.fenceType || undefined,
      status: filters.status
    })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function resetFilters() {
  filters.fenceName = ''
  filters.fenceType = ''
  filters.status = undefined
  filters.page = 1
  load()
}

// ---- 抽屉与表单 ----
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const mapRef = ref<HTMLDivElement>()

const form = reactive({
  id: null as number | null,
  fenceName: '',
  fenceType: 'CIRCLE',
  centerLng: undefined as number | undefined,
  centerLat: undefined as number | undefined,
  radiusM: 1000,
  triggerDir: 3,
  riskLevel: 2,
  cooldownSec: 600,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  fenceName: [{ required: true, message: '请填写围栏名称', trigger: 'blur' }],
  triggerDir: [{ required: true, message: '请选择触发方向', trigger: 'change' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
  cooldownSec: [{ required: true, message: '请填写冷却时间', trigger: 'blur' }]
}

// ---- 地图与绘制 ----
let map: L.Map | null = null
let drawLayer: L.LayerGroup
const vertices = ref<L.LatLng[]>([])
const drawing = ref(false)
const pickingCenter = ref(false)
const DEFAULT_CENTER: L.LatLngTuple = [39.912, 116.404]

function ensureMap() {
  if (map || !mapRef.value) return
  map = L.map(mapRef.value, { zoomControl: true }).setView(DEFAULT_CENTER, 12)
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18, attribution: '高德地图' }
  ).addTo(map)
  drawLayer = L.layerGroup().addTo(map)
  map.on('click', onMapClick)
}

function onDrawerOpened() {
  ensureMap()
  map?.invalidateSize()
  renderPreview()
  fitToShape()
}

function onMapClick(e: L.LeafletMouseEvent) {
  if (form.fenceType === 'POLYGON' && drawing.value) {
    vertices.value.push(e.latlng)
    renderPreview()
    return
  }
  if (form.fenceType === 'CIRCLE' && pickingCenter.value) {
    form.centerLng = Number(e.latlng.lng.toFixed(6))
    form.centerLat = Number(e.latlng.lat.toFixed(6))
    pickingCenter.value = false
    renderPreview()
  }
}

function togglePickCenter() {
  pickingCenter.value = !pickingCenter.value
  if (pickingCenter.value) drawing.value = false
}

function toggleDrawing() {
  drawing.value = !drawing.value
  if (drawing.value) pickingCenter.value = false
}

function undoVertex() {
  vertices.value.pop()
  renderPreview()
}

function clearVertices() {
  vertices.value = []
  renderPreview()
}

function onTypeChange() {
  vertices.value = []
  drawing.value = false
  pickingCenter.value = false
  renderPreview()
  map?.setView(DEFAULT_CENTER, 12)
}

function renderPreview() {
  if (!map || !drawLayer) return
  drawLayer.clearLayers()

  if (form.fenceType === 'CIRCLE' && form.centerLng != null && form.centerLat != null && form.radiusM) {
    L.circle([form.centerLat, form.centerLng], {
      radius: form.radiusM,
      color: '#2563eb',
      weight: 2,
      fillOpacity: 0.12
    }).addTo(drawLayer)
    L.circleMarker([form.centerLat, form.centerLng], {
      radius: 5, color: '#2563eb', fillColor: '#fff', fillOpacity: 1
    }).addTo(drawLayer)
  }

  if (form.fenceType === 'POLYGON' && vertices.value.length) {
    const latlngs = vertices.value.map((p) => [p.lat, p.lng] as L.LatLngTuple)
    if (latlngs.length >= 3) {
      L.polygon(latlngs, { color: '#16a34a', weight: 2, fillOpacity: 0.12 }).addTo(drawLayer)
    } else {
      L.polyline(latlngs, { color: '#16a34a', weight: 2, dashArray: '6 4' }).addTo(drawLayer)
    }
    vertices.value.forEach((p) => {
      L.circleMarker([p.lat, p.lng], {
        radius: 4, color: '#16a34a', fillColor: '#fff', fillOpacity: 1, weight: 2
      }).addTo(drawLayer)
    })
  }
}

function fitToShape() {
  if (!map) return
  if (form.fenceType === 'CIRCLE' && form.centerLng != null && form.centerLat != null) {
    map.setView([form.centerLat, form.centerLng], 13)
  } else if (form.fenceType === 'POLYGON' && vertices.value.length) {
    map.fitBounds(L.latLngBounds(vertices.value).pad(0.3))
  } else {
    map.setView(DEFAULT_CENTER, 12)
  }
}

// 圆形中心/半径变化时刷新预览
watch(
  () => [form.centerLng, form.centerLat, form.radiusM],
  () => {
    if (drawerVisible.value && form.fenceType === 'CIRCLE') renderPreview()
  }
)

function openCreate(type: string) {
  Object.assign(form, {
    id: null,
    fenceName: '',
    fenceType: type,
    centerLng: undefined,
    centerLat: undefined,
    radiusM: 1000,
    triggerDir: 3,
    riskLevel: 2,
    cooldownSec: 600,
    status: 1,
    remark: ''
  })
  vertices.value = []
  drawing.value = false
  pickingCenter.value = false
  drawerVisible.value = true
}

async function openEdit(row: GeoFence) {
  const detail = await getFence(row.id)
  Object.assign(form, {
    id: detail.id,
    fenceName: detail.fenceName,
    fenceType: detail.fenceType,
    centerLng: detail.centerLng != null ? Number(detail.centerLng) : undefined,
    centerLat: detail.centerLat != null ? Number(detail.centerLat) : undefined,
    radiusM: detail.radiusM,
    triggerDir: detail.triggerDir,
    riskLevel: detail.riskLevel,
    cooldownSec: detail.cooldownSec,
    status: detail.status,
    remark: detail.remark || ''
  })
  vertices.value = (detail.points || [])
    .map((p) => L.latLng(Number(p[1]), Number(p[0])))
  drawing.value = false
  pickingCenter.value = false
  drawerVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  if (form.fenceType === 'CIRCLE') {
    if (form.centerLng == null || form.centerLat == null || !form.radiusM) {
      ElMessage.warning('请设置圆形围栏的中心点与半径')
      return
    }
  } else if (vertices.value.length < 3) {
    ElMessage.warning('多边形围栏至少需要 3 个顶点')
    return
  }

  // 去掉后端闭合时补的重复尾点，避免重复保存
  let points: LngLat[] | null = null
  if (form.fenceType === 'POLYGON') {
    const raw = vertices.value.map((p) => [Number(p.lng.toFixed(6)), Number(p.lat.toFixed(6))] as LngLat)
    if (raw.length > 1) {
      const first = raw[0]
      const last = raw[raw.length - 1]
      if (first[0] === last[0] && first[1] === last[1]) raw.pop()
    }
    points = raw
  }

  const body = {
    fenceName: form.fenceName.trim(),
    fenceType: form.fenceType,
    centerLng: form.fenceType === 'CIRCLE' ? form.centerLng! : null,
    centerLat: form.fenceType === 'CIRCLE' ? form.centerLat! : null,
    radiusM: form.fenceType === 'CIRCLE' ? form.radiusM : null,
    points,
    triggerDir: form.triggerDir,
    riskLevel: form.riskLevel,
    cooldownSec: form.cooldownSec,
    status: form.status,
    remark: form.remark || null
  }

  saving.value = true
  try {
    if (form.id) {
      await updateFence(form.id, body)
      ElMessage.success('围栏已更新（约 30 秒内热加载生效）')
    } else {
      await createFence(body)
      ElMessage.success('围栏创建成功（约 30 秒内热加载生效）')
    }
    drawerVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onToggleStatus(row: GeoFence, enabled: boolean) {
  const status = enabled ? 1 : 0
  try {
    await changeFenceStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '围栏已启用' : '围栏已停用')
  } catch {
    /* http 拦截器已提示 */
  }
}

async function onDelete(row: GeoFence) {
  await ElMessageBox.confirm(
    `确认删除围栏「${row.fenceName}」？历史越界事件保留，围栏状态数据同步清除。`,
    '危险操作',
    { type: 'warning', confirmButtonText: '删除' }
  )
  await deleteFence(row.id)
  ElMessage.success('围栏已删除')
  load()
}

// ---- 展示辅助 ----
function shapeText(row: GeoFence): string {
  if (row.fenceType === 'CIRCLE') {
    return `中心 (${row.centerLng}, ${row.centerLat})，半径 ${row.radiusM} 米`
  }
  return '多边形围栏（详情中查看顶点）'
}

function dirText(d: number): string {
  return d === 1 ? '进入' : d === 2 ? '离开' : '进出'
}

function dirTag(d: number): 'success' | 'warning' | 'primary' {
  return d === 1 ? 'success' : d === 2 ? 'warning' : 'primary'
}

function levelText(level: number): string {
  return level === 3 ? '高' : level === 2 ? '中' : '低'
}

function levelType(level: number): 'danger' | 'warning' | 'success' {
  return level === 3 ? 'danger' : level === 2 ? 'warning' : 'success'
}

function cooldownText(sec: number): string {
  if (!sec) return '不去重'
  return sec >= 60 ? `${sec / 60} 分钟` : `${sec} 秒`
}

onBeforeUnmount(() => {
  map?.remove()
  map = null
})

load()
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.muted {
  color: #9ca3af;
  font-size: 12px;
}

.draw-map {
  height: 380px;
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  margin-top: 4px;
}
</style>
