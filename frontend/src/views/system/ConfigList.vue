<template>
  <div class="mdm-page">
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConfigs, addConfig, updateConfig, deleteConfig, type SysConfig } from '@/api/system'

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

onMounted(load)
</script>
