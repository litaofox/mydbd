<template>
  <el-drawer v-model="visible" title="工单详情" direction="rtl" size="780px" @opened="onOpened">
    <template v-if="detail">
      <!-- 状态头 -->
      <div class="status-bar">
        <div class="status-left">
          <el-tag :type="statusTagType" size="large">{{ statusText }}</el-tag>
          <span class="order-no">{{ detail.order.orderNo }}</span>
          <el-tag v-if="isEscalated" type="danger" size="small" effect="dark">已升级</el-tag>
          <el-tag v-else-if="isOverdue" type="danger" size="small">已超时</el-tag>
          <el-tag v-else-if="isDue" type="warning" size="small">临期</el-tag>
        </div>
        <div class="status-right">
          <span class="sla-text">
            时限：{{ detail.order.slaLimitMin }} 分钟｜宽限：{{ detail.order.graceMin }} 分钟
          </span>
          <span :class="['remain', remainClass]">{{ remainText }}</span>
        </div>
      </div>

      <!-- 事件快照 -->
      <el-card class="block">
        <template #header>事件快照</template>
        <el-descriptions :column="3" size="small" border>
          <el-descriptions-item label="事件名称">
            {{ detail.order.eventTitle || detail.order.eventCode }}
          </el-descriptions-item>
          <el-descriptions-item label="事件码">{{ detail.order.eventCode }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.order.eventSource }}</el-descriptions-item>
          <el-descriptions-item label="车牌">{{ detail.order.plateNo }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="levelType">{{ levelText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ detail.order.eventTime }}</el-descriptions-item>
          <el-descriptions-item label="位置" :span="2">
            <span v-if="detail.event?.lng">{{ detail.event.lng }}, {{ detail.event.lat }}</span>
            <span v-else>--</span>
          </el-descriptions-item>
          <el-descriptions-item label="速度">
            {{ detail.event?.speed ?? '-' }} km/h
          </el-descriptions-item>
          <el-descriptions-item label="媒体" v-if="detail.event?.mediaUrl">
            <a :href="detail.event.mediaUrl" target="_blank">查看</a>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 处置信息 -->
      <el-card class="block">
        <template #header>处置信息</template>
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="负责人">
            {{ detail.order.assigneeName || '未指派' }}
          </el-descriptions-item>
          <el-descriptions-item label="分派时间">
            {{ detail.order.assignTime || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="认领时间">
            {{ detail.order.claimTime || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="闭环时间">
            {{ detail.order.closeTime || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="处置结果" v-if="detail.order.closeResult">
            {{ resultName(detail.order.closeResult) }}
          </el-descriptions-item>
          <el-descriptions-item label="闭环说明" v-if="detail.order.closeRemark" :span="2">
            {{ detail.order.closeRemark }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 流转时间线 -->
      <el-card class="block">
        <template #header>流转记录</template>
        <el-timeline>
          <el-timeline-item
            v-for="log in detail.logs"
            :key="log.id"
            :type="timelineType(log.action)"
            :timestamp="log.createDate"
            placement="top"
          >
            <div class="log-line">
              <strong>{{ actionText(log.action) }}</strong>
              <span class="op">· {{ log.operatorName }}</span>
              <span v-if="log.remark" class="rmk">· {{ log.remark }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <!-- 干预记录（F21） -->
      <el-card class="block">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>干预记录</span>
            <el-button v-perm="'risk:order:handle'" type="primary" size="small" @click="openIntervention">新增干预</el-button>
          </div>
        </template>
        <el-table :data="interventions" size="small" v-if="interventions.length">
          <el-table-column prop="createDate" label="时间" width="160" />
          <el-table-column label="动作" width="110">
            <template #default="{ row }">{{ actionTypeName(row.actionType) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.actionResult === 'SUCCESS' ? 'success' : row.actionResult === 'NO_ANSWER' ? 'warning' : 'info'" size="small">
                {{ resultTypeName(row.actionResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="坐席" width="100" />
          <el-table-column label="来源" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.source === 'SYSTEM'" type="warning" size="small">系统</el-tag>
              <el-tag v-else size="small">手动</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" show-overflow-tooltip />
        </el-table>
        <el-empty v-else description="暂无干预记录" :image-size="60" />
      </el-card>

      <!-- 新增干预弹窗 -->
      <el-dialog v-model="ivDialog" title="新增干预记录" width="460px">
        <el-form :model="ivForm" label-width="80px">
          <el-form-item label="动作类型">
            <el-select v-model="ivForm.actionType" style="width:100%">
              <el-option label="电话提醒" value="PHONE_REMIND" />
              <el-option label="安全教育" value="EDUCATION" />
              <el-option label="停运通知" value="STOP" />
              <el-option label="处罚" value="PENALTY" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="ivForm.actionResult" style="width:100%">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="未接通" value="NO_ANSWER" />
              <el-option label="失败" value="FAILED" />
              <el-option label="待跟进" value="PENDING" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="ivForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="ivDialog = false">取消</el-button>
          <el-button type="primary" @click="saveIntervention">保存</el-button>
        </template>
      </el-dialog>

      <!-- 操作区 -->
      <el-card class="block" v-if="detail.order.status !== 'CLOSED'">
        <template #header>工单操作</template>
        <el-row :gutter="12">
          <el-col :span="12" v-if="!detail.order.assigneeId">
            <el-button type="primary" @click="onClaim" v-perm="'risk:order:handle'">认领工单</el-button>
          </el-col>
          <el-col :span="12" v-perm="'risk:order:assign'">
            <div class="action-row">
              <el-select v-model="assignForm.userId" placeholder="选择负责人" filterable style="width: 200px">
                <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.name" />
              </el-select>
              <el-input v-model="assignForm.remark" placeholder="分派备注（可选）" style="width: 200px" />
              <el-button type="primary" @click="onAssign">分派</el-button>
            </div>
          </el-col>
          <el-col :span="12" v-if="detail.order.assigneeId" v-perm="'risk:order:handle'">
            <div class="action-row">
              <el-select v-model="transferForm.userId" placeholder="转派给" filterable style="width: 200px">
                <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.name" />
              </el-select>
              <el-input v-model="transferForm.remark" placeholder="转派原因" style="width: 200px" />
              <el-button type="warning" @click="onTransfer">转派</el-button>
            </div>
          </el-col>
        </el-row>

        <el-divider />

        <div class="close-block" v-perm="'risk:order:handle'">
          <el-form label-position="top">
            <el-form-item label="处置结果">
              <el-radio-group v-model="closeForm.result">
                <el-radio value="PHONE_REMIND">电话提醒</el-radio>
                <el-radio value="EDUCATION">安全教育</el-radio>
                <el-radio value="REPORT_PENALTY">通报处罚</el-radio>
                <el-radio value="TRAFFIC_VIOLATION">交通违法</el-radio>
                <el-radio value="FALSE_ALARM">误报排除</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="闭环说明（10~255 字）">
              <el-input v-model="closeForm.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
            </el-form-item>
            <el-button type="success" @click="onClose">确认闭环</el-button>
          </el-form>
        </div>
      </el-card>

      <el-card class="block" v-else>
        <template #header>已闭环</template>
        <el-button type="warning" v-perm="'risk:order:assign'" @click="showReopen = true">
          重新打开（主管）
        </el-button>
        <el-dialog v-model="showReopen" title="重新打开工单" width="420px">
          <el-input v-model="reopenRemark" type="textarea" :rows="3" placeholder="重开原因（5~255 字）" />
          <template #footer>
            <el-button @click="showReopen = false">取消</el-button>
            <el-button type="warning" @click="onReopen">确认重开</el-button>
          </template>
        </el-dialog>
      </el-card>
    </template>
    <el-empty v-else description="加载中..." />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addIntervention,
  assignOrder,
  claimOrder,
  closeOrder,
  getInterventions,
  getOrder,
  listAssignableUsers,
  reopenOrder,
  transferOrder,
  type AssignableUser,
  type CloseResult,
  type InterventionAction,
  type InterventionResult,
  type OrderDetail,
  type RiskIntervention
} from '@/api/risk'

const props = defineProps<{ modelValue: boolean; orderId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'refresh'): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const detail = ref<OrderDetail | null>(null)
const users = ref<AssignableUser[]>([])
const showReopen = ref(false)
const reopenRemark = ref('')
const assignForm = ref({ userId: '', remark: '' })
const transferForm = ref({ userId: '', remark: '' })
const closeForm = ref({ result: '' as CloseResult | '', remark: '' })
const interventions = ref<RiskIntervention[]>([])
const ivDialog = ref(false)
const ivForm = ref({ actionType: 'PHONE_REMIND' as InterventionAction, actionResult: 'SUCCESS' as InterventionResult, remark: '' })

const ACTION_NAMES: Record<string, string> = {
  PHONE_REMIND: '电话提醒',
  EDUCATION: '安全教育',
  STOP: '停运通知',
  PENALTY: '处罚',
  OTHER: '其他'
}
const RESULT_TYPE_NAMES: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  NO_ANSWER: '未接通',
  PENDING: '待跟进'
}
function actionTypeName(t: string) { return ACTION_NAMES[t] || t }
function resultTypeName(r: string) { return RESULT_TYPE_NAMES[r] || r }

const RESULT_NAMES: Record<string, string> = {
  PHONE_REMIND: '电话提醒',
  EDUCATION: '安全教育',
  REPORT_PENALTY: '通报处罚',
  TRAFFIC_VIOLATION: '交通违法',
  FALSE_ALARM: '误报排除'
}

const statusText = computed(() => {
  const s = detail.value?.order.status
  return s === 'PENDING' ? '待处理' : s === 'PROCESSING' ? '处理中' : '已闭环'
})
const statusTagType = computed<'success' | 'warning' | 'info'>(() => {
  const s = detail.value?.order.status
  return s === 'PENDING' ? 'warning' : s === 'PROCESSING' ? '' : 'success'
})

const levelText = computed(() => {
  const l = detail.value?.order.riskLevel
  return l === 3 ? '高' : l === 2 ? '中' : '低'
})
const levelType = computed<'danger' | 'warning' | 'success'>(() => {
  const l = detail.value?.order.riskLevel
  return l === 3 ? 'danger' : l === 2 ? 'warning' : 'success'
})

const now = ref(Date.now())
setInterval(() => (now.value = Date.now()), 1000)
const remainMs = computed(() => {
  if (!detail.value?.order.deadline) return 0
  return new Date(detail.value.order.deadline).getTime() - now.value
})
const isOverdue = computed(() => detail.value?.order.status !== 'CLOSED' && remainMs.value < 0)
const isDue = computed(() => {
  const lvl = detail.value?.order.slaLimitMin ?? 0
  return (
    detail.value?.order.status !== 'CLOSED' &&
    remainMs.value > 0 &&
    remainMs.value < lvl * 60000 * 0.2
  )
})
const escalateMs = computed(() => {
  const g = detail.value?.order.graceMin ?? 0
  return remainMs.value - g * 60000
})
const isEscalated = computed(() => detail.value?.order.status !== 'CLOSED' && escalateMs.value < 0)

const remainClass = computed(() =>
  isEscalated.value ? 'remain-escalated' : isOverdue.value ? 'remain-overdue' : isDue.value ? 'remain-due' : ''
)
const remainText = computed(() => {
  const ms = remainMs.value
  if (detail.value?.order.status === 'CLOSED') return '已闭环'
  if (ms < 0) return `超时 ${formatMs(-ms)}`
  return `剩余 ${formatMs(ms)}`
})

function formatMs(ms: number): string {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}时${m}分`
  if (m > 0) return `${m}分${sec}秒`
  return `${sec}秒`
}

function resultName(r: string) {
  return RESULT_NAMES[r] || r
}

function actionText(a: string): string {
  return (
    {
      CREATE: '创建工单',
      ASSIGN: '分派',
      CLAIM: '认领',
      TRANSFER: '转派',
      CLOSE: '闭环',
      REOPEN: '重新打开',
      ESCALATE: '自动升级'
    } as Record<string, string>
  )[a] || a
}

function timelineType(a: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (a === 'CLOSE') return 'success'
  if (a === 'ESCALATE') return 'danger'
  if (a === 'REOPEN') return 'warning'
  if (a === 'CLAIM') return 'primary'
  return 'info'
}

async function load() {
  if (!props.orderId) return
  detail.value = null
  detail.value = await getOrder(props.orderId)
}

async function onOpened() {
  await Promise.all([load(), loadUsers()])
  await loadInterventions()
}

async function loadInterventions() {
  if (!props.orderId) { interventions.value = []; return }
  try {
    interventions.value = await getInterventions(props.orderId)
  } catch {
    interventions.value = []
  }
}

function openIntervention() {
  ivForm.value = { actionType: 'PHONE_REMIND', actionResult: 'SUCCESS', remark: '' }
  ivDialog.value = true
}

async function saveIntervention() {
  if (!props.orderId) return
  await addIntervention({
    orderId: props.orderId,
    actionType: ivForm.value.actionType,
    actionResult: ivForm.value.actionResult,
    remark: ivForm.value.remark
  })
  ElMessage.success('已记录')
  ivDialog.value = false
  await loadInterventions()
}

async function loadUsers() {
  try {
    users.value = await listAssignableUsers()
  } catch {
    users.value = []
  }
}

async function onClaim() {
  if (!props.orderId) return
  await claimOrder(props.orderId)
  ElMessage.success('已认领')
  await load()
  emit('refresh')
}

async function onAssign() {
  if (!props.orderId || !assignForm.value.userId) {
    ElMessage.warning('请选择负责人')
    return
  }
  await assignOrder(props.orderId, assignForm.value)
  ElMessage.success('已分派')
  assignForm.value = { userId: '', remark: '' }
  await load()
  emit('refresh')
}

async function onTransfer() {
  if (!props.orderId) return
  if (!transferForm.value.userId || !transferForm.value.remark) {
    ElMessage.warning('请填写转派对象与原因')
    return
  }
  await transferOrder(props.orderId, transferForm.value)
  ElMessage.success('已转派')
  transferForm.value = { userId: '', remark: '' }
  await load()
  emit('refresh')
}

async function onClose() {
  if (!props.orderId) return
  if (!closeForm.value.result) {
    ElMessage.warning('请选择处置结果')
    return
  }
  const rm = closeForm.value.remark.trim()
  if (rm.length < 10) {
    ElMessage.warning('闭环说明至少 10 字')
    return
  }
  await closeOrder(props.orderId, { result: closeForm.value.result as CloseResult, remark: rm })
  ElMessage.success('工单已闭环')
  closeForm.value = { result: '', remark: '' }
  await load()
  await loadInterventions()
  emit('refresh')
}

async function onReopen() {
  if (!props.orderId) return
  const rm = reopenRemark.value.trim()
  if (rm.length < 5) {
    ElMessage.warning('重开原因至少 5 字')
    return
  }
  await reopenOrder(props.orderId, { remark: rm })
  ElMessage.success('已重新打开')
  showReopen.value = false
  reopenRemark.value = ''
  await load()
  emit('refresh')
}

watch(
  () => props.orderId,
  () => {
    if (props.modelValue) load()
  }
)
</script>

<style scoped>
.block {
  margin-bottom: 12px;
}
.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: #f6f8fc;
  border-radius: 8px;
  border: 1px solid #e3e8f0;
}
.status-left {
  display: flex;
  gap: 8px;
  align-items: center;
}
.order-no {
  font-family: monospace;
  color: #4b5563;
  font-size: 13px;
}
.status-right {
  text-align: right;
  font-size: 12px;
  color: #6b7280;
}
.sla-text {
  display: block;
}
.remain {
  display: inline-block;
  margin-top: 3px;
  font-size: 13px;
  font-weight: 600;
}
.remain-due {
  color: #d97706;
}
.remain-overdue {
  color: #dc2626;
}
.remain-escalated {
  color: #b91c1c;
}
.action-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.close-block {
  max-width: 620px;
}
.log-line {
  font-size: 13px;
}
.log-line .op {
  color: #6b7280;
}
.log-line .rmk {
  color: #4b5563;
}
</style>
