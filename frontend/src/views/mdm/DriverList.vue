<template>
  <div class="mdm-page">
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="姓名 / 手机号 / 驾驶证号" clearable style="width: 210px" @keyup.enter="search" />
        <el-select v-model="filters.status" placeholder="在职状态" clearable style="width: 120px">
          <el-option v-for="s in DRIVER_STATUS" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'mdm:driver:edit'" type="success" @click="openCreate">登记驾驶员</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="driverName" label="姓名" width="90" fixed="left" />
        <el-table-column label="性别" width="60">
          <template #default="{ row }">{{ row.sex === 2 ? '女' : '男' }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="140">
          <template #default="{ row }">{{ row.contactPhone || '--' }}</template>
        </el-table-column>
        <el-table-column prop="licenseCode" label="驾驶证号" width="130" />
        <el-table-column prop="licenceCategory" label="准驾车型" width="90">
          <template #default="{ row }">{{ row.licenceCategory || '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="tagOf(DRIVER_STATUS, row.status)">
              {{ labelOf(DRIVER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="boundVehicleNo" label="当前车辆" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.boundVehicleNo" size="small" type="success" effect="plain">{{ row.boundVehicleNo }}</el-tag>
            <span v-else style="color: #9ca3af">未排班</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'mdm:driver:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'mdm:driver:edit'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑驾驶员' : '登记驾驶员'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="driverName">
          <el-input v-model="form.driverName" maxlength="40" />
        </el-form-item>
        <el-form-item label="性别" prop="sex">
          <el-radio-group v-model="form.sex">
            <el-radio v-for="s in DRIVER_SEX" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="驾驶证号" prop="licenseCode">
          <el-input v-model="form.licenseCode" maxlength="40" />
        </el-form-item>
        <el-form-item label="准驾车型">
          <el-input v-model="form.licenceCategory" maxlength="32" placeholder="如 A2、B2、C1" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="form.idcard" maxlength="30" />
        </el-form-item>
        <el-form-item label="照片地址">
          <el-input v-model="form.driverImg" maxlength="128" placeholder="图片 URL（上传功能后续提供）" />
        </el-form-item>
        <el-form-item label="在职状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="s in DRIVER_STATUS" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
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
  createDriver,
  deleteDriver,
  getDrivers,
  updateDriver,
  type Driver,
  type DriverSaveRequest
} from '@/api/mdm'
import { DRIVER_SEX, DRIVER_STATUS, labelOf, tagOf } from '@/constants/dict'

const loading = ref(false)
const records = ref<Driver[]>([])
const total = ref(0)
const filters = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: undefined as number | undefined
})

async function load() {
  loading.value = true
  try {
    const res = await getDrivers({
      page: filters.page,
      size: filters.size,
      keyword: filters.keyword || undefined,
      status: filters.status
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
  filters.status = undefined
  filters.page = 1
  load()
}

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

type DriverForm = DriverSaveRequest & { id?: number }
const emptyForm = (): DriverForm => ({
  driverName: '',
  sex: 1,
  idcard: '',
  contactPhone: '',
  licenseCode: '',
  licenceCategory: '',
  driverImg: '',
  status: 1,
  remark: ''
})
const form = reactive<DriverForm>(emptyForm())

const rules = {
  driverName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  licenseCode: [{ required: true, message: '请输入驾驶证号', trigger: 'blur' }],
  status: [{ required: true, message: '请选择在职状态', trigger: 'change' }],
  contactPhone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: Driver) {
  Object.assign(form, {
    id: row.id,
    driverName: row.driverName,
    sex: row.sex,
    idcard: row.idcard || '',
    contactPhone: row.contactPhone || '',
    licenseCode: row.licenseCode,
    licenceCategory: row.licenceCategory || '',
    driverImg: row.driverImg || '',
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
      await updateDriver(id, payload)
      ElMessage.success('修改成功')
    } else {
      await createDriver(payload)
      ElMessage.success('登记成功')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Driver) {
  await ElMessageBox.confirm(`确认删除驾驶员「${row.driverName}」吗？存在有效绑定时将被系统拒绝。`, '删除确认', {
    type: 'warning'
  })
  await deleteDriver(row.id)
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
