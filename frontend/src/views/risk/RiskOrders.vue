<template>
  <div class="page">
    <!-- 统计条 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="4">
        <el-card class="stat-card"><div class="value">{{ stats.pendingCount }}</div><div class="label">待处理</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="value" style="color:#d97706">{{ stats.processingCount }}</div><div class="label">处理中</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="value" style="color:#dc2626">{{ stats.overdueCount }}</div><div class="label">超时未闭环</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="value" style="color:#b91c1c">{{ stats.escalatedCount }}</div><div class="label">已升级</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="value" style="color:#15803d">{{ stats.closedTodayCount }}</div><div class="label">今日闭环</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card"><div class="value">{{ stats.avgCloseMinutes }}<span class="unit">分</span></div><div class="label">平均闭环时长</div></el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="工单号/车牌/事件名" clearable style="width: 200px" @keyup.enter="loadPage" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px">
          <el-option value="PENDING" label="待处理" />
          <el-option value="PROCESSING" label="处理中" />
          <el-option value="CLOSED" label="已闭环" />
        </el-select>
        <el-select v-model="filters.riskLevel" placeholder="风险等级" clearable style="width: 120px">
          <el-option :value="3" label="高风险" />
          <el-option :value="2" label="中风险" />
          <el-option :value="1" label="低风险" />
        </el-select>
        <el-select v-model="filters.closeResult" placeholder="处置结果" clearable style="width: 130px">
          <el-option value="PHONE_REMIND" label="电话提醒" />
          <el-option value="EDUCATION" label="安全教育" />
          <el-option value="REPORT_PENALTY" label="通报处罚" />
          <el-option value="TRAFFIC_VIOLATION" label="交通违法" />
          <el-option value="FALSE_ALARM" label="误报排除" />
        </el-select>
        <el-select v-model="filters.timeFlag" placeholder="时限档位" clearable style="width: 130px">
          <el-option value="due" label="临期" />
          <el-option value="overdue" label="已超时" />
          <el-option value="escalated" label="已升级" />
        </el-select>
        <el-select v-model="filters.assigneeId" placeholder="负责人" clearable filterable style="width: 160px">
          <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.name" />
        </el-select>
        <el-button type="primary" @click="loadPage">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'risk:order:assign'" style="margin-left:auto" @click="openSla">SLA 配置</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="orders" size="small">
        <el-table-column label="工单/事件" min-width="180">
          <template #default="{ row }">
            <div class="mono">{{ row.orderNo }}</div>
            <div>{{ row.eventTitle || row.eventCode }}</div>
            <div class="code-sub">{{ row.eventCode }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="plateNo" label="车牌" width="92" />
        <el-table-column label="等级" width="76">
          <template #default="{ row }">
            <el-tag :type="levelType(row.riskLevel)" size="small">{{ levelText(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventSource" label="来源" width="80">
          <template #default="{ row }"><el-tag size="small">{{ row.eventSource }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="eventTime" label="事件时间" width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            <el-tag v-if="row.escalated === 1" type="danger" size="small" effect="dark" style="margin-left:4px">升级</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="100">
          <template #default="{ row }">{{ row.assigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="剩余时限" width="110">
          <template #default="{ row }">
            <span :class="remainClass(row)">{{ remainText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 'CLOSED'" v-perm="'risk:order:handle'" link type="primary" @click="openDetail(row)">处理</el-button>
            <el-button v-if="row.status !== 'CLOSED' && !row.assigneeId" v-perm="'risk:order:handle'" link type="success" @click="quickClaim(row)">认领</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="filters.size"
        :current-page="filters.page"
        @current-change="onPage"
      />
    </el-card>

    <OrderDetailDrawer v-model="drawerVisible" :order-id="activeId" @refresh="refreshAll" />

    <!-- SLA 配置 -->
    <el-dialog v-model="slaVisible" title="工单 SLA 配置" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        修改后仅对新建工单生效，存量工单时限不变。
      </el-alert>
      <el-form label-width="100px">
        <el-form-item v-for="item in sla" :key="item.riskLevel" :label="levelText(item.riskLevel) + '风险'">
          <div class="sla-row">
            <el-input-number v-model="item.limitMin" :min="1" :max="10080" />
            <span>分钟处置时限</span>
            <el-divider direction="vertical" />
            <el-input-number v-model="item.graceMin" :min="0" :max="1440" />
            <span>分钟升级宽限</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="slaVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSla">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  claimOrder,
  getOrderSla,
  getOrderStats,
  listAssignableUsers,
  pageOrders,
  updateOrderSla,
  type AssignableUser,
  type OrderSla,
  type OrderStats,
  type RiskWorkOrder
} from '@/api/risk'
import OrderDetailDrawer from './OrderDetailDrawer.vue'

const route = useRoute()
const router = useRouter()

const orders = ref<RiskWorkOrder[]>([])
const total = ref(0)
const stats = ref<OrderStats>({
  pendingCount: 0, processingCount: 0, overdueCount: 0,
  escalatedCount: 0, closedTodayCount: 0, avgCloseSeconds: 0, avgCloseMinutes: 0
})
const users = ref<AssignableUser[]>([])
const sla = ref<OrderSla[]>([])
const slaVisible = ref(false)
const drawerVisible = ref(false)
const activeId = ref<number | null>(null)

const filters = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '' as '' | 'PENDING' | 'PROCESSING' | 'CLOSED',
  riskLevel: undefined as number | undefined,
  closeResult: '' as string,
  timeFlag: '' as '' | 'due' | 'overdue' | 'escalated',
  assigneeId: '' as string
})

function levelText(l: number) { return l === 3 ? '高' : l === 2 ? '中' : '低' }
function levelType(l: number): 'danger' | 'warning' | 'success' {
  return l === 3 ? 'danger' : l === 2 ? 'warning' : 'success'
}
function statusText(s: string) {
  return s === 'PENDING' ? '待处理' : s === 'PROCESSING' ? '处理中' : '已闭环'
}
function statusType(s: string): 'success' | 'warning' | 'info' {
  return s === 'PENDING' ? 'warning' : s === 'PROCESSING' ? '' : 'success'
}

const now = ref(Date.now())
setInterval(() => (now.value = Date.now()), 1000)

function remainMs(r: RiskWorkOrder) {
  if (!r.deadline) return 0
  return new Date(r.deadline).getTime() - now.value
}
function isOverdue(r: RiskWorkOrder) { return r.status !== 'CLOSED' && remainMs(r) < 0 }
function isEscalated(r: RiskWorkOrder) {
  return r.status !== 'CLOSED' && remainMs(r) - (r.graceMin || 0) * 60000 < 0
}
function isDue(r: RiskWorkOrder) {
  const lvl = r.slaLimitMin || 0
  return r.status !== 'CLOSED' && remainMs(r) > 0 && remainMs(r) < lvl * 60000 * 0.2
}
function remainClass(r: RiskWorkOrder) {
  return isEscalated(r) ? 'remain-escalated' : isOverdue(r) ? 'remain-overdue' : isDue(r) ? 'remain-due' : ''
}
function remainText(r: RiskWorkOrder) {
  if (r.status === 'CLOSED') return '已闭环'
  const ms = remainMs(r)
  if (ms < 0) return `超时 ${fmt(-ms)}`
  return fmt(ms)
}
function fmt(ms: number) {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}时${m}分`
  if (m > 0) return `${m}分${sec}秒`
  return `${sec}秒`
}

async function loadPage() {
  const r = await pageOrders({
    page: filters.page,
    size: filters.size,
    keyword: filters.keyword || undefined,
    status: filters.status || undefined,
    riskLevel: filters.riskLevel,
    closeResult: filters.closeResult || undefined,
    timeFlag: filters.timeFlag || undefined,
    assigneeId: filters.assigneeId || undefined
  })
  orders.value = r.records
  total.value = Number(r.total)
}

function onPage(p: number) { filters.page = p; loadPage() }

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.riskLevel = undefined
  filters.closeResult = ''
  filters.timeFlag = ''
  filters.assigneeId = ''
  filters.page = 1
  loadPage()
}

async function loadStats() {
  stats.value = await getOrderStats()
}

async function loadUsers() {
  try { users.value = await listAssignableUsers() } catch { users.value = [] }
}

function openDetail(row: RiskWorkOrder) {
  activeId.value = Number(row.id)
  drawerVisible.value = true
}

// 已在页面内时再次点击通知（query 变化）也自动打开对应工单
watch(
  () => route.query.openOrder,
  (q) => {
    if (!q) return
    const id = Number(q)
    if (!Number.isNaN(id)) {
      activeId.value = id
      drawerVisible.value = true
    }
    router.replace({ path: '/risk/orders' })
  }
)

async function quickClaim(row: RiskWorkOrder) {
  await claimOrder(Number(row.id))
  ElMessage.success('已认领')
  refreshAll()
}

function refreshAll() {
  Promise.all([loadPage(), loadStats()])
}

function openSla() {
  getOrderSla().then(list => {
    sla.value = JSON.parse(JSON.stringify(list))
    slaVisible.value = true
  })
}

async function saveSla() {
  await updateOrderSla(sla.value)
  ElMessage.success('SLA 已更新（仅对新工单生效）')
  slaVisible.value = false
}

onMounted(() => {
  // 大屏钻取：从 route.query 恢复筛选条件（status/timeFlag）
  const q = route.query
  if (q.status === 'PENDING' || q.status === 'PROCESSING' || q.status === 'CLOSED') {
    filters.status = q.status
  }
  if (q.timeFlag === 'due' || q.timeFlag === 'overdue' || q.timeFlag === 'escalated') {
    filters.timeFlag = q.timeFlag
  }
  Promise.all([loadPage(), loadStats(), loadUsers()]).then(() => {
    // F19 通知跳转：携带 openOrder=<工单id> 时自动打开详情抽屉
    const q = route.query.openOrder
    if (q) {
      const id = Number(q)
      if (!Number.isNaN(id)) {
        activeId.value = id
        drawerVisible.value = true
      }
      router.replace({ path: '/risk/orders' })
    }
  })
})
</script>

<style scoped>
.mono { font-family: monospace; font-size: 12px; color: #6b7280; }
.code-sub { font-size: 11px; color: #9ca3af; line-height: 1.3; }
.remain-due { color: #d97706; font-weight: 600; }
.remain-overdue { color: #dc2626; font-weight: 600; }
.remain-escalated { color: #b91c1c; font-weight: 600; }
.sla-row { display: flex; align-items: center; gap: 8px; }
.unit { font-size: 13px; color: #9ca3af; }
</style>
