<template>
  <div class="mdm-page">
    <!-- 筛选 -->
    <el-card style="margin-bottom: 12px">
      <div class="toolbar">
        <el-input v-model="filters.username" placeholder="用户名" clearable style="width: 140px" @keyup.enter="search" />
        <el-input v-model="filters.realName" placeholder="姓名" clearable style="width: 120px" @keyup.enter="search" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 110px">
          <el-option v-for="s in USER_STATUS" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-tree-select
          v-model="filters.deptId"
          :data="deptTree"
          :props="{ label: 'deptName', children: 'children', value: 'id' }"
          check-strictly
          clearable
          placeholder="所属组织"
          style="width: 180px"
        />
        <el-select v-model="filters.roleId" placeholder="角色" clearable style="width: 160px">
          <el-option v-for="r in roleOptions" :key="r.id" :value="r.id" :label="r.roleName" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button v-perm="'iam:user:edit'" type="success" @click="openCreate">新增用户</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="120" fixed="left" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="所属组织" width="150">
          <template #default="{ row }">{{ row.deptName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="n in (row.roleNames || '').split(',').filter(Boolean)" :key="n" size="small" style="margin-right: 4px">
              {{ n }}
            </el-tag>
            <span v-if="!row.roleNames">--</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="tagOf(USER_STATUS, row.status)">{{ labelOf(USER_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动态口令" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.mfaEnabled === 1" size="small" type="warning">已开启</el-tag>
            <span v-else class="muted">未开启</span>
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.locked" size="small" type="danger">锁定中</el-tag>
            <span v-else class="muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最近登录" width="160">
          <template #default="{ row }">{{ row.lastLoginTime || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'iam:user:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="!isAdmin(row)" v-perm="'iam:user:edit'" link type="primary" size="small" @click="openRoles(row)">分配角色</el-button>
            <el-button v-if="!isAdmin(row)" v-perm="'iam:user:edit'" link type="warning" size="small" @click="openReset(row)">重置密码</el-button>
            <el-button v-if="row.locked" v-perm="'iam:user:edit'" link type="success" size="small" @click="onUnlock(row)">解锁</el-button>
            <el-button v-if="!isAdmin(row)" v-perm="'iam:user:edit'" link :type="row.status === 1 ? 'info' : 'success'" size="small" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="row.mfaEnabled === 1 && !isAdmin(row)" v-perm="'iam:user:edit'" link type="warning" size="small" @click="onDisableMfa(row)">关口令</el-button>
            <el-button v-if="!isAdmin(row)" v-perm="'iam:user:edit'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑用户' : '新增用户'" size="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" maxlength="40" :placeholder="form.id ? '' : '2~40 位字母数字'" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" maxlength="40" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password maxlength="20" placeholder="8~20 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="80" />
        </el-form-item>
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
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-drawer>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="rolesVisible" :title="`分配角色 - ${rolesTarget?.username || ''}`" width="420px">
      <el-checkbox-group v-model="roleIdsChecked">
        <el-checkbox v-for="r in roleOptions" :key="r.id" :value="r.id" style="display: block; margin: 8px 0">
          <b>{{ r.roleName }}</b>
          <span class="muted">（{{ r.roleCode }}，{{ labelOf(DATA_SCOPES, r.dataScope) }}）</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="onSaveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageUsers,
  getUser,
  createUser,
  updateUser,
  changeUserStatus,
  resetUserPassword,
  assignUserRoles,
  unlockUser,
  adminDisableUserMfa,
  deleteUser,
  listAllRoles,
  type UserListRow,
  type UserSaveBody,
  type RoleOption
} from '@/api/iam'
import { getDeptTree, type Dept } from '@/api/mdm'
import { USER_STATUS, DATA_SCOPES, labelOf, tagOf } from '@/constants/dict'

const loading = ref(false)
const records = ref<UserListRow[]>([])
const total = ref(0)
const deptTree = ref<Dept[]>([])
const roleOptions = ref<RoleOption[]>([])

const filters = reactive({
  page: 1,
  size: 10,
  username: '',
  realName: '',
  status: null as number | null,
  deptId: null as number | null,
  roleId: null as number | null
})

async function load() {
  loading.value = true
  try {
    const result = await pageUsers({ ...filters })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  load()
}

function resetFilters() {
  filters.username = ''
  filters.realName = ''
  filters.status = null
  filters.deptId = null
  filters.roleId = null
  filters.page = 1
  load()
}

function isAdmin(row: UserListRow) {
  return row.username === 'admin'
}

// ===== 新增/编辑 =====
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: null as number | null,
  username: '',
  realName: '',
  password: '',
  phone: '',
  email: '',
  deptId: null as number | null,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,40}$/, message: '2~40 位字母、数字或下划线', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (!value) return cb()
        if (value.length < 8 || value.length > 20) return cb(new Error('长度须为 8~20 位'))
        if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) return cb(new Error('须同时包含字母和数字'))
        cb()
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  Object.assign(form, {
    id: null,
    username: '',
    realName: '',
    password: '',
    phone: '',
    email: '',
    deptId: null,
    status: 1,
    remark: ''
  })
  drawerVisible.value = true
}

async function openEdit(row: UserListRow) {
  const detail = await getUser(row.id)
  Object.assign(form, {
    id: detail.id,
    username: detail.username,
    realName: detail.realName,
    password: '',
    phone: detail.phone || '',
    email: detail.email || '',
    deptId: detail.deptId ?? null,
    status: detail.status,
    remark: detail.remark || ''
  })
  drawerVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body: UserSaveBody = {
      username: form.id ? undefined : form.username,
      realName: form.realName,
      phone: form.phone || null,
      email: form.email || null,
      deptId: form.deptId,
      status: form.status,
      remark: form.remark || null
    }
    if (!form.id) {
      body.password = form.password
      await createUser(body)
      ElMessage.success('用户创建成功')
    } else {
      await updateUser(form.id, body)
      ElMessage.success('用户已更新')
    }
    drawerVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ===== 分配角色 =====
const rolesVisible = ref(false)
const savingRoles = ref(false)
const rolesTarget = ref<UserListRow | null>(null)
const roleIdsChecked = ref<string[]>([])

async function openRoles(row: UserListRow) {
  rolesTarget.value = row
  const detail = await getUser(row.id)
  roleIdsChecked.value = [...detail.roleIds]
  rolesVisible.value = true
}

async function onSaveRoles() {
  if (!rolesTarget.value) return
  savingRoles.value = true
  try {
    await assignUserRoles(rolesTarget.value.id, roleIdsChecked.value)
    ElMessage.success('角色已更新')
    rolesVisible.value = false
    load()
  } finally {
    savingRoles.value = false
  }
}

// ===== 行操作 =====
async function openReset(row: UserListRow) {
  const { value } = await ElMessageBox.prompt(`为「${row.username}」设置新密码（8~20 位，含字母和数字）`, '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'password',
    inputValidator: (v: string) => {
      if (v.length < 8 || v.length > 20) return '长度须为 8~20 位'
      if (!/[a-zA-Z]/.test(v) || !/\d/.test(v)) return '须同时包含字母和数字'
      return true
    }
  })
  await resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function onUnlock(row: UserListRow) {
  await unlockUser(row.id)
  ElMessage.success('用户已解锁')
  load()
}

async function onToggleStatus(row: UserListRow) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」？${next === 0 ? '停用后该用户无法登录。' : ''}`, '提示', {
    type: 'warning'
  })
  await changeUserStatus(row.id, next)
  ElMessage.success(`已${action}`)
  load()
}

async function onDisableMfa(row: UserListRow) {
  await ElMessageBox.confirm(`确认关闭「${row.username}」的动态口令？关闭后其登录仅需密码。`, '安全确认', {
    type: 'warning'
  })
  await adminDisableUserMfa(row.id)
  ElMessage.success('动态口令已关闭')
  load()
}

async function onDelete(row: UserListRow) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}（${row.realName}）」？删除后不可恢复。`, '危险操作', {
    type: 'error',
    confirmButtonText: '删除'
  })
  await deleteUser(row.id)
  ElMessage.success('用户已删除')
  load()
}

onMounted(async () => {
  const [depts, roles] = await Promise.all([getDeptTree(), listAllRoles()])
  deptTree.value = depts
  roleOptions.value = roles
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
</style>
