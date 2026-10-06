<template>
  <div class="mdm-page">
    <!-- 运行模式与接入配置 -->
    <el-card style="margin-bottom: 12px" v-loading="cfgLoading">
      <template #header>
        <div class="card-header">
          <span>运行模式与接入配置</span>
          <span class="hint">配置保存后需重启 processing 服务生效</span>
        </div>
      </template>

      <div class="mode-cards">
        <div
          v-for="m in MODES"
          :key="m.value"
          class="mode-card"
          :class="{ active: form.mode === m.value, disabled: !canEdit }"
          @click="canEdit && (form.mode = m.value)"
        >
          <div class="mode-card-title">
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.label }}</span>
            <el-tag v-if="status?.mode === m.value" size="small" type="success" effect="dark" style="margin-left: auto">运行中</el-tag>
            <el-tag v-else-if="savedMode === m.value" size="small" type="warning" effect="plain" style="margin-left: auto">待重启</el-tag>
          </div>
          <div class="mode-card-desc">{{ m.desc }}</div>
        </div>
      </div>

      <el-form label-width="150px" size="small" style="margin-top: 14px; max-width: 720px">
        <el-form-item v-if="form.mode !== 'simulator'" label="Kafka 地址" required>
          <el-input
            v-model="form.bootstrapServers"
            :disabled="!canEdit"
            placeholder="mock：kafka:9092；正式：broker1:9092,broker2:9092"
          />
          <span class="field-hint">多个 broker 用英文逗号分隔；留空则回退环境变量</span>
        </el-form-item>
        <el-form-item v-if="form.mode !== 'simulator'" label="消费组">
          <el-input v-model="form.groupId" :disabled="!canEdit" placeholder="mydbd-ingest" />
        </el-form-item>
        <el-form-item v-if="form.mode !== 'simulator'" label="附件服务地址">
          <el-input v-model="form.fileBaseUrl" :disabled="!canEdit" placeholder="http://网关IP:18009（留空回退环境变量）" />
        </el-form-item>
        <el-form-item v-if="form.mode !== 'simulator'" label="附件策略">
          <el-select v-model="form.mediaStrategy" :disabled="!canEdit" style="width: 240px">
            <el-option label="转存平台卷（local，鉴权后查看）" value="local" />
            <el-option label="仅记录原始地址（proxy）" value="proxy" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!canEdit">
          <span class="field-hint">当前账号无系统参数编辑权限（system:config:edit），仅可查看</span>
        </el-form-item>
        <el-form-item v-if="canEdit">
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          <el-button :disabled="saving" @click="loadConfig">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon style="max-width: 720px">
        <template #title>
          演示模式无需外部依赖；测试模式需先启动 dev-infra Kafka；正式模式切换前请确认 broker 连通、只读 ACL 与手机号档案对照。
          <template v-if="sourceHint"> 当前启动配置来源：{{ sourceHint }}。</template>
        </template>
      </el-alert>
    </el-card>

    <!-- 测试数据与模拟器（自系统参数页迁入；按运行模式与权限管控） -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div class="card-header">
          <span>测试数据与模拟器</span>
          <el-tag size="small" type="warning">仅用于演示 / 测试环境</el-tag>
        </div>
      </template>
      <el-alert
        v-if="runningMode === 'gateway'"
        type="error"
        :closable="false"
        show-icon
        title="正式模式已禁用全部测试数据/模拟器操作；如需使用请切回演示/测试模式并重启 processing 服务"
        style="margin-bottom: 12px"
      />
      <el-row :gutter="24">
        <!-- 标准测试数据集 -->
        <el-col :xs="24" :sm="24" :md="14" class="ds-col">
          <div class="section-title">标准测试数据集（4 公司 / 10 车队 / 500 车，鲁苏沪皖 9 月全月）</div>
          <div class="action-row">
            <el-button type="primary" :disabled="dsDisabled" :loading="dsLoading" @click="onDatasetLoad('standard')">
              加载标准测试集
            </el-button>
            <el-button type="warning" plain :disabled="dsDisabled" :loading="dsLoading" @click="onDatasetLoad('dense')">
              加载稠密测试集
            </el-button>
            <el-button type="danger" plain :disabled="dsDisabled" @click="onClearRuntime">
              清除模拟数据
            </el-button>
            <el-button type="danger" plain :disabled="dsDisabled" @click="onDatasetClear">
              清空业务数据
            </el-button>
          </div>
          <div v-if="runningMode === 'mock'" class="mode-hint">
            「清除模拟数据」仅清空轨迹/报警/事件/工单等运行数据，保留公司/车队/车辆/终端/司机等基础数据，用于重新开始；
            「清空业务数据」会连基础数据一并清空（需重新加载数据集）。
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
          <!-- 测试模式：mock 仿真投递开关（运行时启停，重启后保持） -->
          <template v-if="runningMode === 'mock'">
            <div class="section-title">仿真投递（500 车测试数据）</div>
            <div class="sim-row">
              <el-switch
                :model-value="mockRunning"
                :loading="mockSwitching"
                :disabled="mockDisabled"
                inline-prompt
                active-text="投递"
                inactive-text="停止"
                style="--el-switch-on-width: 44px"
                @change="onMockToggle"
              />
              <div class="sim-meta">
                <div>
                  <el-tag :type="mockRunning ? 'success' : 'info'" size="small">
                    {{ mockRunning ? '投递中' : '已停止' }}
                  </el-tag>
                  <span class="sim-text">
                    已投递 {{ status?.mockStats?.ticks ?? 0 }} 轮 · 报警 {{ status?.mockStats?.warns ?? 0 }} 起
                  </span>
                </div>
                <div class="sim-sub">
                  启动时间：{{ mockRunning ? status?.mockStartedAt ?? '-' : '—' }}
                  <template v-if="!mockRunning && status?.mockStoppedAt"> · 停止时间：{{ status.mockStoppedAt }}</template>
                </div>
              </div>
            </div>
            <div class="sim-tip">
              开启时每 5 秒/车投递定位并按网关契约走真实消费链路，报警全局限频 ≤2 起/分钟（风险事件/工单同步受限）；
              停止后不再产生任何模拟数据，重启 processing 后保持当前开关状态。
              <template v-if="!canEdit"> 需系统参数编辑权限。</template>
            </div>
          </template>

          <!-- 演示模式：实时轨迹模拟器 -->
          <template v-else>
            <div class="section-title">实时轨迹模拟器</div>
            <div class="sim-row">
              <el-switch
                :model-value="simRunning"
                :loading="simSwitching"
                :disabled="simDisabled"
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
            <div class="sim-tip">
              开启后每 2 秒推送一批实时轨迹，约每 15 秒均匀产生 1 起终端报警与风险事件（5 分钟约 20 起）。
              <template v-if="runningMode && runningMode !== 'simulator'"> 仅演示模式可用，当前模式已停用。</template>
            </div>
          </template>
        </el-col>
      </el-row>
    </el-card>

    <!-- 状态卡片区 -->
    <el-card style="margin-bottom: 12px" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>运行状态</span>
          <div class="header-actions">
            <el-button size="small" :loading="loading" @click="refresh">刷新</el-button>
            <span class="hint">每 10 秒自动刷新</span>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="运行模式">
          <el-tag size="small" :type="modeTag(status?.mode)">{{ modeLabel(status?.mode) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="消费状态">
          <el-tag size="small" :type="status?.running ? 'success' : 'info'">
            {{ status?.running ? '运行中' : '已停止' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="消费组">{{ status?.groupId || '--' }}</el-descriptions-item>
        <el-descriptions-item label="启动时间">{{ fmt(status?.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="Kafka 地址" :span="2">{{ status?.bootstrapServers || '--' }}</el-descriptions-item>
        <el-descriptions-item label="订阅 Topic" :span="3">
          <template v-if="status?.topics?.length">
            <el-tag v-for="t in status.topics" :key="t" size="small" effect="plain" style="margin-right: 6px">{{ t }}</el-tag>
          </template>
          <span v-else>--</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="status?.lastError"
        type="error"
        :closable="false"
        show-icon
        :title="`最近错误：${status.lastError}`"
        style="margin-top: 10px"
      />
    </el-card>

    <!-- 汇总数字 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="12">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">死信消息（DLQ）</div>
            <div class="kpi-value" :style="{ color: status?.dlqCount ? '#dc2626' : undefined }">
              {{ status?.dlqCount ?? 0 }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div class="kpi">
            <div class="kpi-label">未登记终端</div>
            <div class="kpi-value" :style="{ color: status?.unknownCount ? '#d97706' : undefined }">
              {{ status?.unknownCount ?? 0 }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Topic 统计 -->
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div class="card-header"><span>Topic 消费统计</span></div>
      </template>
      <el-table :data="status?.stats ?? []" size="small">
        <el-table-column prop="topic" label="Topic" min-width="200" show-overflow-tooltip />
        <el-table-column prop="msgCount" label="消息数" width="120" />
        <el-table-column label="失败数" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.errCount ? '#dc2626' : undefined }">{{ row.errCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近处理时间" width="180">
          <template #default="{ row }">{{ fmt(row.lastTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 未登记终端 -->
    <el-card>
      <template #header>
        <div class="card-header"><span>未登记终端（收到消息但未建档）</span></div>
      </template>
      <el-table :data="unknowns" size="small">
        <el-table-column prop="phoneNumber" label="手机号" width="140">
          <template #default="{ row }">{{ row.phoneNumber || '--' }}</template>
        </el-table-column>
        <el-table-column prop="truckId" label="TruckId" width="140">
          <template #default="{ row }">{{ row.truckId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="plateNo" label="车牌" width="110">
          <template #default="{ row }">{{ row.plateNo || '--' }}</template>
        </el-table-column>
        <el-table-column prop="msgCount" label="消息数" width="100" />
        <el-table-column label="首次出现" width="170">
          <template #default="{ row }">{{ fmt(row.firstSeen) }}</template>
        </el-table-column>
        <el-table-column label="最近出现" width="170">
          <template #default="{ row }">{{ fmt(row.lastSeen) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay, Connection, Monitor } from '@element-plus/icons-vue'
import { getGatewayStatus, getUnknownTerminals, type GatewayStatus, type UnknownTerminal } from '@/api/gateway'
import { getConfigs, updateConfig, type SysConfig } from '@/api/system'
import {
  clearRuntimeData,
  datasetClear,
  datasetLoad,
  datasetStatus,
  mockDeliveryStart,
  mockDeliveryStop,
  simulatorStart,
  simulatorStatus,
  simulatorStop,
  type DatasetStatus,
  type SimulatorStatus
} from '@/api/traj'
import { useAuthStore } from '@/store/auth'

const CFG_KEYS = {
  mode: 'gateway.mode',
  bootstrapServers: 'gateway.kafka.bootstrap-servers',
  groupId: 'gateway.kafka.group-id',
  fileBaseUrl: 'gateway.file-base-url',
  mediaStrategy: 'gateway.media-strategy',
} as const

const MODES = [
  { value: 'simulator', label: '演示模式', icon: Monitor, desc: '内置模拟器产生演示数据，无需 Kafka 等外部依赖，用于产品演示与出厂默认' },
  { value: 'mock', label: '测试模式', icon: VideoPlay, desc: '仿真数据按 vps 网关契约投递本地 Kafka，完整验证接入、报警、工单、附件链路' },
  { value: 'gateway', label: '正式模式', icon: Connection, desc: '连接正式 vps 车载网关集群，只读消费真实在网车辆数据' },
] as const

const auth = useAuthStore()
const canEdit = computed(() => auth.has('system:config:edit'))

const loading = ref(false)
const cfgLoading = ref(false)
const saving = ref(false)
const status = ref<GatewayStatus | null>(null)
const unknowns = ref<UnknownTerminal[]>([])
let timer: ReturnType<typeof setInterval> | null = null

const cfgRows = reactive<Record<string, SysConfig>>({})
const form = reactive({
  mode: 'simulator',
  bootstrapServers: '',
  groupId: 'mydbd-ingest',
  fileBaseUrl: '',
  mediaStrategy: 'local',
})
const savedMode = ref('')

const sourceHint = computed(() => {
  const src = status.value?.configSource
  if (!src) return ''
  const label: Record<string, string> = { db: '系统参数', env: '环境变量', default: '默认值' }
  return Object.entries(src).map(([k, v]) => `${k.replace('gateway.', '')}=${label[v] || v}`).join('，')
})

function modeLabel(mode?: string) {
  const hit = MODES.find((m) => m.value === mode)
  return hit ? hit.label : mode || '--'
}

function modeTag(mode?: string) {
  if (mode === 'gateway') return 'success'
  if (mode === 'simulator') return 'warning'
  return 'info'
}

function fmt(v?: string | null) {
  return v ? v.replace('T', ' ').slice(0, 19) : '--'
}

async function refresh() {
  loading.value = true
  try {
    const [s, u] = await Promise.all([getGatewayStatus(), getUnknownTerminals()])
    status.value = s
    unknowns.value = u
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
  // 测试数据与模拟器状态随同周期同步（失败静默，下一轮重试）
  try {
    const st = await simulatorStatus()
    sim.value = st
    simRunning.value = st.running
  } catch { /* 保持上次展示 */ }
  try {
    const st = await datasetStatus()
    if (!dsLoading.value) syncDsStatus(st)
  } catch { /* 保持上次展示 */ }
}

function syncDsStatus(st: DatasetStatus) {
  dsStage.value = st.running ? st.stage : (st.error ? st.stage : '')
  dsPercent.value = st.percent
  if (!st.running && !st.error && st.percent >= 100) {
    dsCounts.value = st.counts ?? null
  }
}

// ===== 测试数据与模拟器（自系统参数页迁入，按运行模式硬管控） =====
// 依据 processing 运行中模式（status.mode，启动期固化值）而非页面待保存的选择值：
// 数据集加载/清空仅演示与测试模式可用；模拟器仅演示模式可用；正式模式全部禁用。
const runningMode = computed(() => status.value?.mode ?? '')
const dsDisabled = computed(() =>
  !canEdit.value || (runningMode.value !== 'simulator' && runningMode.value !== 'mock'))
const simDisabled = computed(() => !canEdit.value || runningMode.value !== 'simulator')

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
}

// ===== 实时轨迹模拟器（拨动开关，仅演示模式） =====
const sim = ref<SimulatorStatus | null>(null)
const simRunning = ref(false)
const simSwitching = ref(false)

const simFallback = computed(() =>
  !!sim.value?.vehicles?.some((v) => v.identityCode.startsWith('SIM_')))

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
    const st = await simulatorStatus()
    sim.value = st
    simRunning.value = st.running
  } finally {
    simSwitching.value = false
  }
}

// ===== mock 仿真投递开关（拨动开关，仅测试模式；启停即时生效并持久化） =====
const mockSwitching = ref(false)
const mockRunning = computed(() => !!status.value?.mockRunning)
const mockDisabled = computed(() => !canEdit.value || runningMode.value !== 'mock')

async function onMockToggle(val: boolean | string | number) {
  const turnOn = Boolean(val)
  if (!turnOn) {
    // 安全停止：二次确认，取消时开关因绑定 model-value 自动回弹
    try {
      await ElMessageBox.confirm(
        '停止后将不再产生模拟定位、终端报警、风险事件与工单（已产生的数据保留），确认停止仿真投递？',
        '停止仿真投递',
        { confirmButtonText: '停止', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  mockSwitching.value = true
  try {
    const st = turnOn ? await mockDeliveryStart() : await mockDeliveryStop()
    // 用接口返回的最新状态同步本地展示（10s 周期刷新亦会覆盖）
    if (status.value) {
      status.value = { ...status.value, ...st }
    }
    if (turnOn) {
      ElMessage.success('仿真投递已开启，测试数据恢复产生（重启后保持开启）')
    } else {
      ElMessage.success('仿真投递已安全停止，不再产生模拟数据（重启后保持停止）')
    }
  } finally {
    mockSwitching.value = false
  }
}

async function onClearRuntime() {
  try {
    await ElMessageBox.confirm(
      '将清除模拟运行数据：GPS 轨迹/照片、终端报警及附件、风险事件、工单及处理记录、干预、日评分、' +
      '视频分析、围栏穿越状态、通知消息等；基础数据（公司/车队/车辆/终端/司机/规则/围栏/账号配置）全部保留。' +
      '清除后若仿真投递开启，新数据即刻按限频频次重新产生。确认清除？',
      '清除模拟数据',
      { confirmButtonText: '确认清除', cancelButtonText: '取消', type: 'warning' }
    )
    await ElMessageBox.confirm(
      '此操作不可恢复，确定继续吗？',
      '二次确认',
      { confirmButtonText: '确定清除', cancelButtonText: '取消', type: 'error' }
    )
  } catch {
    return
  }
  await clearRuntimeData()
  ElMessage.success('模拟运行数据已清除，基础数据保留')
  refresh()
}

async function loadConfig() {
  cfgLoading.value = true
  try {
    const page = await getConfigs({ page: 1, size: 100, keyword: 'gateway' })
    for (const row of page.records) cfgRows[row.configKey] = row
    form.mode = cfgRows[CFG_KEYS.mode]?.configValue || 'simulator'
    form.bootstrapServers = cfgRows[CFG_KEYS.bootstrapServers]?.configValue || ''
    form.groupId = cfgRows[CFG_KEYS.groupId]?.configValue || 'mydbd-ingest'
    form.fileBaseUrl = cfgRows[CFG_KEYS.fileBaseUrl]?.configValue || ''
    form.mediaStrategy = cfgRows[CFG_KEYS.mediaStrategy]?.configValue || 'local'
    savedMode.value = form.mode
  } catch {
    /* 拦截器已提示 */
  } finally {
    cfgLoading.value = false
  }
}

async function save() {
  if (!['simulator', 'mock', 'gateway'].includes(form.mode)) {
    ElMessage.warning('请选择运行模式')
    return
  }
  if (form.mode !== 'simulator' && !form.bootstrapServers.trim()) {
    ElMessage.warning('测试/正式模式必须填写 Kafka 地址')
    return
  }
  if (!['local', 'proxy'].includes(form.mediaStrategy)) {
    ElMessage.warning('附件策略不合法')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认切换为「${MODES.find((m) => m.value === form.mode)?.label}」？配置保存后需重启 processing 服务方可生效。`,
      '模式切换确认',
      { confirmButtonText: '保存并稍后重启', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    const values: Record<string, string> = {
      [CFG_KEYS.mode]: form.mode,
      [CFG_KEYS.bootstrapServers]: form.bootstrapServers.trim(),
      [CFG_KEYS.groupId]: form.groupId.trim() || 'mydbd-ingest',
      [CFG_KEYS.fileBaseUrl]: form.fileBaseUrl.trim(),
      [CFG_KEYS.mediaStrategy]: form.mediaStrategy,
    }
    for (const [key, value] of Object.entries(values)) {
      const row = cfgRows[key]
      if (!row?.id) throw new Error(`系统参数缺失：${key}`)
      await updateConfig(row.id, { ...row, configValue: value })
    }
    savedMode.value = form.mode
    ElMessage.warning('配置已保存，网关相关设置需重启 processing 服务后生效')
    await loadConfig()
    refresh()
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadConfig()
  refresh()
  timer = setInterval(refresh, 10000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  stopDsPolling()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hint {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
}
.field-hint {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
}
.mode-cards {
  display: flex;
  gap: 12px;
}
.mode-card {
  flex: 1;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.mode-card:hover {
  border-color: #409eff;
}
.mode-card.active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
  background: #f5f9ff;
}
.mode-card.disabled {
  cursor: not-allowed;
  opacity: 0.85;
}
.mode-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  margin-bottom: 6px;
}
.mode-card-desc {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
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
/* ===== 测试数据与模拟器卡片 ===== */
.ds-col {
  margin-bottom: 8px;
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
.mode-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
  line-height: 1.6;
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
