<template>
  <div class="page">
    <!-- 顶部汇总卡 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">近 7 天均分</div>
            <div class="kpi-value" :style="{ color: scoreColor(summary.avgScore) }">
              {{ summary.avgScore ?? '-' }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">评分记录数</div>
            <div class="kpi-value">{{ summary.recordCount ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">覆盖司机数</div>
            <div class="kpi-value">{{ summary.driverCount ?? '-' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">等级分布（近 7 天）</div>
            <div class="level-chips">
              <el-tag
                v-for="d in summary.levelDist || []"
                :key="d.level"
                :type="levelTagType(d.level)"
                size="small"
                style="margin-right: 4px"
              >
                {{ d.level }} {{ d.count }}
              </el-tag>
              <span v-if="!summary.levelDist || !summary.levelDist.length" class="hint">暂无</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <!-- 筛选工具条 -->
      <div class="toolbar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
        <el-select v-model="driverId" placeholder="司机" clearable filterable style="width: 150px">
          <el-option v-for="o in driverOptions" :key="o.id" :label="o.label" :value="String(o.id)" />
        </el-select>
        <el-input v-model="plateNo" placeholder="车牌模糊" clearable style="width: 140px" @keyup.enter="reload" />
        <el-select v-model="level" placeholder="等级" clearable style="width: 110px">
          <el-option v-for="i in levelDict.items.value" :key="i.itemValue" :label="i.itemLabel" :value="i.itemValue" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button v-if="canRecalc" type="warning" plain @click="openRecalc">重算</el-button>
      </div>

      <el-table :data="rows" size="small" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="scoreDate" label="日期" width="100" />
        <el-table-column prop="driverName" label="司机" width="100" show-overflow-tooltip />
        <el-table-column prop="plateNo" label="车牌" width="110" />
        <el-table-column label="分数" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor(row.score), fontWeight: 600 }">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主要扣分项 Top3" min-width="240">
          <template #default="{ row }">
            <el-tag
              v-for="t in topDeducts(row.features)"
              :key="t.code"
              size="small"
              effect="plain"
              type="danger"
              style="margin-right: 4px"
            >
              {{ featureName(t.code) }}×{{ t.count }} −{{ t.deduct }}
            </el-tag>
            <span v-if="!topDeducts(row.features).length" class="hint">无扣分</span>
          </template>
        </el-table-column>
        <el-table-column prop="eventCount" label="事件数" width="80" />
        <el-table-column prop="samplePoints" label="样本点" width="90" />
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="640px">
      <div v-if="detail">
        <h4>30 天趋势</h4>
        <div ref="trendRef" style="height: 160px; width: 100%"></div>

        <h4>特征明细</h4>
        <el-table :data="featureRows" size="small">
          <el-table-column prop="name" label="特征" min-width="140" />
          <el-table-column prop="count" label="次数" width="80" />
          <el-table-column label="扣分" width="80">
            <template #default="{ row }">
              <span :style="{ color: row.deduct > 0 ? '#dc2626' : '#9ca3af' }">−{{ row.deduct }}</span>
            </template>
          </el-table-column>
        </el-table>

        <p class="hint" style="margin-top: 12px">
          样本点 {{ detail.samplePoints }} · 行驶 {{ detail.features.driving_minutes ?? '-' }} 分钟 ·
          总扣分 −{{ detail.features.total_deduct ?? '-' }} · 事件 {{ detail.eventCount }} 次
        </p>
      </div>
    </el-drawer>

    <!-- 重算对话框 -->
    <el-dialog v-model="recalcVisible" title="评分重算" width="440px">
      <el-form label-width="90px">
        <el-form-item label="日期区间">
          <el-date-picker
            v-model="recalcRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="司机（可选）">
          <el-select v-model="recalcDriverId" placeholder="全部司机" clearable filterable style="width: 100%">
            <el-option v-for="o in driverOptions" :key="o.id" :label="o.label" :value="String(o.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="hint">跨度最多 92 天；提交后异步执行，稍后刷新查看。</p>
      <template #footer>
        <el-button @click="recalcVisible = false">取消</el-button>
        <el-button type="primary" :loading="recalcSubmitting" @click="submitRecalc">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { useAuthStore } from '@/store/auth'
import { useDict } from '@/composables/useDict'
import { getDriverOptions, type Option } from '@/api/mdm'
import {
  getScorePage,
  getScoreSummary,
  getScoreTrend,
  recalcScore,
  type ScoreRow,
  type ScoreSummary,
  type FeatureItem
} from '@/api/analysis'

const auth = useAuthStore()
const levelDict = useDict('score_level')

const FEATURE_NAMES: Record<string, string> = {
  SPEED_GENERAL: '一般超速',
  SPEED_SEVERE: '严重超速',
  FATIGUE_DRIVE: '疲劳驾驶',
  DSM_FATIGUE: '疲劳信号',
  DSM_DISTRACTION: '分心信号',
  ADAS_FCW: '前碰撞风险',
  ADAS_LDW: '车道偏离',
  COMBO_FATIGUE_SPEED: '疲劳叠加超速',
  HARD_ACCEL: '急加速',
  HARD_BRAKE: '急减速'
}
const FEATURE_CODES = Object.keys(FEATURE_NAMES)

// ===== 列表 =====
const loading = ref(false)
const rows = ref<ScoreRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const dateRange = ref<[string, string] | null>(null)
const driverId = ref('')
const plateNo = ref('')
const level = ref('')
const summary = ref<ScoreSummary>({ avgScore: 0, recordCount: 0, driverCount: 0, levelDist: [] })
const driverOptions = ref<Option[]>([])

const canRecalc = computed(() => auth.roles.includes('SUPER_ADMIN'))

function fmt(d: Date): string {
  return d.toISOString().slice(0, 10)
}

async function loadSummary() {
  try {
    summary.value = await getScoreSummary({
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    })
  } catch {
    /* 拦截器已提示 */
  }
}

async function loadRows() {
  loading.value = true
  try {
    const data = await getScorePage({
      page: page.value,
      size: size.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      driverId: driverId.value || undefined,
      plateNo: plateNo.value || undefined,
      level: level.value || undefined
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadRows()
  loadSummary()
}

function onPage(p: number) {
  page.value = p
  loadRows()
}

function levelLabel(v: string) {
  return levelDict.toMap.value[v] || v
}

function levelTagType(v: string) {
  return v === 'A' ? 'success' : v === 'B' ? 'primary' : v === 'E' ? 'danger' : 'warning'
}

function scoreColor(score?: number) {
  if (score == null) return '#6b7280'
  if (score >= 90) return '#16a34a'
  if (score >= 75) return '#2563eb'
  if (score >= 60) return '#f59e0b'
  return '#dc2626'
}

function featureName(code: string) {
  return FEATURE_NAMES[code] || code
}

function topDeducts(features: ScoreRow['features']): { code: string; count: number; deduct: number }[] {
  return FEATURE_CODES.map((code) => {
    const f = features[code] as FeatureItem | undefined
    return f && f.deduct > 0 ? { code, count: f.count, deduct: f.deduct } : null
  })
    .filter((x): x is { code: string; count: number; deduct: number } => x !== null)
    .sort((a, b) => b.deduct - a.deduct)
    .slice(0, 3)
}

// ===== 详情抽屉 =====
const detailVisible = ref(false)
const detail = ref<ScoreRow | null>(null)
const trendRef = ref<HTMLDivElement>()
const detailTitle = computed(() =>
  detail.value ? `${detail.value.driverName || ''} · ${detail.value.plateNo || ''} · ${detail.value.scoreDate}` : ''
)

const featureRows = computed(() => {
  if (!detail.value) return []
  return FEATURE_CODES.map((code) => {
    const f = detail.value!.features[code] as FeatureItem | undefined
    return { name: featureName(code), count: f?.count ?? 0, deduct: f?.deduct ?? 0 }
  })
})

async function openDetail(row: ScoreRow) {
  detail.value = row
  detailVisible.value = true
  await nextTick()
  renderTrend(row)
}

async function renderTrend(row: ScoreRow) {
  if (!trendRef.value) return
  const end = new Date()
  const start = new Date(end.getTime() - 29 * 86400000)
  let points: { scoreDate: string; score: number }[] = []
  try {
    points = await getScoreTrend({
      driverId: row.driverId,
      startDate: fmt(start),
      endDate: fmt(end)
    })
  } catch {
    points = []
  }
  const chart = echarts.init(trendRef.value)
  chart.setOption({
    grid: { left: 30, right: 10, top: 10, bottom: 20 },
    xAxis: { type: 'category', data: points.map((p) => p.scoreDate.slice(5)), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { fontSize: 10 } },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'line',
        data: points.map((p) => p.score),
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 }
      }
    ]
  })
  chart.resize()
}

watch(detailVisible, (v) => {
  if (!v) {
    trendRef.value && echarts.dispose(trendRef.value)
  }
})

// ===== 重算 =====
const recalcVisible = ref(false)
const recalcSubmitting = ref(false)
const recalcRange = ref<[string, string] | null>(null)
const recalcDriverId = ref('')

function openRecalc() {
  recalcRange.value = dateRange.value
  recalcDriverId.value = ''
  recalcVisible.value = true
}

async function submitRecalc() {
  if (!recalcRange.value || !recalcRange.value[0]) {
    ElMessage.warning('请选择日期区间')
    return
  }
  recalcSubmitting.value = true
  try {
    await recalcScore({
      start: recalcRange.value[0],
      end: recalcRange.value[1],
      driverId: recalcDriverId.value || undefined
    })
    ElMessage.success('任务已提交，稍后刷新查看')
    recalcVisible.value = false
  } catch (e: any) {
    if (String(e?.message || '').includes('进行中')) {
      ElMessage.warning('已有评分计算任务进行中，请稍后再试')
    }
  } finally {
    recalcSubmitting.value = false
  }
}

onMounted(async () => {
  reload()
  try {
    driverOptions.value = await getDriverOptions()
  } catch {
    driverOptions.value = []
  }
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
  margin: 8px 0;
  font-size: 13px;
  color: #374151;
}
</style>
