<template>
  <div class="mdm-page">
    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="角色编码 / 名称" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'iam:role:edit'" type="success" @click="openCreate">新增角色</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色编码" width="170" />
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column label="数据范围" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="tagOf(DATA_SCOPES, row.dataScope)">
              {{ labelOf(DATA_SCOPES, row.dataScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.builtIn === 1" size="small" type="info">内置</el-tag>
            <span v-else class="muted">自定义</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="用户数" width="80" />
        <el-table-column prop="remark" label="备注" min-width="160">
          <template #default="{ row }">{{ row.remark || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'iam:role:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.builtIn !== 1"
              v-perm="'iam:role:edit'"
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
        :page-size="size"
        :current-page="page"
        @current-change="(p) => { page = p; load() }"
        @size-change="(s) => { size = s; page = 1; load() }"
      />
    </el-card>

    <!-- 新增/编辑抽屉（三分区） -->
    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑角色' : '新增角色'" size="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input
            v-model="form.roleCode"
            :disabled="!!form.id"
            maxlength="40"
            placeholder="大写字母开头，2~40 位大写字母/数字/下划线"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" maxlength="40" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-radio-group v-model="form.dataScope" :disabled="form.builtIn === 1">
            <el-radio v-for="s in DATA_SCOPES" :key="s.value" :value="s.value" style="display: block; margin: 6px 0">
              {{ s.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>

        <el-divider content-position="left">功能权限（菜单与按钮）</el-divider>
        <el-form-item label="权限点">
          <div class="tree-box">
            <el-tree
              ref="menuTreeRef"
              :data="menuTree"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="{ label: 'menuName', children: 'children' }"
            />
          </div>
        </el-form-item>

        <template v-if="form.dataScope === 5">
          <el-divider content-position="left">自定义数据范围（授权部门）</el-divider>
          <el-form-item label="授权部门">
            <div class="tree-box">
              <el-tree
                ref="deptTreeRef"
                :data="deptTree"
                node-key="id"
                show-checkbox
                check-strictly
                default-expand-all
                :default-checked-keys="editDeptIds"
                :props="{ label: 'deptName', children: 'children' }"
              />
            </div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import {
  pageRoles,
  getRole,
  createRole,
  updateRole,
  deleteRole,
  getMenuTree,
  type RoleListRow,
  type RoleSaveBody
} from '@/api/iam'
import type { MenuNode } from '@/api/auth'
import { getDeptTree, type Dept } from '@/api/mdm'
import { DATA_SCOPES, labelOf, tagOf } from '@/constants/dict'

const loading = ref(false)
const records = ref<RoleListRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref<number | null>(null)

const menuTree = ref<MenuNode[]>([])
const deptTree = ref<Dept[]>([])
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const deptTreeRef = ref<InstanceType<typeof ElTree>>()
/** 编辑角色时已授权部门，供部门树首次渲染回显 */
const editDeptIds = ref<number[]>([])

async function load() {
  loading.value = true
  try {
    const result = await pageRoles(page.value, size.value, keyword.value || undefined, statusFilter.value)
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = null
  page.value = 1
  load()
}

// ===== 新增/编辑 =====
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: null as string | null,
  roleCode: '',
  roleName: '',
  dataScope: 3,
  builtIn: 0,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]{1,39}$/,
      message: '大写字母开头，2~40 位大写字母/数字/下划线',
      trigger: 'blur'
    }
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, {
    id: null,
    roleCode: '',
    roleName: '',
    dataScope: 3,
    builtIn: 0,
    status: 1,
    remark: ''
  })
  editDeptIds.value = []
  drawerVisible.value = true
  nextTick(() => {
    menuTreeRef.value?.setCheckedKeys([])
    deptTreeRef.value?.setCheckedKeys([])
  })
}

async function openEdit(row: RoleListRow) {
  const detail = await getRole(row.id)
  Object.assign(form, {
    id: detail.id,
    roleCode: detail.roleCode,
    roleName: detail.roleName,
    dataScope: detail.dataScope,
    builtIn: detail.builtIn,
    status: detail.status,
    remark: detail.remark || ''
  })
  editDeptIds.value = detail.deptIds || []
  drawerVisible.value = true
  await nextTick()
  // menuIds 已包含半选祖先节点，setCheckedKeys 会重新推导勾选/半选态
  menuTreeRef.value?.setCheckedKeys(detail.menuIds || [])
  deptTreeRef.value?.setCheckedKeys(editDeptIds.value)
}

async function onSave() {
  await formRef.value?.validate()
  // 勾选叶子 + 半选父节点都要提交，保证菜单建树完整
  const checkedMenu = (menuTreeRef.value?.getCheckedKeys() || []) as number[]
  const halfMenu = (menuTreeRef.value?.getHalfCheckedKeys() || []) as number[]
  const menuIds = [...checkedMenu, ...halfMenu]
  if (menuIds.length === 0) {
    ElMessage.warning('请至少勾选一个功能权限')
    return
  }
  const deptIds = form.dataScope === 5 ? ((deptTreeRef.value?.getCheckedKeys() || []) as number[]) : []

  const body: RoleSaveBody = {
    roleCode: form.id ? undefined : form.roleCode,
    roleName: form.roleName,
    dataScope: form.dataScope,
    status: form.status,
    remark: form.remark || null,
    menuIds,
    deptIds
  }

  saving.value = true
  try {
    if (form.id) {
      await updateRole(form.id, body)
      ElMessage.success('角色已更新')
    } else {
      await createRole(body)
      ElMessage.success('角色创建成功')
    }
    drawerVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: RoleListRow) {
  await ElMessageBox.confirm(
    `确认删除自定义角色「${row.roleName}」？该角色下现有 ${row.userCount} 名用户将自动解除关联。`,
    '危险操作',
    { type: 'error', confirmButtonText: '删除' }
  )
  await deleteRole(row.id)
  ElMessage.success('角色已删除')
  load()
}

onMounted(async () => {
  const [menus, depts] = await Promise.all([getMenuTree(), getDeptTree()])
  menuTree.value = menus
  deptTree.value = depts
  load()
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

.tree-box {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 8px 12px;
  max-height: 300px;
  overflow: auto;
  width: 100%;
}
</style>
