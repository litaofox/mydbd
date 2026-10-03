<template>
  <div class="page">
    <el-card>
      <div class="toolbar">
        <el-radio-group v-model="unreadOnly" @change="reload">
          <el-radio-button :value="false">全部</el-radio-button>
          <el-radio-button :value="true">未读</el-radio-button>
        </el-radio-group>
        <el-button type="primary" plain :disabled="unread === 0" @click="onReadAll">全部已读</el-button>
        <span class="hint">未读 {{ unread }} 条</span>
      </div>

      <el-table :data="messages" size="small" @row-click="onRowClick">
        <el-table-column label="级别" width="70">
          <template #default="{ row }">
            <el-tag :type="row.level >= 3 ? 'danger' : row.level === 2 ? 'warning' : 'info'" size="small">
              {{ row.level >= 3 ? '高' : row.level === 2 ? '中' : '低' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ eventTypeName(row.eventType) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="createDate" label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isRead === 0" type="danger" size="small" effect="plain">未读</el-tag>
            <span v-else style="color: #9ca3af">已读</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click.stop="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  deleteMessage,
  listMessages,
  markRead,
  readAll,
  unreadCount,
  type NotifyMessage
} from '@/api/notify'

const router = useRouter()
const messages = ref<NotifyMessage[]>([])
const total = ref(0)
const page = ref(1)
const size = 15
const unreadOnly = ref(false)
const unread = ref(0)

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
}

function eventTypeName(e: string) {
  switch (e) {
    case 'ORDER_CREATE': return '新建工单'
    case 'ORDER_ASSIGN': return '工单分派'
    case 'ORDER_ESCALATE': return '升级督办'
    case 'ORDER_CLOSE': return '工单闭环'
    default: return e || '系统'
  }
}

async function load() {
  const r = await listMessages({ page: page.value, size, unreadOnly: unreadOnly.value })
  messages.value = r.records
  total.value = Number(r.total)
}

async function loadUnread() {
  try {
    const c = await unreadCount()
    unread.value = Number(c.count)
  } catch {
    /* 静默 */
  }
}

function reload() {
  page.value = 1
  load()
}

function onPage(p: number) {
  page.value = p
  load()
}

async function onRowClick(row: NotifyMessage) {
  if (row.isRead === 0) {
    await markRead(row.id)
    row.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
  }
  if (row.bizType === 'WORK_ORDER' && row.bizId) {
    router.push({ path: '/risk/orders', query: { openOrder: row.bizId } })
  }
}

async function onReadAll() {
  await readAll()
  ElMessage.success('已全部标记为已读')
  loadUnread()
  load()
}

async function onDelete(row: NotifyMessage) {
  await deleteMessage(row.id)
  if (row.isRead === 0) loadUnread()
  load()
}

onMounted(() => {
  Promise.all([load(), loadUnread()])
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  color: #6b7280;
  font-size: 12px;
}
</style>
