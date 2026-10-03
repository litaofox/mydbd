<template>
  <div class="mdm-page">
    <!-- 引擎统计条 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value">{{ summary.ruleEnabled ?? '-' }} <span class="unit">/ {{ summary.ruleTotal ?? '-' }}</span></div>
          <div class="label">启用 / 全部规则</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value">{{ summary.fenceEnabled ?? '-' }} <span class="unit">/ {{ summary.fenceTotal ?? '-' }}</span></div>
          <div class="label">启用 / 全部围栏</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="value" style="color: #dc2626">{{ summary.todayEventTotal ?? '-' }}</div>
          <div class="label">
            今日事件
            <span v-if="todayLevelText" class="muted">（{{ todayLevelText }}）</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card top-card">
          <div v-if="!summary.todayTopRules?.length" class="value muted" style="font-size: 14px">今日暂无规则事件</div>
          <div v-for="t in summary.todayTopRules" :key="String(t.rule_id)" class="top-line">
            <span class="top-name">{{ t.title || ('规则#' + t.rule_id) }}</span>
            <el-tag size="small" type="danger">{{ t.cnt }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.ruleCode" placeholder="规则编码" clearable style="width: 180px" @keyup.enter="search" />
        <el-input v-model="filters.ruleName" placeholder="规则名称" clearable style="width: 180px" @keyup.enter="search" />
        <el-select v-model="filters.ruleType" placeholder="规则类型" clearable style="width: 150px">
          <el-option v-for="t in RULE_TYPES" :key="t.value" :value="t.value" :label="t.label" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'risk:rule:edit'" type="success" @click="openCreate">新增规则</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="ruleCode" label="规则编码" width="180" />
        <el-table-column prop="ruleName" label="规则名称" width="170" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.ruleType)">{{ typeLabel(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发条件" min-width="250">
          <template #default="{ row }">
            <span class="muted">{{ paramsText(row) }}</span>
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
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.builtIn === 1" size="small" type="info">内置</el-tag>
            <span v-else class="muted">自定义</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="76">
          <template #default="{ row }">
            <el-switch
              v-perm="'risk:rule:edit'"
              :model-value="row.status === 1"
              @change="(v: boolean) => onToggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'risk:rule:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.builtIn !== 1"
              v-perm="'risk:rule:edit'"
              link
              type="danger"
              size="small"
              @click="onDelete(row)"
            >
              删除
            </el-button>
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
    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑风控规则' : '新增风控规则'" size="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="form.ruleType" :disabled="!!form.id" style="width: 100%" @change="onTypeChange">
            <el-option v-for="t in RULE_TYPES" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则编码" prop="ruleCode">
          <el-select
            v-if="form.ruleType === 'SIGNAL'"
            v-model="form.ruleCode"
            :disabled="!!form.id"
            style="width: 100%"
            placeholder="选择终端信号码"
          >
            <el-option v-for="s in SIGNAL_OPTIONS" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
          <el-input
            v-else
            v-model="form.ruleCode"
            :disabled="!!form.id"
            maxlength="40"
            placeholder="大写字母开头，3~40 位大写字母/数字/下划线"
          />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" maxlength="50" />
        </el-form-item>

        <el-divider content-position="left">触发参数</el-divider>

        <!-- SPEED -->
        <template v-if="form.ruleType === 'SPEED'">
          <el-form-item label="超速阈值" required>
            <el-input-number v-model="form.params.speedKmh" :min="1" :max="220" :step="5" />
            <span class="muted" style="margin-left: 8px">km/h，点位速度达到即触发</span>
          </el-form-item>
        </template>

        <!-- FATIGUE -->
        <template v-else-if="form.ruleType === 'FATIGUE'">
          <el-form-item label="连续驾驶上限" required>
            <el-input-number v-model="form.params.continuousMin" :min="1" :max="1440" :step="10" />
            <span class="muted" style="margin-left: 8px">分钟</span>
          </el-form-item>
          <el-form-item label="中断间隔" required>
            <el-input-number v-model="form.params.gapMin" :min="1" :max="120" :step="5" />
            <span class="muted" style="margin-left: 8px">分钟，间隔小于该值视为连续</span>
          </el-form-item>
        </template>

        <!-- COMBO -->
        <template v-else-if="form.ruleType === 'COMBO'">
          <el-form-item label="统计窗口" required>
            <el-input-number v-model="form.params.windowMin" :min="1" :max="720" :step="5" />
            <span class="muted" style="margin-left: 8px">分钟</span>
          </el-form-item>
          <el-form-item label="超速阈值" required>
            <el-input-number v-model="form.params.speedKmh" :min="1" :max="220" :step="5" />
            <span class="muted" style="margin-left: 8px">km/h，窗口内存在疲劳会话且超速即触发</span>
          </el-form-item>
        </template>

        <!-- SIGNAL -->
        <el-form-item v-else-if="form.ruleType === 'SIGNAL'">
          <el-alert type="info" :closable="false" show-icon
                    title="信号规则无阈值参数：终端 DSM/ADAS 通道上报所选信号码即按本规则等级与冷却策略落事件。" />
        </el-form-item>

        <el-divider content-position="left">处置策略</el-divider>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="form.riskLevel">
            <el-radio :value="1">低风险</el-radio>
            <el-radio :value="2">中风险</el-radio>
            <el-radio :value="3">高风险</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="冷却时间" prop="cooldownSec">
          <el-input-number v-model="form.cooldownSec" :min="0" :max="86400" :step="60" />
          <span class="muted" style="margin-left: 8px">秒，同车同规则冷却期内不重复告警（0=不去重）</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageRules,
  getRule,
  createRule,
  updateRule,
  changeRuleStatus,
  deleteRule,
  getEngineSummary,
  type EngineSummary,
  type RiskRule
} from '@/api/risk'

const RULE_TYPES = [
  { value: 'SPEED', label: '超速规则' },
  { value: 'FATIGUE', label: '疲劳驾驶' },
  { value: 'SIGNAL', label: '终端信号' },
  { value: 'COMBO', label: '组合规则' }
] as const

const SIGNAL_OPTIONS = [
  { value: 'DSM_FATIGUE', label: 'DSM_FATIGUE（DSM 疲劳）' },
  { value: 'DSM_DISTRACTION', label: 'DSM_DISTRACTION（DSM 分心）' },
  { value: 'ADAS_FCW', label: 'ADAS_FCW（前向碰撞）' },
  { value: 'ADAS_LDW', label: 'ADAS_LDW（车道偏离）' }
]

const DEFAULT_PARAMS: Record<string, Record<string, number>> = {
  SPEED: { speedKmh: 100 },
  FATIGUE: { continuousMin: 240, gapMin: 10 },
  COMBO: { windowMin: 30, speedKmh: 100 },
  SIGNAL: {}
}

const loading = ref(false)
const records = ref<RiskRule[]>([])
const total = ref(0)
const summary = ref<Partial<EngineSummary>>({})
const filters = reactive({
  page: 1,
  size: 10,
  ruleCode: '',
  ruleName: '',
  ruleType: '',
  status: undefined as number | undefined
})

const todayLevelText = computed(() => {
  const rows = summary.value.todayByLevel
  if (!rows || !rows.length) return ''
  return rows
    .map((r) => `${levelText(Number(r.level))}:${r.cnt}`)
    .join(' / ')
})

async function load() {
  loading.value = true
  try {
    const result = await pageRules({
      page: filters.page,
      size: filters.size,
      ruleCode: filters.ruleCode || undefined,
      ruleName: filters.ruleName || undefined,
      ruleType: filters.ruleType || undefined,
      status: filters.status
    })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  summary.value = await getEngineSummary()
}

function search() {
  filters.page = 1
  load()
}

function resetFilters() {
  filters.ruleCode = ''
  filters.ruleName = ''
  filters.ruleType = ''
  filters.status = undefined
  filters.page = 1
  load()
}

// ---- 新增/编辑 ----
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: null as number | null,
  ruleCode: '',
  ruleName: '',
  ruleType: 'SPEED',
  riskLevel: 2,
  params: { ...DEFAULT_PARAMS.SPEED } as Record<string, number>,
  cooldownSec: 300,
  status: 1,
  builtIn: 0,
  remark: ''
})

const rules: FormRules = {
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  ruleCode: [
    { required: true, message: '请填写规则编码', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]{2,39}$/,
      message: '大写字母开头，3~40 位大写字母/数字/下划线',
      trigger: 'blur'
    }
  ],
  ruleName: [{ required: true, message: '请填写规则名称', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
  cooldownSec: [{ required: true, message: '请填写冷却时间', trigger: 'blur' }]
}

function resetFormForType(type: string) {
  form.params = { ...DEFAULT_PARAMS[type] }
}

function onTypeChange(type: string) {
  resetFormForType(type)
  if (type !== 'SIGNAL') {
    form.ruleCode = ''
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    ruleCode: '',
    ruleName: '',
    ruleType: 'SPEED',
    riskLevel: 2,
    params: { ...DEFAULT_PARAMS.SPEED },
    cooldownSec: 300,
    status: 1,
    builtIn: 0,
    remark: ''
  })
  drawerVisible.value = true
}

async function openEdit(row: RiskRule) {
  const detail = await getRule(row.id)
  Object.assign(form, {
    id: detail.id,
    ruleCode: detail.ruleCode,
    ruleName: detail.ruleName,
    ruleType: detail.ruleType,
    riskLevel: detail.riskLevel,
    params: { ...(detail.params || DEFAULT_PARAMS[detail.ruleType] || {}) },
    cooldownSec: detail.cooldownSec,
    status: detail.status,
    builtIn: detail.builtIn,
    remark: detail.remark || ''
  })
  drawerVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = {
      ruleCode: form.ruleCode,
      ruleName: form.ruleName.trim(),
      ruleType: form.ruleType,
      riskLevel: form.riskLevel,
      params: form.params,
      cooldownSec: form.cooldownSec,
      status: form.status,
      remark: form.remark || null
    }
    if (form.id) {
      await updateRule(form.id, body)
      ElMessage.success('规则已更新')
    } else {
      await createRule(body)
      ElMessage.success('规则创建成功（约 30 秒内热加载生效）')
    }
    drawerVisible.value = false
    await Promise.all([load(), loadSummary()])
  } finally {
    saving.value = false
  }
}

async function onToggleStatus(row: RiskRule, enabled: boolean) {
  const status = enabled ? 1 : 0
  try {
    await changeRuleStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '规则已启用' : '规则已停用')
    loadSummary()
  } catch {
    /* http 拦截器已提示 */
  }
}

async function onDelete(row: RiskRule) {
  await ElMessageBox.confirm(
    `确认删除规则「${row.ruleName}」？历史风险事件保留，删除后引擎不再产生该规则事件。`,
    '危险操作',
    { type: 'warning', confirmButtonText: '删除' }
  )
  await deleteRule(row.id)
  ElMessage.success('规则已删除')
  await Promise.all([load(), loadSummary()])
}

// ---- 展示辅助 ----
function typeLabel(t: string): string {
  return RULE_TYPES.find((x) => x.value === t)?.label || t
}

function typeTag(t: string): 'primary' | 'warning' | 'danger' | 'success' | 'info' {
  return t === 'SPEED' ? 'danger' : t === 'FATIGUE' ? 'warning'
    : t === 'SIGNAL' ? 'primary' : t === 'COMBO' ? 'success' : 'info'
}

function paramsText(row: RiskRule): string {
  const p = row.params || {}
  switch (row.ruleType) {
    case 'SPEED':
      return `点位速度 ≥ ${p.speedKmh ?? '-'} km/h`
    case 'FATIGUE':
      return `连续驾驶 ≥ ${p.continuousMin ?? '-'} 分钟（${p.gapMin ?? '-'} 分钟内间隔视为连续）`
    case 'SIGNAL':
      return `终端上报 ${row.ruleCode} 信号即触发`
    case 'COMBO':
      return `${p.windowMin ?? '-'} 分钟窗口内疲劳驾驶且速度 ≥ ${p.speedKmh ?? '-'} km/h`
    default:
      return ''
  }
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

onMounted(() => {
  load()
  loadSummary()
})
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

.stat-card :deep(.el-card__body) {
  padding: 14px 18px;
}

.value {
  font-size: 26px;
  font-weight: 600;
  color: #111827;
  line-height: 1.2;
}

.unit {
  font-size: 14px;
  color: #9ca3af;
  font-weight: 400;
}

.label {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

.top-card {
  min-height: 74px;
}

.top-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  padding: 2px 0;
}

.top-name {
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}
</style>
