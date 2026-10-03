<template>
  <div class="audit-page">
    <!-- 筛选区 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD HH:mm:ss"
          :default-time="defaultTime"
          style="width: 360px"
        />
        <el-input v-model="filters.userName" placeholder="操作人账号" clearable style="width: 130px" @keyup.enter="search" />
        <el-select v-model="filters.module" placeholder="模块" clearable style="width: 130px">
          <el-option v-for="m in AUDIT_MODULES" :key="m.value" :value="m.value" :label="m.label" />
        </el-select>
        <el-select v-model="filters.action" placeholder="动作" clearable style="width: 130px">
          <el-option v-for="a in AUDIT_ACTIONS" :key="a.value" :value="a.value" :label="a.label" />
        </el-select>
        <el-select v-model="filters.status" placeholder="结果" clearable style="width: 110px">
          <el-option v-for="s in AUDIT_STATUS" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="URI / 对象ID / 错误信息" clearable style="width: 200px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <!-- 概览统计 -->
    <div class="stat-row" v-loading="statsLoading">
      <div class="stat-card">
        <div class="num">{{ stats?.total ?? 0 }}</div>
        <div class="lbl">区间操作总量</div>
      </div>
      <div class="stat-card">
        <div class="num">{{ stats?.successRate ?? 100 }}%</div>
        <div class="lbl">成功率（成功 {{ stats?.successCount ?? 0 }}）</div>
      </div>
      <div class="stat-card">
        <div class="num red">{{ stats?.loginFailCount ?? 0 }}</div>
        <div class="lbl">登录失败次数</div>
      </div>
      <div class="stat-card">
        <div class="num">{{ stats?.todayCount ?? 0 }}</div>
        <div class="lbl">今日操作数</div>
      </div>
      <div class="stat-card dist">
        <div class="lbl" style="margin-bottom: 4px">动作分布 Top5</div>
        <div v-if="stats && stats.actionDist.length" class="dist-list">
          <div v-for="d in stats.actionDist.slice(0, 5)" :key="d.name" class="dist-item">
            <span>{{ actionLabel(d.name) }}</span>
            <b>{{ d.count }}</b>
          </div>
        </div>
        <span v-else style="color: #9ca3af; font-size: 12px">暂无数据</span>
      </div>
    </div>

    <!-- 日志表格 -->
    <el-card>
      <el-table :data="records" size="small" v-loading="loading" :row-class-name="rowClassName">
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column label="用户" width="110">
          <template #default="{ row }">
            <span v-if="row.userName">{{ row.userName }}</span>
            <span v-else class="anon">匿名</span>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ moduleLabel(row.module) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTagType(row.action)">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="150">
          <template #default="{ row }">
            <span v-if="row.objectType">{{ row.objectType }}: {{ row.objectId }}</span>
            <span v-else style="color: #9ca3af">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="requestMethod" label="方法" width="70" />
        <el-table-column prop="clientIp" label="来源IP" width="120">
          <template #default="{ row }">{{ row.clientIp || '--' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ row.costMs == null ? '--' : row.costMs + 'ms' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="filters.page"
        :page-size="filters.size"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="审计日志详情" size="560px">
      <div v-loading="detailLoading" class="detail-wrap">
        <template v-if="detail">
          <h4>基本信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="时间">{{ detail.createTime }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ detail.userName || '匿名（未认证）' }}</el-descriptions-item>
            <el-descriptions-item label="模块 / 动作">
              {{ moduleLabel(detail.module) }} / {{ actionLabel(detail.action) }}
            </el-descriptions-item>
            <el-descriptions-item label="对象">
              {{ detail.objectType ? detail.objectType + ': ' + detail.objectId : '--' }}
            </el-descriptions-item>
            <el-descriptions-item label="追踪ID">{{ detail.traceId || '--' }}</el-descriptions-item>
          </el-descriptions>

          <h4>请求信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="方法 / 路径">
              {{ detail.requestMethod }} {{ detail.requestUri }}
            </el-descriptions-item>
            <el-descriptions-item label="查询参数">{{ detail.queryString || '--' }}</el-descriptions-item>
            <el-descriptions-item label="请求体（已脱敏）">
              <pre v-if="detail.requestBody" class="json-box">{{ prettyJson(detail.requestBody) }}</pre>
              <span v-else style="color: #9ca3af">无</span>
            </el-descriptions-item>
          </el-descriptions>

          <h4>结果信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="结果">
              <el-tag size="small" :type="detail.status === 1 ? 'success' : 'danger'">
                {{ detail.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="业务码">{{ detail.resultCode ?? '--' }}</el-descriptions-item>
            <el-descriptions-item label="错误消息">
              <span class="error-text">{{ detail.errorMsg || '--' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="耗时">{{ detail.costMs ?? '--' }} ms</el-descriptions-item>
          </el-descriptions>

          <h4>终端信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="来源 IP">{{ detail.clientIp || '--' }}</el-descriptions-item>
            <el-descriptions-item label="设备信息">{{ detail.userAgent || '--' }}</el-descriptions-item>
            <el-descriptions-item label="内容指纹">
              <span class="hash">{{ detail.contentHash || '--' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  pageAuditLogs,
  getAuditLog,
  getAuditStats,
  type AuditLog,
  type AuditLogDetail,
  type AuditStats
} from '@/api/audit'
import {
  AUDIT_MODULES,
  AUDIT_ACTIONS,
  AUDIT_STATUS,
  labelOf
} from '@/constants/dict'

const defaultTime = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59)
]

function initialRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  return [formatTime(start, false), formatTime(end, true)]
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function formatTime(d: Date, endOfDay: boolean): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + (endOfDay ? '23:59:59' : '00:00:00')
}

const dateRange = ref<[string, string] | undefined>(initialRange())
const filters = reactive({
  userName: '',
  module: '',
  action: '',
  status: null as number | null,
  keyword: '',
  page: 1,
  size: 10
})

const loading = ref(false)
const records = ref<AuditLog[]>([])
const total = ref(0)

const statsLoading = ref(false)
const stats = ref<AuditStats | null>(null)

function queryParams() {
  return {
    startTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1],
    userName: filters.userName || undefined,
    module: filters.module || undefined,
    action: filters.action || undefined,
    status: filters.status,
    keyword: filters.keyword || undefined,
    page: filters.page,
    size: filters.size
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await pageAuditLogs(queryParams())
    records.value = data.records
    total.value = data.total
  } catch {
    // 错误提示已由 http 拦截器统一处理，保证页面可用
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getAuditStats({
      startTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1]
    })
  } catch {
    // 统计失败不影响明细检索
    stats.value = null
  } finally {
    statsLoading.value = false
  }
}

function search() {
  filters.page = 1
  loadList()
  loadStats()
}

function resetFilters() {
  dateRange.value = initialRange()
  filters.userName = ''
  filters.module = ''
  filters.action = ''
  filters.status = null
  filters.keyword = ''
  filters.page = 1
  loadList()
  loadStats()
}

function onPageChange(p: number) {
  filters.page = p
  loadList()
}

function onSizeChange(size: number) {
  filters.size = size
  filters.page = 1
  loadList()
}

function rowClassName({ row }: { row: AuditLog }) {
  return row.status === 0 ? 'row-fail' : ''
}

function moduleLabel(value: string) {
  return labelOf(AUDIT_MODULES, value)
}

function actionLabel(value: string) {
  return labelOf(AUDIT_ACTIONS, value)
}

function actionTagType(action: string) {
  const tag = AUDIT_ACTIONS.find((a) => a.value === action)?.tag
  if (tag === 'primary') return ''
  return tag ?? 'info'
}

// 详情
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AuditLogDetail | null>(null)

async function openDetail(id: string | number) {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await getAuditLog(id)
  } finally {
    detailLoading.value = false
  }
}

function prettyJson(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onMounted(() => {
  loadList()
  loadStats()
})
</script>

<style scoped>
.audit-page {
  padding: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.stat-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.stat-card {
  flex: 1;
  background: #fff;
  border: 1px solid #e3e8f0;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(16, 42, 90, 0.05);
}
.stat-card.dist {
  flex: 1.4;
}
.stat-card .num {
  font-size: 26px;
  font-weight: 700;
  color: #174a96;
  line-height: 1.2;
}
.stat-card .num.red {
  color: #b91c1c;
}
.stat-card .lbl {
  font-size: 12.5px;
  color: #667085;
}
.dist-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dist-item {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  color: #44506a;
}
.anon {
  color: #9ca3af;
}
.detail-wrap h4 {
  margin: 18px 0 10px;
  color: #174a96;
  font-size: 14px;
}
.detail-wrap h4:first-child {
  margin-top: 0;
}
.json-box {
  background: #f6f8fa;
  border-radius: 4px;
  padding: 8px 10px;
  margin: 0;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.error-text {
  color: #b91c1c;
}
.hash {
  font-family: Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}
:deep(.el-table .row-fail) {
  background-color: #fef6f6;
}
:deep(.el-table .row-fail:hover > td) {
  background-color: #feecec !important;
}
</style>
