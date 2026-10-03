<template>
  <div class="mdm-page">
    <el-card>
      <div class="dict-layout">
        <!-- 左侧：字典类型 -->
        <div class="dict-left">
          <div class="panel-title">
            <span>字典类型</span>
            <el-button v-perm="'system:dict:edit'" type="primary" size="small" @click="openType()">新增</el-button>
          </div>
          <el-input v-model="typeKeyword" placeholder="搜索字典" clearable size="small" style="margin-bottom: 8px" @input="loadTypes" />
          <el-table :data="types" size="small" highlight-current-row @current-change="onTypeChange" style="cursor: pointer">
            <el-table-column prop="dictName" label="名称" />
            <el-table-column prop="dictCode" label="编码" width="110" />
            <el-table-column label="状态" width="56">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启' : '停' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" v-perm="'system:dict:edit'">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openType(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="removeType(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 右侧：字典项 -->
        <div class="dict-right">
          <div class="panel-title">
            <span>字典项 - {{ currentType?.dictName || '请选择左侧字典' }}</span>
            <el-button v-perm="'system:dict:edit'" type="primary" size="small" :disabled="!currentType" @click="openItem()">新增</el-button>
          </div>
          <el-table :data="items" size="small" v-loading="itemsLoading">
            <el-table-column prop="sort" label="排序" width="70" />
            <el-table-column prop="itemLabel" label="显示名" />
            <el-table-column prop="itemValue" label="存储值" width="140" />
            <el-table-column label="状态" width="60">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启' : '停' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="cssClass" label="样式" width="100" />
            <el-table-column label="操作" width="120" v-perm="'system:dict:edit'">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openItem(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeItem(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-card>

    <!-- 类型弹窗 -->
    <el-dialog v-model="typeDialog" :title="typeForm.id ? '编辑字典类型' : '新增字典类型'" width="420px">
      <el-form :model="typeForm" label-width="80px">
        <el-form-item label="编码"><el-input v-model="typeForm.dictCode" :disabled="!!typeForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="typeForm.dictName" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="typeForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 项弹窗 -->
    <el-dialog v-model="itemDialog" :title="itemForm.id ? '编辑字典项' : '新增字典项'" width="420px">
      <el-form :model="itemForm" label-width="80px">
        <el-form-item label="显示名"><el-input v-model="itemForm.itemLabel" /></el-form-item>
        <el-form-item label="存储值"><el-input v-model="itemForm.itemValue" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="itemForm.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="itemForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="样式"><el-input v-model="itemForm.cssClass" placeholder="如 danger" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="itemForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDictTypes, addDictType, updateDictType, deleteDictType,
  getDictItems, addDictItem, updateDictItem, deleteDictItem,
  type DictType, type DictItem
} from '@/api/system'

const typeKeyword = ref('')
const types = ref<DictType[]>([])
const currentType = ref<DictType | null>(null)
const items = ref<DictItem[]>([])
const itemsLoading = ref(false)

const typeDialog = ref(false)
const typeForm = ref<DictType>({ dictCode: '', dictName: '', status: 1 })

const itemDialog = ref(false)
const itemForm = ref<DictItem>({ dictTypeId: 0, itemLabel: '', itemValue: '', sort: 0, status: 1 })

async function loadTypes() {
  const data = await getDictTypes({ page: 1, size: 200, keyword: typeKeyword.value || undefined })
  types.value = data.records
  if (currentType.value) {
    const keep = types.value.find((t) => t.id === currentType.value!.id)
    if (!keep) {
      currentType.value = null
      items.value = []
    }
  }
}

async function onTypeChange(row: DictType | null) {
  currentType.value = row
  if (row?.id) {
    itemsLoading.value = true
    try {
      items.value = await getDictItems(row.id)
    } finally {
      itemsLoading.value = false
    }
  } else {
    items.value = []
  }
}

function openType(row?: DictType) {
  typeForm.value = row ? { ...row } : { dictCode: '', dictName: '', status: 1 }
  typeDialog.value = true
}

async function saveType() {
  if (!typeForm.value.dictCode || !typeForm.value.dictName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  if (typeForm.value.id) {
    await updateDictType(typeForm.value.id, typeForm.value)
  } else {
    await addDictType(typeForm.value)
  }
  typeDialog.value = false
  ElMessage.success('保存成功')
  loadTypes()
}

async function removeType(row: DictType) {
  await ElMessageBox.confirm(`确认停用字典「${row.dictName}」？`, '提示', { type: 'warning' })
  await deleteDictType(row.id!)
  ElMessage.success('已停用')
  loadTypes()
  if (currentType.value?.id === row.id) {
    currentType.value = null
    items.value = []
  }
}

function openItem(row?: DictItem) {
  itemForm.value = row
    ? { ...row }
    : { dictTypeId: currentType.value!.id!, itemLabel: '', itemValue: '', sort: 0, status: 1 }
  itemDialog.value = true
}

async function saveItem() {
  if (!itemForm.value.itemLabel || !itemForm.value.itemValue) {
    ElMessage.warning('请填写显示名和存储值')
    return
  }
  if (itemForm.value.id) {
    await updateDictItem(itemForm.value.id, itemForm.value)
  } else {
    await addDictItem(itemForm.value)
  }
  itemDialog.value = false
  ElMessage.success('保存成功')
  if (currentType.value) onTypeChange(currentType.value)
}

async function removeItem(row: DictItem) {
  await ElMessageBox.confirm(`确认停用字典项「${row.itemLabel}」？`, '提示', { type: 'warning' })
  await deleteDictItem(row.id!)
  ElMessage.success('已停用')
  if (currentType.value) onTypeChange(currentType.value)
}

onMounted(loadTypes)
</script>

<style scoped>
.dict-layout { display: flex; gap: 16px; height: calc(100vh - 180px); min-height: 480px; }
.dict-left { width: 320px; display: flex; flex-direction: column; }
.dict-right { flex: 1; display: flex; flex-direction: column; overflow: auto; }
.panel-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-weight: 600; }
</style>
