<template>
  <div class="page">
    <!-- 总览 -->
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
          <div class="value" style="color: #dc2626">{{ overview.pendingRisks ?? '-' }}</div>
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

    <!-- 感知—预警—干预—闭环 漏斗（F21） -->
    <el-card v-if="funnel" style="margin-bottom: 12px">
      <div class="funnel">
        <div class="f-step">
          <div class="f-num">{{ funnel.eventTotal }}</div>
          <div class="f-lbl">感知 · 风险事件</div>
        </div>
        <div class="f-arrow">→</div>
        <div class="f-step">
          <div class="f-num">{{ funnel.orderTotal }}</div>
          <div class="f-lbl">预警 · 生成工单</div>
        </div>
        <div class="f-arrow">→</div>
        <div class="f-step">
          <div class="f-num">{{ funnel.interventionTotal }}</div>
          <div class="f-lbl">干预 · 已干预工单</div>
        </div>
        <div class="f-arrow">→</div>
        <div class="f-step">
          <div class="f-num" style="color:#15803d">{{ funnel.closedTotal }}</div>
          <div class="f-lbl">闭环 · 已闭环工单</div>
        </div>
        <div class="f-rate">
          <div>干预率 <strong>{{ funnel.interventionRate }}%</strong></div>
          <div>闭环率 <strong style="color:#15803d">{{ funnel.closeRate }}%</strong></div>
        </div>
      </div>
    </el-card>

    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar" style="margin-bottom: 0">
        <el-select v-model="filters.eventSource" placeholder="事件来源" clearable style="width: 140px">
          <el-option value="DSM" label="DSM 驾驶员" />
          <el-option value="ADAS" label="ADAS 前向" />
          <el-option value="national" label="国标平台" />
          <el-option value="vendor" label="终端厂商" />
          <el-option value="北斗" label="北斗" />
        </el-select>
        <el-select v-model="filters.riskLevel" placeholder="风险等级" clearable style="width: 130px">
          <el-option :value="1" label="低风险" />
          <el-option :value="2" label="中风险" />
          <el-option :value="3" label="高风险" />
        </el-select>
        <el-select v-model="filters.handleStatus" placeholder="处置状态" clearable style="width: 130px">
          <el-option :value="0" label="未处置" />
          <el-option :value="1" label="已处置" />
        </el-select>
        <el-select v-model="filters.ruleId" placeholder="命中规则" clearable filterable style="width: 200px">
          <el-option
            v-for="r in ruleOptions"
            :key="r.id"
            :value="r.id"
            :label="r.ruleName + '（' + r.ruleCode + '）'"
          />
        </el-select>
        <el-input
          v-model="filters.plateNo"
          placeholder="车牌号"
          clearable
          style="width: 140px"
          @keyup.enter="loadRisks"
        />
        <el-tag
          v-if="filters.cityCode"
          closable
          type="warning"
          effect="plain"
          @close="filters.cityCode = ''; filters.cityName = ''; loadRisks()"
        >城市：{{ filters.cityName || filters.cityCode }}</el-tag>
        <el-button type="primary" @click="loadRisks">查询</el-button>
      </div>
    </el-card>

    <!-- 列表 + 分布图 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="16">
        <el-card>
          <template #header>风险预警事件</template>
          <el-table :data="risks" size="small">
            <el-table-column label="事件名称" min-width="170">
              <template #default="{ row }">
                <div>{{ row.title || row.eventCode }}</div>
                <div class="code-sub">{{ row.eventCode }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="eventSource" label="来源" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ row.eventSource }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="plateNo" label="车牌" width="92" />
            <el-table-column prop="eventTime" label="发生时间" width="160" />
            <el-table-column prop="speed" label="速度" width="70">
              <template #default="{ row }">{{ row.speed }} km/h</template>
            </el-table-column>
            <el-table-column label="等级" width="76">
              <template #default="{ row }">
                <el-tag :type="levelType(row.riskLevel)" size="small">
                  {{ levelText(row.riskLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="76">
              <template #default="{ row }">
                <el-tag :type="row.handleStatus === 1 ? 'success' : 'danger'" size="small">
                  {{ row.handleStatus === 1 ? '已处置' : '未处置' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="76" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.handleStatus === 0" link type="primary" @click="goHandle(row)">
                  去处理
                </el-button>
                <el-tooltip v-else :content="row.handleRemark || ''" placement="top">
                  <span style="color: #9ca3af">--</span>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="total"
            :page-size="filters.size"
            :current-page="filters.page"
            @current-change="onPageChange"
          />
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card style="margin-bottom: 12px">
          <template #header>事件类型分布</template>
          <div ref="chartRef" style="height: 280px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 视频分析任务 -->
    <el-card>
      <template #header>驾驶员监控视频分析任务</template>
      <el-table :data="videoAnalyses" size="small">
        <el-table-column prop="taskId" label="任务号" width="180" />
        <el-table-column prop="plateNo" label="车牌" width="100" />
        <el-table-column prop="channel" label="通道" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.channel === 'DSM' ? 'warning' : 'primary'">
              {{ row.channel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventCount" label="检出事件" width="90" />
        <el-table-column prop="resultSummary" label="分析结论" />
        <el-table-column prop="createDate" label="创建时间" width="170" />
      </el-table>
    </el-card>

    <OrderDetailDrawer v-model="drawerVisible" :order-id="activeOrderId" @refresh="loadRisks" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getOverview,
  getRiskTypeStats,
  getRisks,
  getVideoAnalyses,
  type Overview,
  type RiskEvent,
  type VideoAnalysis
} from '@/api/monitor'
import { ensureOrder, getOrderFunnel, listAllRules, type RiskRule, type OrderFunnel } from '@/api/risk'
import OrderDetailDrawer from './OrderDetailDrawer.vue'

const route = useRoute()

const overview = ref<Partial<Overview>>({})
const risks = ref<RiskEvent[]>([])
const total = ref(0)
const videoAnalyses = ref<VideoAnalysis[]>([])
const chartRef = ref<HTMLDivElement>()
const ruleOptions = ref<RiskRule[]>([])
const funnel = ref<OrderFunnel | null>(null)

const filters = reactive({
  page: 1,
  size: 10,
  eventSource: '',
  riskLevel: undefined as number | undefined,
  handleStatus: undefined as number | undefined,
  ruleId: undefined as number | undefined,
  plateNo: '',
  // 区域柱钻取：cityCode 参与过滤，cityName 仅用于展示标签
  cityCode: '',
  cityName: ''
})

const drawerVisible = ref(false)
const activeOrderId = ref<number | null>(null)

function levelText(level: number): string {
  return level === 3 ? '高' : level === 2 ? '中' : '低'
}

function levelType(level: number): 'danger' | 'warning' | 'success' {
  return level === 3 ? 'danger' : level === 2 ? 'warning' : 'success'
}

async function loadRisks() {
  const result = await getRisks({
    page: filters.page,
    size: filters.size,
    eventSource: filters.eventSource || undefined,
    riskLevel: filters.riskLevel,
    handleStatus: filters.handleStatus,
    ruleId: filters.ruleId,
    plateNo: filters.plateNo.trim() || undefined,
    cityCode: filters.cityCode || undefined
  })
  risks.value = result.records
  total.value = Number(result.total)
}

function onPageChange(page: number) {
  filters.page = page
  loadRisks()
}

async function goHandle(row: RiskEvent) {
  // 确保工单存在（无则补建），再打开详情抽屉
  try {
    const order = await ensureOrder(row.id)
    activeOrderId.value = Number(order.id)
    drawerVisible.value = true
  } catch (e) {
    ElMessage.error('创建工单失败')
  }
}

async function loadOverview() {
  overview.value = await getOverview()
}

async function loadChart() {
  const stats = await getRiskTypeStats()
  const chart = echarts.init(chartRef.value!)
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    color: ['#dc2626', '#f59e0b', '#2563eb', '#10b981', '#8b5cf6', '#6b7280'],
    series: [
      {
        name: '风险事件',
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '44%'],
        data: stats.length ? stats : [{ name: '暂无数据', value: 1 }],
        label: { fontSize: 11 }
      }
    ]
  })
}

onMounted(async () => {
  // 大屏钻取：从 route.query 恢复筛选条件（handleStatus/riskLevel/plateNo/cityCode）
  const q = route.query
  if (q.handleStatus !== undefined && q.handleStatus !== '' && !Number.isNaN(Number(q.handleStatus))) {
    filters.handleStatus = Number(q.handleStatus)
  }
  if (q.riskLevel !== undefined && q.riskLevel !== '' && !Number.isNaN(Number(q.riskLevel))) {
    filters.riskLevel = Number(q.riskLevel)
  }
  if (typeof q.plateNo === 'string' && q.plateNo) {
    filters.plateNo = q.plateNo
  }
  if (typeof q.cityCode === 'string' && q.cityCode) {
    filters.cityCode = q.cityCode
    filters.cityName = typeof q.cityName === 'string' ? q.cityName : ''
  }
  await Promise.all([loadOverview(), loadRisks(), loadChart()])
  videoAnalyses.value = await getVideoAnalyses()
  // 规则下拉需要 risk:rule:view 权限，无权限时静默降级
  try {
    ruleOptions.value = await listAllRules()
  } catch {
    ruleOptions.value = []
  }
  loadFunnel()
})

async function loadFunnel() {
  try {
    const start = new Date(new Date().setHours(0, 0, 0, 0)).toISOString().slice(0, 19)
    const end = new Date(new Date().setHours(0, 0, 0, 0) + 86400000).toISOString().slice(0, 19)
    funnel.value = await getOrderFunnel(start, end)
  } catch {
    funnel.value = null
  }
}
</script>

<style scoped>
.code-sub {
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.3;
}
.funnel {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.f-step {
  flex: 1;
  min-width: 110px;
  text-align: center;
  padding: 10px 8px;
  background: #f6f8fc;
  border: 1px solid #e3e8f0;
  border-radius: 8px;
}
.f-num {
  font-size: 22px;
  font-weight: 700;
  color: #1f5fbf;
}
.f-lbl {
  font-size: 12px;
  color: #667085;
  margin-top: 2px;
}
.f-arrow {
  color: #1f5fbf;
  font-size: 18px;
  font-weight: 700;
}
.f-rate {
  margin-left: auto;
  padding: 10px 16px;
  font-size: 13px;
  color: #667085;
  line-height: 1.8;
}
.f-rate strong {
  color: #1f5fbf;
  font-size: 15px;
}
</style>
