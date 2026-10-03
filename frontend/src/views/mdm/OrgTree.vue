<template>
  <div class="mdm-page">
    <el-row :gutter="12" style="height: calc(100vh - 96px)">
      <!-- 左：组织树 -->
      <el-col :span="9">
        <el-card class="full-card">
          <template #header>
            <div class="card-header">
              <span>组织架构</span>
              <el-button v-perm="'mdm:org:edit'" type="primary" size="small" @click="openCreate(null)">新增根单位</el-button>
            </div>
          </template>
          <el-tree
            :data="treeData"
            node-key="id"
            :props="{ label: 'deptName', children: 'children' }"
            :expand-on-click-node="false"
            highlight-current
            @node-click="onSelect"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span class="tree-label">
                  {{ data.deptName }}
                  <el-tag size="small" :type="data.deptType === 1 ? 'primary' : 'info'" effect="plain">
                    {{ data.deptType === 1 ? '企业' : '车队' }}
                  </el-tag>
                </span>
                <span class="tree-ops" @click.stop>
                  <el-button v-perm="'mdm:org:edit'" link type="primary" size="small" @click="openCreate(data)">新增下级</el-button>
                  <el-button v-perm="'mdm:org:edit'" link type="primary" size="small" @click="openEdit(data)">编辑</el-button>
                  <el-button v-perm="'mdm:org:edit'" link type="danger" size="small" @click="onDelete(data)">删除</el-button>
                </span>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右：详情与车辆 -->
      <el-col :span="15">
        <el-card v-if="selected" class="full-card">
          <template #header>
            <div class="card-header">
              <span>{{ selected.deptName }}</span>
              <el-button v-perm="'mdm:org:edit'" type="primary" size="small" @click="openEdit(selected)">编辑</el-button>
            </div>
          </template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="单位类型">
              {{ selected.deptType === 1 ? '运输企业' : '车队/部门' }}
            </el-descriptions-item>
            <el-descriptions-item label="单位编码">{{ selected.deptCode || '--' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ selected.contactPerson || '--' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ selected.contactPhone || '--' }}</el-descriptions-item>
            <el-descriptions-item label="详细地址" :span="2">{{ selected.address || '--' }}</el-descriptions-item>
            <el-descriptions-item label="有效车辆数">
              <el-tag type="success">{{ selected.vehicleCount ?? 0 }} 台</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="排序号">{{ selected.sortNo }}</el-descriptions-item>
          </el-descriptions>

          <div style="margin: 14px 0 8px; font-weight: 600; color: #303133">该单位下车辆</div>
          <el-table :data="vehicles" size="small" max-height="260">
            <el-table-column prop="vehicleNo" label="车牌" width="110" />
            <el-table-column prop="vehiclePlateColor" label="车牌颜色" width="90" />
            <el-table-column prop="vehicleType" label="车型" />
            <el-table-column prop="vehicleBrand" label="品牌" width="90" />
            <el-table-column prop="mainDriverName" label="主班司机" width="100">
              <template #default="{ row }">{{ row.mainDriverName || '--' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty v-else description="请选择左侧组织查看详情" style="margin-top: 120px" />
      </el-col>
    </el-row>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑组织' : '新增组织'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="上级组织">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', children: 'children', value: 'id' }"
            check-strictly
            clearable
            placeholder="不选为根组织"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="单位名称" prop="deptName">
          <el-input v-model="form.deptName" maxlength="60" />
        </el-form-item>
        <el-form-item label="单位类型" prop="deptType">
          <el-radio-group v-model="form.deptType">
            <el-radio :value="1">运输企业</el-radio>
            <el-radio :value="2">车队/部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="单位编码">
          <el-input v-model="form.deptCode" maxlength="40" placeholder="可选，启用后唯一" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactPerson" maxlength="30" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" maxlength="120" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  createDept,
  deleteDept,
  getDeptTree,
  getVehicles,
  updateDept,
  type Dept,
  type Vehicle
} from '@/api/mdm'

const treeData = ref<Dept[]>([])
const selected = ref<Dept | null>(null)
const vehicles = ref<Vehicle[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

interface DeptForm {
  id?: number
  parentId: number | null
  deptName: string
  deptCode: string
  deptType: number
  contactPerson: string
  contactPhone: string
  address: string
  sortNo: number
}

const emptyForm = (): DeptForm => ({
  parentId: null,
  deptName: '',
  deptCode: '',
  deptType: 1,
  contactPerson: '',
  contactPhone: '',
  address: '',
  sortNo: 0
})
const form = reactive<DeptForm>(emptyForm())

const rules = {
  deptName: [{ required: true, message: '请输入单位名称', trigger: 'blur' }],
  deptType: [{ required: true, message: '请选择单位类型', trigger: 'change' }],
  contactPhone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

/** 上级组织候选：编辑时排除自身（el-tree-select 挂 check-strictly，可任选层级） */
const parentOptions = computed(() => treeData.value)

async function loadTree(preserveSelectId?: number) {
  treeData.value = await getDeptTree()
  if (preserveSelectId) {
    const found = findNode(treeData.value, preserveSelectId)
    if (found) {
      selected.value = found
      await loadVehicles(found.id)
    }
  }
}

function findNode(nodes: Dept[], id: number): Dept | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const hit = findNode(n.children, id)
      if (hit) return hit
    }
  }
  return null
}

async function onSelect(data: Dept) {
  selected.value = data
  await loadVehicles(data.id)
}

async function loadVehicles(deptId: number) {
  const res = await getVehicles({ deptId, size: 100 })
  vehicles.value = res.records
}

function openCreate(parent: Dept | null) {
  Object.assign(form, emptyForm())
  if (parent) form.parentId = parent.id
  dialogVisible.value = true
}

function openEdit(data: Dept) {
  Object.assign(form, {
    id: data.id,
    parentId: data.parentId && data.parentId !== 0 ? data.parentId : null,
    deptName: data.deptName,
    deptCode: data.deptCode || '',
    deptType: data.deptType,
    contactPerson: data.contactPerson || '',
    contactPhone: data.contactPhone || '',
    address: data.address || '',
    sortNo: data.sortNo
  })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...form, parentId: form.parentId ?? 0 }
    if (form.id) {
      await updateDept(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createDept(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadTree(form.id)
  } finally {
    saving.value = false
  }
}

async function onDelete(data: Dept) {
  await ElMessageBox.confirm(
    `确认删除组织「${data.deptName}」吗？存在下级单位或有效车辆时将被系统拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteDept(data.id)
  ElMessage.success('已删除')
  if (selected.value?.id === data.id) {
    selected.value = null
    vehicles.value = []
  }
  await loadTree()
}

onMounted(() => loadTree())
</script>

<style scoped>
.full-card {
  height: 100%;
  overflow-y: auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}
.tree-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}
.tree-ops {
  display: none;
}
.tree-node:hover .tree-ops {
  display: inline-flex;
}
</style>
