<template>
  <div class="profile-page">
    <!-- 全局时间条 -->
    <el-card shadow="never" class="range-bar">
      <div class="range-row">
        <span class="lbl">时间范围</span>
        <el-radio-group v-model="quick" @change="onQuick">
          <el-radio-button label="7">近7日</el-radio-button>
          <el-radio-button label="30">近30日</el-radio-button>
          <el-radio-button label="90">近90日</el-radio-button>
          <el-radio-button label="custom">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="quick === 'custom'"
          v-model="customRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="开始"
          end-placeholder="结束"
          style="width: 380px"
        />
        <span class="range-text">{{ rangeText }}</span>
        <el-button type="primary" plain @click="refreshActive">刷新</el-button>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
      <!-- Tab1 趋势分析 -->
      <el-tab-pane label="趋势分析" name="trend">
        <div class="filter-row">
          <el-radio-group v-model="trendDim" @change="onTrendDim">
            <el-radio-button label="global">全网</el-radio-button>
            <el-radio-button label="dept">企业</el-radio-button>
            <el-radio-button label="driver">司机</el-radio-button>
            <el-radio-button label="vehicle">车辆</el-radio-button>
          </el-radio-group>
          <el-select
            v-if="trendDim === 'dept'"
            v-model="trendDeptId"
            filterable
            placeholder="选择企业"
            style="width: 220px"
            @change="loadTrend"
          >
            <el-option v-for="o in deptOptions" :key="o.id" :label="o.label" :value="String(o.id)" />
          </el-select>
          <el-select
            v-if="trendDim === 'driver'"
            v-model="trendDriverId"
            filterable
            placeholder="选择司机"
            style="width: 200px"
            @change="loadTrend"
          >
            <el-option v-for="o in driverOptions" :key="o.id" :label="o.label" :value="String(o.id)" />
          </el-select>
          <el-select
            v-if="trendDim === 'vehicle'"
            v-model="trendPlate"
            filterable
            placeholder="选择车辆"
            style="width: 200px"
            @change="loadTrend"
          >
            <el-option v-for="o in vehicleOptions" :key="o.id" :label="o.label" :value="o.label" />
          </el-select>
          <el-radio-group v-model="granularity" @change="loadTrend">
            <el-radio-button label="day">日</el-radio-button>
            <el-radio-button label="week">周</el-radio-button>
            <el-radio-button label="month">月</el-radio-button>
          </el-radio-group>
        </div>
        <div v-loading="trendLoading">
          <div ref="trendChartRef" class="chart-main"></div>
          <div ref="codeChartRef" class="chart-sub"></div>
        </div>
      </el-tab-pane>

      <!-- Tab2 对象画像 -->
      <el-tab-pane label="对象画像" name="card">
        <div class="filter-row">
          <el-radio-group v-model="cardType" @change="onCardType">
            <el-radio-button label="vehicle">车辆</el-radio-button>
            <el-radio-button label="driver">司机</el-radio-button>
            <el-radio-button label="dept">企业</el-radio-button>
          </el-radio-group>
          <el-select v-if="cardType === 'vehicle'" v-model="cardPlate" filterable placeholder="选择车辆" style="width: 200px" @change="loadCard">
            <el-option v-for="o in vehicleOptions" :key="o.id" :label="o.label" :value="o.label" />
          </el-select>
          <el-select v-if="cardType === 'driver'" v-model="cardDriverId" filterable placeholder="选择司机" style="width: 200px" @change="loadCard">
            <el-option v-for="o in driverOptions" :key="o.id" :label="o.label" :value="String(o.id)" />
          </el-select>
          <el-select v-if="cardType === 'dept'" v-model="cardDeptId" filterable placeholder="选择企业" style="width: 220px" @change="loadCard">
            <el-option v-for="o in deptOptions" :key="o.id" :label="o.label" :value="String(o.id)" />
          </el-select>
        </div>
        <div v-loading="cardLoading" class="card-body">
          <template v-if="card">
            <!-- 概览头卡 -->
            <el-row :gutter="12">
              <el-col :span="8">
                <el-card shadow="never" class="stat-card">
                  <div class="stat-name">{{ card.name }}<span class="stat-sub">{{ card.sub }}</span></div>
                  <div class="stat-grid">
                    <div><b>{{ card.eventTotal }}</b><span>周期事件</span></div>
                    <div><b class="danger">{{ card.highCnt }}</b><span>高危事件</span></div>
                    <div>
                      <b>{{ card.scoreReady && card.latestScore ? card.latestScore.score : '—' }}</b>
                      <span>最新评分{{ card.latestScore ? ' (' + card.latestScore.level + ')' : '' }}</span>
                    </div>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="16">
                <el-card shadow="never" class="stat-card">
                  <template #header><span class="card-h">干预与闭环漏斗</span></template>
                  <div v-if="card.funnel" class="funnel">
                    <div class="f-step"><b>{{ card.funnel.eventTotal }}</b><span>事件</span></div>
                    <div class="f-arrow">→</div>
                    <div class="f-step"><b>{{ card.funnel.orderTotal }}</b><span>工单</span></div>
                    <div class="f-arrow">→</div>
                    <div class="f-step"><b>{{ card.funnel.interventionTotal }}</b><span>干预</span></div>
                    <div class="f-arrow">→</div>
                    <div class="f-step"><b>{{ card.funnel.closedTotal }}</b><span>闭环</span></div>
                    <div class="f-rates">
                      干预率 {{ card.funnel.interventionRate ?? '—' }}% ｜ 闭环率 {{ card.funnel.closeRate ?? '—' }}%
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>

            <el-row :gutter="12" class="mt12">
              <el-col :span="12">
                <el-card shadow="never">
                  <template #header><span class="card-h">评分曲线</span></template>
                  <div v-if="card.scoreReady" ref="scoreChartRef" class="chart-half"></div>
                  <el-empty v-else description="依赖未就绪：驾驶行为评分（F22）尚未上线" :image-size="60" />
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never">
                  <template #header><span class="card-h">事件构成（Top6）</span></template>
                  <div ref="compChartRef" class="chart-half"></div>
                </el-card>
              </el-col>
            </el-row>

            <el-card shadow="never" class="mt12">
              <template #header><span class="card-h">工单 Top5</span></template>
              <el-table :data="card.topOrders" size="small" empty-text="周期内无工单">
                <el-table-column prop="orderNo" label="工单号" min-width="170">
                  <template #default="{ row }">
                    <el-link type="primary" @click="goOrder(row.id)">{{ row.orderNo }}</el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="事件" min-width="140" />
                <el-table-column label="等级" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.riskLevel === 3 ? 'danger' : row.riskLevel === 2 ? 'warning' : 'info'" size="small">
                      {{ levelDict.toMap.value[String(row.riskLevel)] || row.riskLevel }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="110">
                  <template #default="{ row }">{{ statusLabel(row.status) }}</template>
                </el-table-column>
                <el-table-column label="超时" width="70">
                  <template #default="{ row }">
                    <el-tag v-if="row.overdue === 1" type="danger" size="small">超时</el-tag>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column prop="eventTime" label="事件时间" min-width="150" />
              </el-table>
            </el-card>
          </template>
          <el-empty v-else description="选择对象查看画像" />
        </div>
      </el-tab-pane>

      <!-- Tab3 黑点路段 -->
      <el-tab-pane label="黑点路段" name="hotspots">
        <div class="filter-row">
          <span class="lbl">网格半径</span>
          <el-radio-group v-model="radiusM" @change="loadHotspots">
            <el-radio-button :label="100">100m</el-radio-button>
            <el-radio-button :label="200">200m</el-radio-button>
            <el-radio-button :label="500">500m</el-radio-button>
          </el-radio-group>
          <span class="lbl">Top</span>
          <el-input-number v-model="spotLimit" :min="1" :max="50" @change="loadHotspots" />
          <span class="note">{{ hotspotNote }}</span>
        </div>
        <div v-loading="spotLoading" class="spot-body">
          <el-table
            :data="spotItems"
            size="small"
            class="spot-table"
            highlight-current-row
            @current-change="onSpotSelect"
            @row-click="onSpotSelect"
          >
            <el-table-column prop="rank" label="#" width="46" />
            <el-table-column label="中心坐标" min-width="150">
              <template #default="{ row }">{{ row.lng.toFixed(4) }}, {{ row.lat.toFixed(4) }}</template>
            </el-table-column>
            <el-table-column prop="eventCnt" label="事件数" width="70" sortable />
            <el-table-column prop="weightedScore" label="加权分" width="70" sortable />
            <el-table-column prop="plateCnt" label="车辆数" width="70" />
            <el-table-column prop="topPlate" label="代表车牌" min-width="100" />
            <el-table-column label="高频事件" min-width="110">
              <template #default="{ row }">{{ codeName(row.topCode) }}</template>
            </el-table-column>
          </el-table>
          <div ref="spotMapRef" class="spot-map"></div>
        </div>
      </el-tab-pane>

      <!-- Tab4 对比排行 -->
      <el-tab-pane label="对比排行" name="ranking">
        <div class="filter-row">
          <el-radio-group v-model="rankDim" @change="loadRanking">
            <el-radio-button label="driver">司机榜</el-radio-button>
            <el-radio-button label="vehicle">车辆榜</el-radio-button>
          </el-radio-group>
          <span class="lbl">Top</span>
          <el-input-number v-model="rankLimit" :min="1" :max="50" @change="loadRanking" />
        </div>
        <el-table v-loading="rankLoading" :data="rankItems" size="default" class="mt12" @row-click="goCard">
          <el-table-column prop="rank" label="排名" width="70" />
          <el-table-column prop="name" label="对象" min-width="120" />
          <el-table-column prop="sub" label="关联" min-width="110">
            <template #default="{ row }">{{ row.sub || '—' }}</template>
          </el-table-column>
          <el-table-column prop="eventCnt" label="事件数" width="90" sortable />
          <el-table-column prop="highCnt" label="高危数" width="90" sortable />
          <el-table-column prop="weightedScore" label="等级加权分" width="120" sortable />
          <el-table-column label="最新评分" width="100">
            <template #default="{ row }">{{ row.score ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="等级" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.level" :type="levelTagType(row.level)" size="small">{{ row.level }}</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <p class="hint">点击行跳转对应对象画像。</p>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, RadarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useDict } from '@/composables/useDict'
import { getDeptOptions, getDriverOptions, getVehicleOptions, type Option } from '@/api/mdm'
import {
  getProfileTrend,
  getProfileObjectCard,
  getProfileHotspots,
  getProfileRanking,
  type ProfileTrend,
  type ProfileObjectCard,
  type ProfileHotspots,
  type ProfileRanking
} from '@/api/analysis'

echarts.use([LineChart, BarChart, RadarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const levelDict = useDict('risk_level')

// ===== 全局时间范围 =====
const quick = ref<'7' | '30' | '90' | 'custom'>('30')
const customRange = ref<[string, string] | null>(null)
const activeTab = ref('trend')

function pad(n: number): string {
  return String(n).padStart(2, '0')
}
function fmtLocal(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function dayEnd(d: Date): Date {
  const x = new Date(d)
  x.setDate(x.getDate() + 1)
  x.setHours(0, 0, 0, 0)
  return x
}

const range = computed<{ start: string; end: string } | null>(() => {
  if (quick.value === 'custom') {
    if (!customRange.value) return null
    return { start: customRange.value[0], end: customRange.value[1] }
  }
  const days = Number(quick.value)
  const end = dayEnd(new Date())
  const start = new Date(end)
  start.setDate(start.getDate() - days)
  return { start: fmtLocal(start), end: fmtLocal(end) }
})

const rangeText = computed(() => {
  const r = range.value
  return r ? `${r.start.replace('T', ' ')} ~ ${r.end.replace('T', ' ')}` : '请选择时间范围'
})

function checkSpan(): boolean {
  const r = range.value
  if (!r) {
    ElMessage.warning('请选择时间范围')
    return false
  }
  const days = (new Date(r.end).getTime() - new Date(r.start).getTime()) / 86400000
  if (days > 366) {
    ElMessage.error('时间跨度不得超过 366 天')
    return false
  }
  if (days <= 0) {
    ElMessage.error('开始时间必须早于结束时间')
    return false
  }
  return true
}

function onQuick(v: any) {
  if (v === 'custom' && !customRange.value) {
    const end = dayEnd(new Date())
    const start = new Date(end)
    start.setDate(start.getDate() - 30)
    customRange.value = [fmtLocal(start), fmtLocal(end)]
  }
  refreshActive()
}

// ===== 选项数据 =====
const deptOptions = ref<Option[]>([])
const driverOptions = ref<Option[]>([])
const vehicleOptions = ref<Option[]>([])

// ===== Tab1 趋势 =====
const trendDim = ref<'global' | 'dept' | 'driver' | 'vehicle'>('global')
const trendDeptId = ref('')
const trendDriverId = ref('')
const trendPlate = ref('')
const granularity = ref<'day' | 'week' | 'month'>('day')
const trendLoading = ref(false)
const trendData = ref<ProfileTrend | null>(null)
const trendChartRef = ref<HTMLElement>()
const codeChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let codeChart: echarts.ECharts | null = null

function trendObjectId(): string | undefined {
  if (trendDim.value === 'dept') return trendDeptId.value || undefined
  if (trendDim.value === 'driver') return trendDriverId.value || undefined
  if (trendDim.value === 'vehicle') return trendPlate.value || undefined
  return undefined
}

function onTrendDim() {
  trendDeptId.value = ''
  trendDriverId.value = ''
  trendPlate.value = ''
  loadTrend()
}

async function loadTrend() {
  if (trendDim.value !== 'global' && !trendObjectId()) return
  if (!checkSpan()) return
  const r = range.value!
  trendLoading.value = true
  try {
    trendData.value = await getProfileTrend({
      dim: trendDim.value,
      id: trendObjectId(),
      start: r.start,
      end: r.end,
      granularity: granularity.value
    })
    await nextTick()
    renderTrend()
  } catch {
    /* 拦截器提示 */
  } finally {
    trendLoading.value = false
  }
}

function renderTrend() {
  const d = trendData.value
  if (!d || !trendChartRef.value) return
  trendChart = trendChart || echarts.init(trendChartRef.value)
  trendChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      legend: { data: ['总量', '高', '中', '低'] },
      grid: { left: 50, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: d.buckets },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '总量', type: 'line', data: d.total, itemStyle: { color: '#1f5fbf' } },
        { name: '高', type: 'line', data: d.byLevel.high, lineStyle: { type: 'dashed' }, itemStyle: { color: '#dc2626' } },
        { name: '中', type: 'line', data: d.byLevel.mid, lineStyle: { type: 'dashed' }, itemStyle: { color: '#d97706' } },
        { name: '低', type: 'line', data: d.byLevel.low, lineStyle: { type: 'dashed' }, itemStyle: { color: '#6b7280' } }
      ]
    },
    true
  )
  if (!codeChartRef.value) return
  codeChart = codeChart || echarts.init(codeChartRef.value)
  codeChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: d.topCodes.map((c) => c.name) },
      grid: { left: 50, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: d.buckets },
      yAxis: { type: 'value', minInterval: 1 },
      series: d.topCodes.map((c) => ({ name: c.name, type: 'bar', stack: 'code', data: c.series }))
    },
    true
  )
}

// ===== Tab2 对象画像 =====
const cardType = ref<'vehicle' | 'driver' | 'dept'>('vehicle')
const cardPlate = ref('')
const cardDriverId = ref('')
const cardDeptId = ref('')
const cardLoading = ref(false)
const card = ref<ProfileObjectCard | null>(null)
const scoreChartRef = ref<HTMLElement>()
const compChartRef = ref<HTMLElement>()
let scoreChart: echarts.ECharts | null = null
let compChart: echarts.ECharts | null = null

function cardObjectId(): string {
  if (cardType.value === 'vehicle') return cardPlate.value
  if (cardType.value === 'driver') return cardDriverId.value
  return cardDeptId.value
}

function onCardType() {
  cardPlate.value = ''
  cardDriverId.value = ''
  cardDeptId.value = ''
  card.value = null
}

async function loadCard() {
  const id = cardObjectId()
  if (!id || !checkSpan()) return
  const r = range.value!
  cardLoading.value = true
  try {
    card.value = await getProfileObjectCard({ type: cardType.value, id, start: r.start, end: r.end })
    await nextTick()
    renderCardCharts()
  } catch {
    /* 拦截器提示 */
  } finally {
    cardLoading.value = false
  }
}

function renderCardCharts() {
  const c = card.value
  if (!c) return
  if (c.scoreReady && scoreChartRef.value) {
    scoreChart = scoreChart || echarts.init(scoreChartRef.value)
    const curve = c.scoreCurve
    scoreChart.setOption(
      {
        tooltip: { trigger: 'axis' },
        grid: { left: 45, right: 20, top: 30, bottom: 30 },
        xAxis: { type: 'category', data: curve ? curve.dates : [] },
        yAxis: { type: 'value', min: 0, max: 100 },
        series: [{ type: 'line', data: curve ? curve.scores : [], itemStyle: { color: '#15803d' }, areaStyle: { opacity: 0.08 } }]
      },
      true
    )
  } else {
    scoreChart?.dispose()
    scoreChart = null
  }
  if (compChartRef.value) {
    compChart = compChart || echarts.init(compChartRef.value)
    const top6 = c.composition.slice(0, 6)
    const maxCnt = Math.max(1, ...top6.map((x) => x.cnt))
    compChart.setOption(
      {
        tooltip: {},
        radar: {
          indicator: top6.map((x) => ({ name: x.name, max: maxCnt })),
          radius: '65%'
        },
        series: [
          {
            type: 'radar',
            data: [{ value: top6.map((x) => x.cnt), name: '事件数', areaStyle: { opacity: 0.25 } }]
          }
        ]
      },
      true
    )
  }
}

function statusLabel(s: string) {
  return s === 'PENDING' ? '待处理' : s === 'PROCESSING' ? '处理中' : s === 'CLOSED' ? '已闭环' : s
}

function goOrder(id: string) {
  router.push({ path: '/risk/orders', query: { openOrder: id } })
}

// ===== Tab3 黑点路段 =====
const radiusM = ref(200)
const spotLimit = ref(20)
const spotLoading = ref(false)
const spotItems = ref<ProfileHotspots['items']>([])
const hotspotNote = ref('')
const spotMapRef = ref<HTMLElement>()
let spotMap: L.Map | null = null
let spotLayer: L.LayerGroup | null = null
const spotMarkers = new Map<number, L.CircleMarker>()

async function loadHotspots() {
  if (!checkSpan()) return
  const r = range.value!
  spotLoading.value = true
  try {
    const data = await getProfileHotspots({
      start: r.start,
      end: r.end,
      radius_m: radiusM.value,
      limit: spotLimit.value
    })
    spotItems.value = data.items
    hotspotNote.value = data.note
    await nextTick()
    renderSpotMap()
  } catch {
    /* 拦截器提示 */
  } finally {
    spotLoading.value = false
  }
}

function ensureSpotMap() {
  if (spotMap || !spotMapRef.value) return
  spotMap = L.map(spotMapRef.value, { zoomControl: true }).setView([39.91, 116.4], 9)
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18, attribution: '高德地图' }
  ).addTo(spotMap)
  spotLayer = L.layerGroup().addTo(spotMap)
}

function renderSpotMap() {
  ensureSpotMap()
  if (!spotMap || !spotLayer) return
  spotMap.invalidateSize()
  spotLayer.clearLayers()
  spotMarkers.clear()
  const items = spotItems.value
  if (!items.length) return
  const avg = items.reduce((s, x) => s + x.weightedScore, 0) / items.length
  const maxWs = Math.max(...items.map((x) => x.weightedScore), 1)
  for (const it of items) {
    const color = it.weightedScore >= avg * 1.5 ? '#dc2626' : it.weightedScore >= avg ? '#ea580c' : '#eab308'
    const m = L.circleMarker([it.lat, it.lng], {
      radius: 6 + (it.weightedScore / maxWs) * 10,
      color,
      fillColor: color,
      fillOpacity: 0.5,
      weight: 1.5
    })
    m.bindTooltip(`#${it.rank} 事件${it.eventCnt} 加权${it.weightedScore} ${it.topPlate || ''}`)
    m.on('click', () => highlightRow(it.rank))
    m.addTo(spotLayer)
    spotMarkers.set(it.rank, m)
  }
}

function highlightRow(rank: number) {
  const row = spotItems.value.find((x) => x.rank === rank)
  if (row) onSpotSelect(row)
}

function onSpotSelect(row: ProfileHotspots['items'][number] | null) {
  if (!row || !spotMap) return
  spotMap.flyTo([row.lat, row.lng], Math.max(spotMap.getZoom(), 13))
  for (const [rank, m] of spotMarkers) {
    const sel = rank === row.rank
    m.setStyle({ weight: sel ? 3 : 1.5, fillOpacity: sel ? 0.8 : 0.5 })
  }
}

// ===== Tab4 对比排行 =====
const rankDim = ref<'driver' | 'vehicle'>('driver')
const rankLimit = ref(10)
const rankLoading = ref(false)
const rankItems = ref<ProfileRanking['items']>([])

async function loadRanking() {
  if (!checkSpan()) return
  const r = range.value!
  rankLoading.value = true
  try {
    const data = await getProfileRanking({ dim: rankDim.value, start: r.start, end: r.end, limit: rankLimit.value })
    rankItems.value = data.items
  } catch {
    /* 拦截器提示 */
  } finally {
    rankLoading.value = false
  }
}

function goCard(row: ProfileRanking['items'][number]) {
  cardType.value = rankDim.value
  if (rankDim.value === 'driver') cardDriverId.value = row.id
  else cardPlate.value = row.id
  activeTab.value = 'card'
  loadCard()
}

function levelTagType(v: string) {
  return v === 'A' ? 'success' : v === 'B' ? 'primary' : v === 'E' ? 'danger' : 'warning'
}

const CODE_NAMES: Record<string, string> = {
  SPEED_GENERAL: '一般超速', SPEED_SEVERE: '严重超速', FATIGUE_DRIVE: '疲劳驾驶',
  DSM_FATIGUE: '终端信号·疲劳', DSM_DISTRACTION: '终端信号·分心', ADAS_FCW: '前向碰撞风险',
  ADAS_LDW: '车道偏离', COMBO_FATIGUE_SPEED: '疲劳叠加超速', GEO_ENTER: '进入围栏', GEO_EXIT: '离开围栏',
  V_LDW: '车道偏离', V_HMW: '车距过近', V_FCW: '前向碰撞', V_PCW: '未系安全带', BEIDOU_SPEED: '北斗超速',
  '1': '紧急报警', '2': '超速报警', '3': '疲劳驾驶', '4': '危险预警', '5': 'GNSS故障', '6': '通信故障', '7': '超速预警'
}
function codeName(code: string) {
  return CODE_NAMES[code] || code || '—'
}

// ===== Tab 懒加载与刷新 =====
const loadedTabs = new Set<string>()

function refreshActive() {
  loadedTabs.add(activeTab.value)
  loadTab(activeTab.value)
}

function loadTab(tab: string) {
  if (tab === 'trend') loadTrend()
  else if (tab === 'card') { if (cardObjectId()) loadCard() }
  else if (tab === 'hotspots') loadHotspots()
  else if (tab === 'ranking') loadRanking()
}

function onTabChange(name: any) {
  if (!loadedTabs.has(String(name))) {
    loadedTabs.add(String(name))
    loadTab(String(name))
  }
}

watch([customRange], () => {
  if (quick.value === 'custom') refreshActive()
})

function onResize() {
  trendChart?.resize()
  codeChart?.resize()
  scoreChart?.resize()
  compChart?.resize()
}

onMounted(async () => {
  const [depts, drivers, vehicles] = await Promise.all([
    getDeptOptions().catch(() => []),
    getDriverOptions().catch(() => []),
    getVehicleOptions().catch(() => [])
  ])
  deptOptions.value = depts
  driverOptions.value = drivers
  vehicleOptions.value = vehicles
  loadedTabs.add('trend')
  loadTrend()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  codeChart?.dispose()
  scoreChart?.dispose()
  compChart?.dispose()
  spotMap?.remove()
})
</script>

<style scoped>
.profile-page {
  padding: 4px;
}
.range-bar {
  margin-bottom: 12px;
}
.range-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.range-row .lbl,
.filter-row .lbl {
  color: #667085;
  font-size: 13px;
}
.range-text {
  color: #344054;
  font-size: 13px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.note {
  color: #b45309;
  font-size: 12px;
}
.chart-main {
  height: 300px;
}
.chart-sub {
  height: 240px;
  margin-top: 8px;
}
.chart-half {
  height: 240px;
}
.mt12 {
  margin-top: 12px;
}
.stat-card .stat-name {
  font-size: 16px;
  font-weight: 600;
  color: #16345f;
}
.stat-sub {
  margin-left: 8px;
  font-size: 12px;
  color: #667085;
  font-weight: 400;
}
.stat-grid {
  display: flex;
  gap: 30px;
  margin-top: 12px;
}
.stat-grid b {
  font-size: 22px;
  color: #1f5fbf;
  display: block;
}
.stat-grid b.danger {
  color: #dc2626;
}
.stat-grid span {
  font-size: 12px;
  color: #667085;
}
.card-h {
  font-weight: 600;
  font-size: 14px;
}
.funnel {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.f-step {
  text-align: center;
  background: #eef4ff;
  border-radius: 8px;
  padding: 8px 18px;
}
.f-step b {
  display: block;
  font-size: 20px;
  color: #174a96;
}
.f-step span {
  font-size: 12px;
  color: #667085;
}
.f-arrow {
  color: #1f5fbf;
  font-weight: 700;
}
.f-rates {
  margin-left: auto;
  font-size: 13px;
  color: #344054;
}
.spot-body {
  display: flex;
  gap: 12px;
  height: 460px;
}
.spot-table {
  width: 52%;
  flex-shrink: 0;
}
.spot-map {
  flex: 1;
  min-width: 0;
  height: 100%;
  border-radius: 8px;
  border: 1px solid #e3e8f0;
}
.hint {
  color: #98a2b3;
  font-size: 12px;
  margin-top: 8px;
}
</style>
