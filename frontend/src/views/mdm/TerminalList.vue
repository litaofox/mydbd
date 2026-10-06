<template>
  <div class="mdm-page">
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="终端编号 / SIM 卡号" clearable style="width: 190px" @keyup.enter="search" />
        <el-input v-model="filters.simAccount" placeholder="手机号" clearable style="width: 140px" @keyup.enter="search" />
        <el-select v-model="filters.protocolType" placeholder="通信协议" clearable style="width: 120px">
          <el-option v-for="p in PROTOCOL_TYPES" :key="p" :value="p" :label="p" />
        </el-select>
        <el-select v-model="filters.equipmentType" placeholder="设备类型" clearable style="width: 140px">
          <el-option v-for="t in EQUIPMENT_TYPES" :key="t.value" :value="t.value" :label="t.label" />
        </el-select>
        <el-select v-model="filters.status" placeholder="设备状态" clearable style="width: 120px">
          <el-option v-for="s in TERMINAL_STATUS" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'mdm:terminal:edit'" type="success" @click="openCreate">登记终端</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="identityCode" label="终端编号" width="130" fixed="left" />
        <el-table-column label="厂商/型号" width="160">
          <template #default="{ row }">{{ [row.oemCode, row.tlModel].filter(Boolean).join(' / ') || '--' }}</template>
        </el-table-column>
        <el-table-column prop="simAccount" label="手机号(SIM)" width="140">
          <template #default="{ row }">{{ row.simAccount || '--' }}</template>
        </el-table-column>
        <el-table-column prop="gatewayTruckId" label="网关TruckId" width="130">
          <template #default="{ row }">{{ row.gatewayTruckId || '--' }}</template>
        </el-table-column>
        <el-table-column label="在线状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.onlineStatus === 1" size="small" type="success">在线</el-tag>
            <el-tag v-else-if="row.onlineStatus === 0" size="small" type="info">离线</el-tag>
            <span v-else style="color: #9ca3af">--</span>
          </template>
        </el-table-column>
        <el-table-column label="最近心跳" width="160">
          <template #default="{ row }">{{ fmt(row.lastHeartbeatTime) }}</template>
        </el-table-column>
        <el-table-column prop="protocolType" label="协议" width="90" />
        <el-table-column label="设备类型" width="110">
          <template #default="{ row }">{{ labelOf(EQUIPMENT_TYPES, row.equipmentType) }}</template>
        </el-table-column>
        <el-table-column prop="videoChannel" label="视频通道" width="80">
          <template #default="{ row }">{{ row.videoChannel ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagOf(TERMINAL_STATUS, row.status)">
              {{ labelOf(TERMINAL_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="boundVehicleNo" label="绑定车辆" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.boundVehicleNo" size="small" type="success" effect="plain">{{ row.boundVehicleNo }}</el-tag>
            <span v-else style="color: #9ca3af">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'mdm:terminal:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'mdm:terminal:edit'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="filters.size"
        :current-page="filters.page"
        @current-change="(p) => { filters.page = p; load() }"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑终端' : '登记终端'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="终端编号" prop="identityCode">
          <el-input v-model="form.identityCode" maxlength="100" placeholder="设备出厂/入网唯一编号" />
        </el-form-item>
        <el-form-item label="厂商编码">
          <el-input v-model="form.oemCode" maxlength="40" />
        </el-form-item>
        <el-form-item label="设备型号">
          <el-input v-model="form.tlModel" maxlength="64" />
        </el-form-item>
        <el-form-item label="MAC 地址">
          <el-input v-model="form.tlMac" maxlength="16" />
        </el-form-item>
        <el-form-item label="SIM 卡号">
          <el-input v-model="form.simAccount" maxlength="32" placeholder="手机号 / SIM 卡号" />
        </el-form-item>
        <el-form-item label="网关TruckId">
          <el-input v-model="form.gatewayTruckId" maxlength="64" placeholder="网关侧 TruckId（可选）" />
        </el-form-item>
        <el-form-item label="通信协议">
          <el-select v-model="form.protocolType" style="width: 100%">
            <el-option v-for="p in PROTOCOL_TYPES" :key="p" :value="p" :label="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="form.equipmentType" style="width: 100%">
            <el-option v-for="t in EQUIPMENT_TYPES" :key="t.value" :value="String(t.value)" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="视频通道数">
          <el-input-number v-model="form.videoChannel" :min="0" :max="16" />
        </el-form-item>
        <el-form-item label="设备状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="s in TERMINAL_STATUS" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  createTerminal,
  deleteTerminal,
  getTerminals,
  updateTerminal,
  type Terminal,
  type TerminalSaveRequest
} from '@/api/mdm'
import { EQUIPMENT_TYPES, PROTOCOL_TYPES, TERMINAL_STATUS, labelOf, tagOf } from '@/constants/dict'

const loading = ref(false)
const records = ref<Terminal[]>([])
const total = ref(0)
const filters = reactive({
  page: 1,
  size: 10,
  keyword: '',
  simAccount: '',
  status: undefined as number | undefined,
  protocolType: '',
  equipmentType: ''
})

function fmt(v?: string | null) {
  return v ? v.replace('T', ' ').slice(0, 19) : '--'
}

async function load() {
  loading.value = true
  try {
    const res = await getTerminals({
      page: filters.page,
      size: filters.size,
      keyword: filters.keyword || undefined,
      simAccount: filters.simAccount || undefined,
      status: filters.status,
      protocolType: filters.protocolType || undefined,
      equipmentType: filters.equipmentType || undefined
    })
    records.value = res.records
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function resetFilters() {
  filters.keyword = ''
  filters.simAccount = ''
  filters.status = undefined
  filters.protocolType = ''
  filters.equipmentType = ''
  filters.page = 1
  load()
}

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

type TerminalForm = TerminalSaveRequest & { id?: number }
const emptyForm = (): TerminalForm => ({
  identityCode: '',
  tlMac: '',
  oemCode: '',
  tlModel: '',
  simAccount: '',
  gatewayTruckId: '',
  protocolType: 'JT808',
  equipmentType: '4',
  videoChannel: 4,
  status: 1,
  remark: ''
})
const form = reactive<TerminalForm>(emptyForm())

const rules = {
  identityCode: [{ required: true, message: '请输入终端编号', trigger: 'blur' }],
  status: [{ required: true, message: '请选择设备状态', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: Terminal) {
  Object.assign(form, {
    id: row.id,
    identityCode: row.identityCode,
    tlMac: row.tlMac || '',
    oemCode: row.oemCode || '',
    tlModel: row.tlModel || '',
    simAccount: row.simAccount || '',
    gatewayTruckId: row.gatewayTruckId || '',
    protocolType: row.protocolType || 'JT808',
    equipmentType: row.equipmentType ?? '4',
    videoChannel: row.videoChannel ?? 0,
    status: row.status,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const { id, ...payload } = form
    if (id) {
      await updateTerminal(id, payload)
      ElMessage.success('修改成功')
    } else {
      await createTerminal(payload)
      ElMessage.success('登记成功')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Terminal) {
  await ElMessageBox.confirm(`确认删除终端「${row.identityCode}」吗？已绑定车辆时将被系统拒绝。`, '删除确认', {
    type: 'warning'
  })
  await deleteTerminal(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
</style>
