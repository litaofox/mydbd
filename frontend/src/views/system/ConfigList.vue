<template>
  <div class="mdm-page">
    <!-- 测试数据与模拟器（由实时监控模块迁入） -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div class="card-header">
          <span>测试数据与模拟器</span>
          <el-tag size="small" type="warning">仅用于演示 / 测试环境</el-tag>
        </div>
      </template>
      <el-row :gutter="24">
        <!-- 标准测试数据集 -->
        <el-col :xs="24" :sm="24" :md="14" class="ds-col">
          <div class="section-title">标准测试数据集（4 公司 / 10 车队 / 500 车，鲁苏沪皖 9 月全月）</div>
          <div class="action-row">
            <el-button type="primary" :loading="dsLoading" @click="onDatasetLoad('standard')">
              加载标准测试集
            </el-button>
            <el-button type="warning" plain :loading="dsLoading" @click="onDatasetLoad('dense')">
              加载稠密测试集
            </el-button>
            <el-button type="danger" plain :disabled="dsLoading" @click="onDatasetClear">
              清空业务数据
            </el-button>
          </div>
          <div v-if="dsLoading || dsStage" class="ds-progress">
            <el-progress
              :percentage="dsPercent"
              :stroke-width="16"
              text-inside
              :status="dsError ? 'exception' : !dsLoading && dsPercent >= 100 ? 'success' : undefined"
            />
            <div class="ds-stage-line">
              <span>{{ dsStage }}</span>
              <span v-if="dsLoading" class="ds-hint">加载期间请勿关闭页面，可切换至其他菜单</span>
            </div>
            <div v-if="dsError" class="ds-error">加载失败：{{ dsError }}</div>
            <div v-else-if="dsSummary" class="ds-summary">{{ dsSummary }}</div>
          </div>
          <div v-else-if="dsSummary" class="ds-summary">{{ dsSummary }}</div>
        </el-col>

        <el-col :xs="24" :sm="24" :md="10" class="sim-col">
          <div class="section-title">实时轨迹模拟器</div>
          <div class="sim-row">
            <el-switch
              :model-value="simRunning"
              :loading="simSwitching"
              inline-prompt
              active-text="运行"
              inactive-text="停止"
              style="--el-switch-on-width: 44px"
              @change="onSimToggle"
            />
            <div class="sim-meta">
              <template v-if="sim">
                <div>
                  <el-tag :type="sim.running ? 'success' : 'info'" size="small">
                    {{ sim.running ? '运行中' : '已停止' }}
                  </el-tag>
                  <span class="sim-text">{{ sim.vehicleCount }} 辆车 · {{ sim.ticks }} 个心跳 · 演示事件 {{ sim.demoEvents ?? 0 }} 起</span>
                </div>
                <div class="sim-sub">启动时间：{{ sim.running ? sim.startedAt ?? '-' : '—' }}</div>
              </template>
              <span v-else class="sim-text">状态获取中…</span>
            </div>
          </div>
          <el-alert
            v-if="simFallback"
            type="info"
            :closable="false"
            show-icon
            title="未检测到标准测试数据集，模拟器使用内置 5 辆演示车，建议先在左侧加载测试集"
            style="margin-top: 10px"
          />
          <div class="sim-tip">开启后每 2 秒推送一批实时轨迹，约每 15 秒均匀产生 1 起终端报警与风险事件（5 分钟约 20 起）。</div>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="参数名称 / 键" clearable style="width: 220px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button v-perm="'system:config:edit'" type="success" @click="openCreate">新增参数</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="configKey" label="参数键" width="220" />
        <el-table-column prop="configName" label="名称" width="180" />
        <el-table-column prop="configValue" label="参数值" show-overflow-tooltip />
        <el-table-column prop="valueType" label="类型" width="90" />
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isSystem === 1" type="warning" size="small">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.isSystem !== 1" link type="danger" size="small" v-perm="'system:config:edit'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        v-model:current-page="page" v-model:page-size="size"
        :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        @current-change="load" @size-change="load"
      />
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑参数' : '新增参数'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="参数键"><el-input v-model="form.configKey" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.configName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.valueType" :disabled="!!form.id" style="width: 100%">
            <el-option label="字符串" value="STRING" />
            <el-option label="整数" value="INT" />
            <el-option label="布尔" value="BOOL" />
            <el-option label="JSON" value="JSON" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数值">
          <el-switch v-if="form.valueType === 'BOOL'" v-model="form.configValue" active-value="true" inactive-value="false" />
          <el-input v-else-if="form.valueType === 'JSON'" v-model="form.configValue" type="textarea" :rows="4" placeholder='{"key":"value"}' />
          <el-input v-else v-model="form.configValue" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConfigs, addConfig, updateConfig, deleteConfig, type SysConfig } from '@/api/system'
import {
  datasetClear,
  datasetLoad,
  datasetStatus,
  simulatorStart,
  simulatorStatus,
  simulatorStop,
  type DatasetStatus,
  type SimulatorStatus
} from '@/api/traj'

// ===== 测试数据集（由实时监控页迁入） =====
const dsLoading = ref(false)
const dsStage = ref('')
const dsPercent = ref(0)
const dsError = ref('')
const dsCounts = ref<DatasetStatus['counts'] | null>(null)
let dsTimer: ReturnType<typeof setInterval> | null = null

const dsSummary = computed(() => {
  const c = dsCounts.value
  if (!c) return ''
  return `上次加载完成：${c.vehicles ?? 0} 辆车 / ${c.points ?? 0} 个轨迹点 / ` +
         `${c.events ?? 0} 起风险事件 / ${c.warns ?? 0} 条终端报警 / ${c.scores ?? 0} 条日评分`
})

async function pollDataset() {
  try {
    const st = await datasetStatus()
    dsStage.value = st.running ? st.stage : (st.error ? st.stage : '加载完成')
    dsPercent.value = st.percent
    dsError.value = st.error ?? ''
    if (!st.running) {
      stopDsPolling()
      dsLoading.value = false
      if (!st.error && st.percent >= 100) {
        dsCounts.value = st.counts ?? null
        ElMessage.success('测试集加载完成')
        refreshSimStatus()
      } else if (st.error) {
        ElMessage.error(`测试集加载失败：${st.error}`)
      }
    }
  } catch { /* 轮询失败下一轮重试 */ }
}

function startDsPolling() {
  dsLoading.value = true
  dsError.value = ''
  pollDataset()
  dsTimer = setInterval(pollDataset, 2000)
}

function stopDsPolling() {
  if (dsTimer) {
    clearInterval(dsTimer)
    dsTimer = null
  }
}

async function onDatasetLoad(mode: 'standard' | 'dense') {
  try {
    await ElMessageBox.confirm(
      mode === 'dense'
        ? '稠密版约 330 万轨迹点（15 秒一点，192 起风险事件），预计加载 12~15 分钟，确认开始？'
        : '标准版约 115 万轨迹点（30 秒一点，96 起风险事件），预计 4~5 分钟，且会先清空现有业务数据，确认开始？',
      '加载标准测试数据集',
      { confirmButtonText: '开始加载', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await datasetLoad(mode)
  ElMessage.info('数据集开始后台生成，可在此查看进度，也可切换至其他菜单')
  startDsPolling()
}

async function onDatasetClear() {
  try {
    await ElMessageBox.confirm(
      '将清空全部公司/车队/车辆/终端/司机/轨迹/报警/风险事件/工单/干预/评分数据（保留账号、角色、菜单与系统参数），确认清空？',
      '清空业务数据',
      { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning' }
    )
    await ElMessageBox.confirm(
      '此操作不可恢复，确定继续吗？',
      '二次确认',
      { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'error' }
    )
  } catch {
    return
  }
  await datasetClear()
  dsStage.value = ''
  dsPercent.value = 0
  dsError.value = ''
  dsCounts.value = null
  ElMessage.success('业务数据已清空，可重新加载测试集')
  refreshSimStatus()
}

// ===== 实时轨迹模拟器（拨动开关） =====
const sim = ref<SimulatorStatus | null>(null)
const simRunning = ref(false)
const simSwitching = ref(false)
let simTimer: ReturnType<typeof setInterval> | null = null

const simFallback = computed(() =>
  !!sim.value?.vehicles?.some((v) => v.identityCode.startsWith('SIM_')))

async function refreshSimStatus() {
  try {
    const s = await simulatorStatus()
    sim.value = s
    simRunning.value = s.running
  } catch {
    /* 状态接口异常时保持上次展示，下轮重试 */
  }
}

async function onSimToggle(val: boolean | string | number) {
  const turnOn = Boolean(val)
  if (!turnOn) {
    // 安全停止：二次确认，取消时开关因绑定 model-value 自动回弹
    try {
      await ElMessageBox.confirm(
        '停止后将不再产生实时轨迹、终端报警与风险事件（已产生的数据保留），确认停止模拟器？',
        '停止模拟器',
        { confirmButtonText: '停止', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  simSwitching.value = true
  try {
    if (turnOn) {
      await simulatorStart()
      ElMessage.success('模拟器已启动，开始推送实时轨迹')
    } else {
      await simulatorStop()
      ElMessage.success('模拟器已安全停止，资源已释放')
    }
    await refreshSimStatus()
  } finally {
    simSwitching.value = false
  }
}

// ===== 系统参数 CRUD =====
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const records = ref<SysConfig[]>([])
const loading = ref(false)

const dialog = ref(false)
const form = ref<SysConfig>({ configKey: '', configName: '', configValue: '', valueType: 'STRING', isSystem: 0 })

async function load() {
  loading.value = true
  try {
    const data = await getConfigs({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function reset() { keyword.value = ''; search() }

function openCreate() {
  form.value = { configKey: '', configName: '', configValue: '', valueType: 'STRING', isSystem: 0 }
  dialog.value = true
}

function openEdit(row: SysConfig) {
  form.value = { ...row }
  dialog.value = true
}

async function save() {
  if (!form.value.configKey || !form.value.configName) {
    ElMessage.warning('请填写参数键和名称')
    return
  }
  if (form.value.valueType === 'INT' && form.value.configValue && !/^-?\d+$/.test(form.value.configValue.trim())) {
    ElMessage.warning('整数类型参数值必须为数字')
    return
  }
  if (form.value.id) {
    await updateConfig(form.value.id, form.value)
  } else {
    await addConfig(form.value)
  }
  dialog.value = false
  ElMessage.success('保存成功')
  load()
}

async function remove(row: SysConfig) {
  await ElMessageBox.confirm(`确认删除参数「${row.configKey}」？`, '提示', { type: 'warning' })
  await deleteConfig(row.id!)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  await refreshSimStatus()
  // 进入页面时若已有数据集任务在跑，自动接续进度展示
  try {
    const st = await datasetStatus()
    if (st.running) {
      startDsPolling()
    } else if (st.percent >= 100 && !st.error) {
      dsStage.value = '加载完成'
      dsPercent.value = 100
      dsCounts.value = st.counts ?? null
    }
  } catch { /* ignore */ }
  // 模拟器状态周期同步（反映后端真实状态，支持多标签页一致）
  simTimer = setInterval(refreshSimStatus, 5000)
})

onBeforeUnmount(() => {
  stopDsPolling()
  if (simTimer) {
    clearInterval(simTimer)
    simTimer = null
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  white-space: nowrap;
}
.sim-col {
  margin-top: 16px;
}
@media (min-width: 992px) {
  .sim-col {
    margin-top: 0;
    padding-left: 24px;
    border-left: 1px solid var(--el-border-color-lighter);
  }
}
.section-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}
.action-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.ds-progress {
  margin-top: 12px;
}
.ds-stage-line {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ds-hint {
  color: var(--el-color-warning);
}
.ds-error {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-danger);
}
.ds-summary {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-success);
}
.sim-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.sim-meta {
  flex: 1;
}
.sim-text {
  margin-left: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.sim-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sim-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
