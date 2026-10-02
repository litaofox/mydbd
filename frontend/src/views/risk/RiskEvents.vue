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
        <el-button type="primary" @click="loadRisks">查询</el-button>
      </div>
    </el-card>

    <!-- 列表 + 分布图 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="16">
        <el-card>
          <template #header>风险预警事件</template>
          <el-table :data="risks" size="small">
            <el-table-column prop="eventCode" label="事件码" width="150" />
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
                <el-button v-if="row.handleStatus === 0" link type="primary" @click="openHandle(row)">
                  处置
                </el-button>
                <span v-else style="color: #9ca3af">--</span>
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

    <!-- 处置对话框 -->
    <el-dialog v-model="dialogVisible" title="风险事件处置" width="460px">
      <div style="margin-bottom: 10px; color: #4b5563; font-size: 13px">
        {{ active?.eventCode }} ｜ {{ active?.plateNo }} ｜ {{ active?.eventTime }}
      </div>
      <el-input v-model="handleRemark" type="textarea" :rows="4" placeholder="请填写处置意见（如：电话提醒驾驶员休息、误报标记等）" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getOverview,
  getRiskTypeStats,
  getRisks,
  getVideoAnalyses,
  handleRisk,
  type Overview,
  type RiskEvent,
  type VideoAnalysis
} from '@/api/monitor'

const overview = ref<Partial<Overview>>({})
const risks = ref<RiskEvent[]>([])
const total = ref(0)
const videoAnalyses = ref<VideoAnalysis[]>([])
const chartRef = ref<HTMLDivElement>()

const filters = reactive({
  page: 1,
  size: 10,
  eventSource: '',
  riskLevel: undefined as number | undefined,
  handleStatus: undefined as number | undefined
})

const dialogVisible = ref(false)
const active = ref<RiskEvent | null>(null)
const handleRemark = ref('')
const submitting = ref(false)

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
    handleStatus: filters.handleStatus
  })
  risks.value = result.records
  total.value = Number(result.total)
}

function onPageChange(page: number) {
  filters.page = page
  loadRisks()
}

function openHandle(row: RiskEvent) {
  active.value = row
  handleRemark.value = ''
  dialogVisible.value = true
}

async function submitHandle() {
  if (!handleRemark.value.trim()) {
    ElMessage.warning('请填写处置意见')
    return
  }
  submitting.value = true
  try {
    await handleRisk(active.value!.id, handleRemark.value.trim())
    ElMessage.success('处置完成')
    dialogVisible.value = false
    await Promise.all([loadRisks(), loadOverview()])
  } finally {
    submitting.value = false
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
  await Promise.all([loadOverview(), loadRisks(), loadChart()])
  videoAnalyses.value = await getVideoAnalyses()
})
</script>
