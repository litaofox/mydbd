<template>
  <div class="page">
    <!-- 统计条 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">今日报警总数</div>
            <div class="kpi-value">{{ stats.total }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">待处理</div>
            <div class="kpi-value" style="color: #dc2626">{{ statusCount(0) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">已确认</div>
            <div class="kpi-value" style="color: #d97706">{{ statusCount(1) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">已解除</div>
            <div class="kpi-value" style="color: #16a34a">{{ statusCount(2) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <!-- 筛选栏 -->
      <div class="toolbar">
        <el-input v-model="query.plateNo" placeholder="车牌模糊" clearable style="width: 140px" @keyup.enter="reload" />
        <el-select v-model="query.typeId" placeholder="报警类型" clearable style="width: 140px">
          <el-option v-for="t in typeOptions" :key="t.typeId" :label="t.typeName" :value="t.typeId" />
        </el-select>
        <el-select v-model="query.handleStatus" placeholder="处置状态" clearable style="width: 120px">
          <el-option label="待处理" :value="0" />
          <el-option label="已确认" :value="1" />
          <el-option label="已解除" :value="2" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 360px"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>

      <el-table :data="rows" size="small" v-loading="loading">
        <el-table-column prop="plateNo" label="车牌" width="110" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="gradeTag(row.gradeLevel)" size="small">{{ row.typeName || `类型 ${row.typeId}` }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报警开始" width="160">
          <template #default="{ row }">{{ fmt(row.startWarnTime) }}</template>
        </el-table-column>
        <el-table-column label="报警结束" width="160">
          <template #default="{ row }">{{ fmt(row.endWarnTime) }}</template>
        </el-table-column>
        <el-table-column label="速度" width="80">
          <template #default="{ row }">{{ row.startSpeed ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="持续" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.warnContinueMark === 1" type="danger" size="small" effect="plain">持续</el-tag>
            <span v-else-if="row.warnContinueMark === 0" class="hint">停止</span>
            <span v-else class="hint">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.handleStatus)" size="small">{{ statusLabel(row.handleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处置人" width="100" show-overflow-tooltip />
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmt(row.updateDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.handleStatus === 0"
              v-perm="'alarm:handle'"
              link
              type="warning"
              size="small"
              @click="doConfirm(row)"
            >确认</el-button>
            <el-button
              v-if="row.handleStatus === 0 || row.handleStatus === 1"
              v-perm="'alarm:handle'"
              link
              type="success"
              size="small"
              @click="openResolve(row)"
            >解除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="query.size"
        :page-sizes="[20, 50, 100]"
        :current-page="query.page"
        @size-change="onSizeChange"
        @current-change="onPage"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="报警详情" size="640px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="车牌">{{ detail.plateNo }}</el-descriptions-item>
          <el-descriptions-item label="终端标识">{{ detail.identityCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="gradeTag(detail.gradeLevel)" size="small">{{ detail.typeName || `类型 ${detail.typeId}` }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.handleStatus)" size="small">{{ statusLabel(detail.handleStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="报警开始">{{ fmt(detail.startWarnTime) }}</el-descriptions-item>
          <el-descriptions-item label="报警结束">{{ fmt(detail.endWarnTime) }}</el-descriptions-item>
          <el-descriptions-item label="起始速度">{{ detail.startSpeed ?? '-' }} km/h</el-descriptions-item>
          <el-descriptions-item label="结束速度">{{ detail.endSpeed ?? '-' }} km/h</el-descriptions-item>
          <el-descriptions-item label="持续标志">{{ detail.warnContinueMark === 1 ? '持续' : detail.warnContinueMark === 0 ? '停止' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="处置人">{{ detail.handler || '-' }}</el-descriptions-item>
          <el-descriptions-item label="解除结论">{{ resultCodeLabel(detail.handleResultCode) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ fmt(detail.updateDate) }}</el-descriptions-item>
          <el-descriptions-item label="处置说明" :span="2">{{ detail.handleResultMsg || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4>报警位置（坐标为终端上报原始值）</h4>
        <div v-if="hasCoord" ref="mapRef" class="mini-map"></div>
        <p v-else class="hint">坐标缺失</p>

        <h4>关联风险事件（同车牌 ±30 分钟）</h4>
        <el-table v-if="detail.relatedRisks && detail.relatedRisks.length" :data="detail.relatedRisks" size="small">
          <el-table-column label="事件" min-width="150">
            <template #default="{ row }">{{ row.title || row.eventCode || '-' }}</template>
          </el-table-column>
          <el-table-column label="等级" width="80">
            <template #default="{ row }">
              <el-tag :type="row.riskLevel === 3 ? 'danger' : row.riskLevel === 2 ? 'warning' : 'info'" size="small">
                {{ row.riskLevel === 3 ? '高' : row.riskLevel === 2 ? '中' : '低' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmt(row.eventTime) }}</template>
          </el-table-column>
        </el-table>
        <p v-else class="hint">窗口内无关联风险事件</p>
        <router-link to="/risk" class="risk-link">前往风险预警分析页人工研判 →</router-link>
      </div>
    </el-drawer>

    <!-- 解除对话框 -->
    <el-dialog v-model="resolveVisible" title="解除报警" width="440px">
      <el-form label-width="80px">
        <el-form-item label="结论">
          <el-radio-group v-model="resolveForm.resultCode">
            <el-radio value="00">属实</el-radio>
            <el-radio value="01">误报</el-radio>
            <el-radio value="02">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="resolveForm.resultMsg"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="可选，不超过 200 字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="submitResolve">确定解除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useRealtime, type AlarmBrief } from '@/composables/useRealtime'
import {
  getAlarmPage,
  getAlarmTypes,
  getAlarmStats,
  getAlarmDetail,
  confirmAlarm,
  resolveAlarm,
  type AlarmVO,
  type AlarmType,
  type AlarmStats
} from '@/api/alarm'

const route = useRoute()

// ===== 列表与筛选 =====
const query = reactive({
  page: 1,
  size: 20,
  plateNo: '',
  typeId: undefined as number | undefined,
  handleStatus: undefined as number | undefined,
  beginTime: '',
  endTime: ''
})
const loading = ref(false)
const rows = ref<AlarmVO[]>([])
const total = ref(0)
const typeOptions = ref<AlarmType[]>([])
const stats = ref<AlarmStats>({ total: 0, byType: [], byStatus: [] })

function todayDefaultRange(): [string, string] {
  const d = new Date()
  const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return [`${day}T00:00:00`, `${day}T23:59:59`]
}
const timeRange = ref<[string, string] | null>(todayDefaultRange())

function gradeTag(g: number | null) {
  return g === 1 ? 'danger' : g === 2 ? 'warning' : 'info'
}
function statusTag(s: number) {
  return s === 0 ? 'danger' : s === 1 ? 'warning' : 'success'
}
function statusLabel(s: number) {
  return s === 0 ? '待处理' : s === 1 ? '已确认' : '已解除'
}
function resultCodeLabel(c: string | null) {
  return c === '00' ? '属实' : c === '01' ? '误报' : c === '02' ? '未知' : '-'
}
function fmt(v: string | null) {
  return v ? v.replace('T', ' ').slice(0, 19) : '-'
}
function statusCount(s: number) {
  return stats.value.byStatus.find((x) => x.handleStatus === s)?.count ?? 0
}

async function loadRows() {
  loading.value = true
  try {
    const data = await getAlarmPage({
      page: query.page,
      size: query.size,
      plateNo: query.plateNo || undefined,
      typeId: query.typeId,
      handleStatus: query.handleStatus,
      beginTime: timeRange.value?.[0] || undefined,
      endTime: timeRange.value?.[1] || undefined
    })
    rows.value = data.records
    total.value = data.total
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await getAlarmStats()
  } catch {
    /* ignore */
  }
}

function reload() {
  query.page = 1
  loadRows()
}

function resetFilter() {
  query.plateNo = ''
  query.typeId = undefined
  query.handleStatus = undefined
  timeRange.value = todayDefaultRange()
  reload()
}

function onPage(p: number) {
  query.page = p
  loadRows()
}

function onSizeChange(s: number) {
  query.size = s
  query.page = 1
  loadRows()
}

// ===== 处置 =====
function replaceRow(vo: AlarmVO) {
  const i = rows.value.findIndex((r) => r.id === vo.id)
  if (i >= 0) rows.value[i] = vo
}

async function doConfirm(row: AlarmVO) {
  try {
    const vo = await confirmAlarm(row.id)
    replaceRow(vo)
    ElMessage.success('已确认')
    loadStats()
  } catch (e: any) {
    if (String(e?.message || '').includes('状态已变更')) loadRows()
  }
}

const resolveVisible = ref(false)
const resolving = ref(false)
const resolveTarget = ref<AlarmVO | null>(null)
const resolveForm = reactive({ resultCode: '00', resultMsg: '' })

function openResolve(row: AlarmVO) {
  resolveTarget.value = row
  resolveForm.resultCode = '00'
  resolveForm.resultMsg = ''
  resolveVisible.value = true
}

async function submitResolve() {
  if (!resolveTarget.value) return
  resolving.value = true
  try {
    const vo = await resolveAlarm(
      resolveTarget.value.id,
      resolveForm.resultCode,
      resolveForm.resultMsg.trim() || undefined
    )
    replaceRow(vo)
    ElMessage.success('已解除')
    resolveVisible.value = false
    loadStats()
  } catch (e: any) {
    if (String(e?.message || '').includes('状态已变更')) {
      resolveVisible.value = false
      loadRows()
    }
  } finally {
    resolving.value = false
  }
}

// ===== 详情抽屉 + 地图 =====
const detailVisible = ref(false)
const detail = ref<AlarmVO | null>(null)
const mapRef = ref<HTMLDivElement>()
let map: L.Map | null = null

const hasCoord = computed(() => !!detail.value && (parseCoord(detail.value.startLng, detail.value.startLat) !== null || parseCoord(detail.value.endLng, detail.value.endLat) !== null))

/** varchar 坐标防御解析：非法/0 视为缺失 */
function parseCoord(lng: string | null, lat: string | null): [number, number] | null {
  if (!lng || !lat) return null
  const ln = parseFloat(lng)
  const la = parseFloat(lat)
  if (Number.isFinite(ln) && Number.isFinite(la) && ln !== 0 && la !== 0) return [la, ln]
  return null
}

async function openDetail(row: AlarmVO) {
  try {
    detail.value = await getAlarmDetail(row.id)
  } catch {
    detail.value = row
  }
  detailVisible.value = true
  await nextTick()
  renderMap()
}

function renderMap() {
  if (!mapRef.value) return
  map?.remove()
  map = L.map(mapRef.value, { zoomControl: true, attributionControl: false }).setView([39.91, 116.4], 11)
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18 }
  ).addTo(map)
  const d = detail.value!
  const s = parseCoord(d.startLng, d.startLat)
  const e = parseCoord(d.endLng, d.endLat)
  const pts: L.LatLngExpression[] = []
  if (s) {
    L.circleMarker(s, { radius: 7, color: '#16a34a', fillColor: '#16a34a', fillOpacity: 0.8 })
      .addTo(map)
      .bindPopup('报警起点')
    pts.push(s)
  }
  if (e) {
    L.circleMarker(e, { radius: 7, color: '#dc2626', fillColor: '#dc2626', fillOpacity: 0.8 })
      .addTo(map)
      .bindPopup('报警终点')
    pts.push(e)
  }
  if (s && e) L.polyline([s, e], { color: '#1f5fbf', weight: 3 }).addTo(map)
  if (pts.length === 2) map.fitBounds(L.latLngBounds(pts).pad(0.3))
  else if (pts.length === 1) map.setView(pts[0], 13)
}

watch(detailVisible, (v) => {
  if (!v && map) {
    map.remove()
    map = null
  }
})

// ===== 实时链路（复用 F14 WS，§6.4） =====
const seenIds = new Set<string>()
let statsTimer: number | null = null

function filtersInactive() {
  return !query.plateNo && query.typeId === undefined && !timeRange.value
}

useRealtime({
  onAlarm: (alarms: AlarmBrief[]) => {
    for (const a of alarms) {
      const id = String(a.id)
      if (seenIds.has(id)) continue
      seenIds.add(id)
      if (seenIds.size > 200) {
        const first = seenIds.values().next().value
        if (first !== undefined) seenIds.delete(first)
      }
      const t = typeOptions.value.find((x) => x.typeId === a.typeId)
      const typeName = t?.typeName || `类型 ${a.typeId}`
      ElMessage.warning(`新报警：${a.plateNo} · ${typeName}`)
      if (filtersInactive() || query.handleStatus === 0) {
        rows.value.unshift({
          id,
          plateNo: a.plateNo,
          identityCode: null,
          typeId: a.typeId,
          typeName,
          gradeLevel: t?.gradeLevel ?? null,
          startWarnTime: a.startWarnTime,
          endWarnTime: null,
          startLng: a.startLng ?? null,
          startLat: a.startLat ?? null,
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
        total.value += 1
        stats.value.total += 1
      }
    }
  }
})

onMounted(async () => {
  // 大屏钻取：从 route.query 恢复筛选条件（plateNo/typeId/handleStatus/beginTime/endTime）
  const q = route.query
  if (typeof q.plateNo === 'string' && q.plateNo) query.plateNo = q.plateNo
  if (typeof q.typeId === 'string' && q.typeId && !Number.isNaN(Number(q.typeId))) query.typeId = Number(q.typeId)
  if (typeof q.handleStatus === 'string' && q.handleStatus !== '' && !Number.isNaN(Number(q.handleStatus))) query.handleStatus = Number(q.handleStatus)
  if (typeof q.beginTime === 'string' && typeof q.endTime === 'string' && q.beginTime && q.endTime) {
    timeRange.value = [q.beginTime, q.endTime]
  }
  try {
    typeOptions.value = await getAlarmTypes()
  } catch {
    typeOptions.value = []
  }
  loadRows()
  loadStats()
  statsTimer = window.setInterval(loadStats, 30000)
})

onBeforeUnmount(() => {
  if (statsTimer) clearInterval(statsTimer)
})
</script>

<style scoped>
.page {
  padding: 4px;
}
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  align-items: center;
}
.kpi-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
}
.kpi-value {
  font-size: 22px;
  font-weight: 600;
}
.hint {
  font-size: 12px;
  color: #9ca3af;
}
h4 {
  margin: 14px 0 8px;
  font-size: 13px;
  color: #374151;
}
.mini-map {
  height: 260px;
  width: 100%;
  border-radius: 8px;
  border: 1px solid #e3e8f0;
}
.risk-link {
  display: inline-block;
  margin-top: 10px;
  font-size: 13px;
  color: #1f5fbf;
}
</style>
