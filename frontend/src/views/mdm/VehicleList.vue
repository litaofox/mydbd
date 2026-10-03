<template>
  <div class="mdm-page">
    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="车牌号 / VIN" clearable style="width: 170px" @keyup.enter="search" />
        <el-tree-select
          v-model="filters.deptId"
          :data="deptTree"
          :props="{ label: 'deptName', children: 'children', value: 'id' }"
          check-strictly
          clearable
          placeholder="所属组织（含下级）"
          style="width: 200px"
        />
        <el-select v-model="filters.plateColor" placeholder="车牌颜色" clearable style="width: 120px">
          <el-option v-for="c in PLATE_COLORS" :key="c" :value="c" :label="c" />
        </el-select>
        <el-select v-model="filters.operationType" placeholder="运营类型" clearable style="width: 120px">
          <el-option v-for="t in OPERATION_TYPES" :key="t.value" :value="t.value" :label="t.label" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'mdm:vehicle:edit'" type="success" @click="openCreate">登记车辆</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="vehicleNo" label="车牌" width="100" fixed="left" />
        <el-table-column prop="vehiclePlateColor" label="颜色" width="70" />
        <el-table-column prop="deptName" label="所属组织" width="140">
          <template #default="{ row }">{{ row.deptName || '--' }}</template>
        </el-table-column>
        <el-table-column label="车型/品牌" width="150">
          <template #default="{ row }">{{ [row.vehicleType, row.vehicleBrand].filter(Boolean).join(' / ') || '--' }}</template>
        </el-table-column>
        <el-table-column label="运营类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="tagOf(OPERATION_TYPES, row.operationType)">
              {{ labelOf(OPERATION_TYPES, row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="车主" width="130">
          <template #default="{ row }">{{ row.ownerName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="terminalIdentity" label="当前终端" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.terminalIdentity || '未绑定' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mainDriverName" label="主班司机" width="100">
          <template #default="{ row }">{{ row.mainDriverName || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'mdm:vehicle:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'mdm:vehicle:edit'" link type="warning" size="small" @click="openBinding(row)">绑定管理</el-button>
            <el-button v-perm="'mdm:vehicle:edit'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
        @current-change="(p) => { filters.page = p; load() }"
        @size-change="(s) => { filters.size = s; filters.page = 1; load() }"
      />
    </el-card>

    <!-- 新增/编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑车辆' : '登记车辆'" size="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="车牌号" prop="vehicleNo">
          <el-input v-model="form.vehicleNo" maxlength="40" />
        </el-form-item>
        <el-form-item label="车牌颜色" prop="vehiclePlateColor">
          <el-select v-model="form.vehiclePlateColor" style="width: 100%">
            <el-option v-for="c in PLATE_COLORS" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="车辆类型">
          <el-input v-model="form.vehicleType" placeholder="如 重型货车、冷链车" maxlength="40" />
        </el-form-item>
        <el-form-item label="车身颜色">
          <el-input v-model="form.vehicleColor" maxlength="10" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.vehicleBrand" maxlength="20" />
        </el-form-item>
        <el-form-item label="VIN 车架号">
          <el-input v-model="form.vin" maxlength="17" placeholder="17 位字母数字" />
        </el-form-item>
        <el-form-item label="运营类型">
          <el-select v-model="form.operationType" style="width: 100%">
            <el-option v-for="t in OPERATION_TYPES" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属行业">
          <el-input v-model="form.vehicleIndustry" placeholder="如 普货、冷链" maxlength="40" />
        </el-form-item>

        <el-divider content-position="left">归属与证件</el-divider>
        <el-form-item label="所属组织">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="车主名称">
          <el-input v-model="form.ownerName" maxlength="20" />
        </el-form-item>
        <el-form-item label="车主电话">
          <el-input v-model="form.ownerPhone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="道路运输证号">
          <el-input v-model="form.roadLicenseNo" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <!-- 绑定管理弹层 -->
    <el-dialog v-model="bindingVisible" :title="`绑定管理 - ${activeVehicle?.vehicleNo || ''}`" width="780px" @open="loadBinding">
      <el-row :gutter="12">
        <!-- 当前终端 -->
        <el-col :span="8">
          <el-card shadow="never" class="bind-card">
            <template #header>当前终端</template>
            <template v-if="binding?.currentTerminal">
              <div class="bind-line"><b>{{ binding.currentTerminal.terminalIdentity }}</b></div>
              <div class="bind-line">型号：{{ binding.currentTerminal.tlModel || '--' }}</div>
              <div class="bind-line">SIM：{{ binding.currentTerminal.simAccount || '--' }}</div>
              <div class="bind-line">安装人：{{ binding.currentTerminal.installer || '--' }}</div>
              <div class="bind-line">安装时间：{{ fmt(binding.currentTerminal.installTime) }}</div>
              <div style="margin-top: 10px">
                <el-button size="small" type="primary" @click="terminalDialog = true">换绑/绑定</el-button>
                <el-button size="small" type="danger" plain @click="onUnbindTerminal">解绑</el-button>
              </div>
            </template>
            <el-empty v-else description="未绑定终端" :image-size="60">
              <el-button size="small" type="primary" @click="terminalDialog = true">绑定终端</el-button>
            </el-empty>
          </el-card>
        </el-col>

        <!-- 在班司机 -->
        <el-col :span="8">
          <el-card shadow="never" class="bind-card">
            <template #header>
              在班司机
              <el-button size="small" type="primary" style="float: right" @click="driverDialog = true">绑定司机</el-button>
            </template>
            <div v-if="binding?.activeDrivers.length">
              <div v-for="d in binding.activeDrivers" :key="d.id" class="driver-row">
                <div>
                  <el-tag size="small" :type="d.driverType === 1 ? 'danger' : 'primary'">
                    {{ d.driverType === 1 ? '主班' : '副班' }}
                  </el-tag>
                  <b style="margin-left: 6px">{{ d.driverName }}</b>
                  <span class="bind-line"> {{ d.contactPhone || '' }} ｜ {{ d.licenceCategory || '' }}</span>
                </div>
                <el-button link type="danger" size="small" @click="onUnbindDriver(d.driverId)">解绑</el-button>
              </div>
            </div>
            <el-empty v-else description="暂无在班司机" :image-size="60" />
          </el-card>
        </el-col>

        <!-- 历史记录 -->
        <el-col :span="8">
          <el-card shadow="never" class="bind-card">
            <template #header>绑定历史</template>
            <el-timeline style="max-height: 300px; overflow-y: auto; padding-left: 4px">
              <el-timeline-item v-for="t in binding?.terminalHistory ?? []" :key="'t' + t.id"
                :type="t.status === 1 ? 'primary' : 'info'" size="normal" :timestamp="fmt(t.bindTime)">
                终端 {{ t.terminalIdentity }}
                <el-tag size="small" :type="t.status === 1 ? 'success' : 'info'">
                  {{ t.status === 1 ? '生效中' : `已解绑 ${fmt(t.unbindTime)}` }}
                </el-tag>
              </el-timeline-item>
              <el-timeline-item v-for="d in binding?.driverHistory ?? []" :key="'d' + d.id"
                :type="d.status === 1 ? 'warning' : 'info'" size="normal" :timestamp="fmt(d.bindTime)">
                司机 {{ d.driverName }}（{{ d.driverType === 1 ? '主班' : '副班' }}）
                <el-tag size="small" :type="d.status === 1 ? 'success' : 'info'">
                  {{ d.status === 1 ? '生效中' : `已解绑 ${fmt(d.unbindTime)}` }}
                </el-tag>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>
    </el-dialog>

    <!-- 绑定终端弹窗 -->
    <el-dialog v-model="terminalDialog" title="绑定终端" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="选择终端">
          <el-select v-model="terminalForm.terminalId" filterable placeholder="选择正常状态终端" style="width: 100%">
            <el-option v-for="o in terminalOptions" :key="o.id" :value="o.id" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定类型">
          <el-radio-group v-model="terminalForm.bindType">
            <el-radio :value="1">正式安装</el-radio>
            <el-radio :value="2">临时换装</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="安装人">
          <el-input v-model="terminalForm.installer" maxlength="40" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="terminalForm.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="terminalDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="onBindTerminal">确认绑定</el-button>
      </template>
    </el-dialog>

    <!-- 绑定司机弹窗 -->
    <el-dialog v-model="driverDialog" title="绑定司机" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="选择司机">
          <el-select v-model="driverForm.driverId" filterable placeholder="选择在岗司机" style="width: 100%">
            <el-option v-for="o in driverOptions" :key="o.id" :value="o.id" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="班别">
          <el-radio-group v-model="driverForm.driverType">
            <el-radio :value="1">主班</el-radio>
            <el-radio :value="2">副班</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="driverForm.remark" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="driverDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="onBindDriver">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  bindDriver,
  bindTerminal,
  createVehicle,
  deleteVehicle,
  getDeptTree,
  getDriverOptions,
  getTerminalOptions,
  getVehicleBindings,
  getVehicles,
  unbindDriver,
  unbindTerminal,
  updateVehicle,
  type Dept,
  type Option,
  type Vehicle,
  type VehicleBinding
} from '@/api/mdm'
import { OPERATION_TYPES, PLATE_COLORS, labelOf, tagOf } from '@/constants/dict'

const loading = ref(false)
const records = ref<Vehicle[]>([])
const total = ref(0)
const deptTree = ref<Dept[]>([])

const filters = reactive({
  page: 1,
  size: 10,
  keyword: '',
  deptId: undefined as number | undefined,
  plateColor: '',
  operationType: undefined as number | undefined
})

async function load() {
  loading.value = true
  try {
    const res = await getVehicles({
      page: filters.page,
      size: filters.size,
      keyword: filters.keyword || undefined,
      deptId: filters.deptId,
      plateColor: filters.plateColor || undefined,
      operationType: filters.operationType
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
  filters.deptId = undefined
  filters.plateColor = ''
  filters.operationType = undefined
  filters.page = 1
  load()
}

// ===== 新增/编辑 =====
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

interface VehicleForm {
  id?: number
  deptId: number | null
  vehicleNo: string
  vehiclePlateColor: string
  vin: string
  vehicleType: string
  operationType: number
  vehicleIndustry: string
  roadLicenseNo: string
  vehicleColor: string
  vehicleBrand: string
  ownerName: string
  ownerPhone: string
  remark: string
}

const emptyForm = (): VehicleForm => ({
  deptId: null,
  vehicleNo: '',
  vehiclePlateColor: '蓝色',
  vin: '',
  vehicleType: '',
  operationType: 1,
  vehicleIndustry: '',
  roadLicenseNo: '',
  vehicleColor: '',
  vehicleBrand: '',
  ownerName: '',
  ownerPhone: '',
  remark: ''
})
const form = reactive<VehicleForm>(emptyForm())

const rules = {
  vehicleNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  vehiclePlateColor: [{ required: true, message: '请选择车牌颜色', trigger: 'change' }],
  vin: [{ pattern: /^$|^[A-Za-z0-9]{17}$/, message: 'VIN 应为 17 位字母数字', trigger: 'blur' }],
  ownerPhone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, emptyForm())
  drawerVisible.value = true
}

function openEdit(row: Vehicle) {
  Object.assign(form, {
    id: row.id,
    deptId: row.deptId ?? null,
    vehicleNo: row.vehicleNo,
    vehiclePlateColor: row.vehiclePlateColor,
    vin: row.vin || '',
    vehicleType: row.vehicleType || '',
    operationType: row.operationType ?? 1,
    vehicleIndustry: row.vehicleIndustry || '',
    roadLicenseNo: row.roadLicenseNo || '',
    vehicleColor: row.vehicleColor || '',
    vehicleBrand: row.vehicleBrand || '',
    ownerName: row.ownerName || '',
    ownerPhone: row.ownerPhone || '',
    remark: row.remark || ''
  })
  drawerVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateVehicle(form.id, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createVehicle({ ...form })
      ElMessage.success('登记成功')
    }
    drawerVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Vehicle) {
  await ElMessageBox.confirm(`确认删除车辆「${row.vehicleNo}」吗？存在有效绑定时将被系统拒绝。`, '删除确认', {
    type: 'warning'
  })
  await deleteVehicle(row.id)
  ElMessage.success('已删除')
  await load()
}

// ===== 绑定管理 =====
const bindingVisible = ref(false)
const terminalDialog = ref(false)
const driverDialog = ref(false)
const acting = ref(false)
const activeVehicle = ref<Vehicle | null>(null)
const binding = ref<VehicleBinding | null>(null)
const terminalOptions = ref<Option[]>([])
const driverOptions = ref<Option[]>([])

const terminalForm = reactive({ terminalId: undefined as number | undefined, bindType: 1, installer: '', remark: '' })
const driverForm = reactive({ driverId: undefined as number | undefined, driverType: 1, remark: '' })

async function openBinding(row: Vehicle) {
  activeVehicle.value = row
  binding.value = null
  bindingVisible.value = true
}

async function loadBinding() {
  if (!activeVehicle.value) return
  binding.value = await getVehicleBindings(activeVehicle.value.id)
  const [terminals, drivers] = await Promise.all([getTerminalOptions(), getDriverOptions()])
  terminalOptions.value = terminals
  driverOptions.value = drivers
  terminalForm.terminalId = undefined
  terminalForm.bindType = 1
  terminalForm.installer = ''
  terminalForm.remark = ''
  driverForm.driverId = undefined
  driverForm.driverType = 1
  driverForm.remark = ''
}

async function onBindTerminal() {
  if (!terminalForm.terminalId) {
    ElMessage.warning('请选择终端')
    return
  }
  acting.value = true
  try {
    await bindTerminal(activeVehicle.value!.id, { ...terminalForm })
    ElMessage.success('终端绑定成功')
    terminalDialog.value = false
    await loadBinding()
    await load()
  } finally {
    acting.value = false
  }
}

async function onUnbindTerminal() {
  await ElMessageBox.confirm('确认解绑当前终端吗？', '解绑确认', { type: 'warning' })
  await unbindTerminal(activeVehicle.value!.id, '页面操作解绑')
  ElMessage.success('终端已解绑')
  await loadBinding()
  await load()
}

async function onBindDriver() {
  if (!driverForm.driverId) {
    ElMessage.warning('请选择司机')
    return
  }
  acting.value = true
  try {
    await bindDriver(activeVehicle.value!.id, { ...driverForm })
    ElMessage.success('司机绑定成功')
    driverDialog.value = false
    await loadBinding()
    await load()
  } finally {
    acting.value = false
  }
}

async function onUnbindDriver(driverId: number) {
  await ElMessageBox.confirm('确认解绑该司机吗？', '解绑确认', { type: 'warning' })
  await unbindDriver(activeVehicle.value!.id, driverId, '页面操作解绑')
  ElMessage.success('司机已解绑')
  await loadBinding()
  await load()
}

function fmt(t?: string | null): string {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

onMounted(async () => {
  deptTree.value = await getDeptTree()
  await load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.bind-card {
  min-height: 360px;
}
.bind-card :deep(.el-card__header) {
  font-weight: 600;
  font-size: 14px;
  padding: 10px 12px;
}
.bind-line {
  font-size: 13px;
  color: #4b5563;
  line-height: 1.9;
}
.driver-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px dashed #e5e7eb;
}
</style>
