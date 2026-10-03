<template>
  <el-container style="height: 100%">
    <el-aside width="210px" style="background: #111827">
      <div class="logo">mydbd 北斗业务平台</div>
      <el-menu
        :default-active="activeMenu"
        background-color="#111827"
        text-color="#cbd5e1"
        active-text-color="#60a5fa"
        router
      >
        <template v-for="m in auth.menus" :key="m.id">
          <!-- 目录 -->
          <el-sub-menu v-if="m.menuType === 1 && visibleChildren(m).length" :index="'g' + m.id">
            <template #title>
              <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
              <span>{{ m.menuName }}</span>
            </template>
            <el-menu-item
              v-for="c in visibleChildren(m)"
              :key="c.id"
              :index="c.path || ''"
            >
              <el-icon v-if="c.icon"><component :is="c.icon" /></el-icon>
              <span>{{ c.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <!-- 单菜单（无层级） -->
          <el-menu-item v-else-if="m.menuType === 2 && m.visible !== 0 && m.path" :index="m.path">
            <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
            <span>{{ m.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="title">{{ route.meta.title || '' }}</div>
        <div class="header-right">
          <el-popover placement="bottom-end" :width="340" trigger="click" popper-class="notify-pop">
            <template #reference>
              <el-badge :value="notifyUnread" :max="99" :hidden="notifyUnread === 0" class="bell-badge">
                <el-icon class="bell"><Bell /></el-icon>
              </el-badge>
            </template>
            <div class="notify-panel">
              <div class="notify-head">
                <span>通知</span>
                <el-button link type="primary" size="small" :disabled="notifyUnread === 0" @click="onReadAll">全部已读</el-button>
              </div>
              <el-scrollbar max-height="300px">
                <div v-if="!notifyLatest.length" class="notify-empty">暂无消息</div>
                <div
                  v-for="m in notifyLatest"
                  :key="m.id"
                  class="notify-item"
                  @click="openMessage(m)"
                >
                  <el-tag :type="m.level >= 3 ? 'danger' : m.level === 2 ? 'warning' : 'info'" size="small" class="lv">
                    {{ m.level >= 3 ? '高' : m.level === 2 ? '中' : '低' }}
                  </el-tag>
                  <div class="body">
                    <div class="t" :class="{ unread: m.isRead === 0 }">{{ m.title }}</div>
                    <div class="time">{{ formatTime(m.createDate) }}</div>
                  </div>
                </div>
              </el-scrollbar>
              <div class="notify-foot">
                <el-button link type="primary" size="small" @click="goMessageCenter">消息中心</el-button>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="click" @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon>
            <span class="name">{{ auth.realName || auth.username }}</span>
            <el-tag v-if="mfaOn" size="small" type="warning" effect="plain">动态口令</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><Setting /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        </div>
      </el-header>

      <el-main style="padding: 0; background: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>

    <ProfileDialog ref="profileRef" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { useNotify } from '@/composables/useNotify'
import { readAll } from '@/api/notify'
import type { MenuNode } from '@/api/auth'
import type { NotifyMessage } from '@/api/notify'
import ProfileDialog from '@/components/ProfileDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notify = useNotify()
const { unread: notifyUnread, latest: notifyLatest } = notify
notify.init()

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

async function onReadAll() {
  await readAll()
  notifyUnread.value = 0
  notifyLatest.value.forEach((m) => (m.isRead = 1))
}

function openMessage(m: NotifyMessage) {
  if (m.isRead === 0) notify.readOne(m.id).catch(() => undefined)
  if (m.bizType === 'WORK_ORDER' && m.bizId) {
    router.push({ path: '/risk/orders', query: { openOrder: m.bizId } })
  } else {
    router.push('/system/messages')
  }
}

function goMessageCenter() {
  router.push('/system/messages')
}
const profileRef = ref<InstanceType<typeof ProfileDialog>>()

const activeMenu = computed(() => route.path)
const mfaOn = computed(() => auth.profile?.mfaEnabled === 1)

function visibleChildren(m: MenuNode): MenuNode[] {
  return (m.children || []).filter((c) => c.menuType === 2 && c.visible !== 0 && !!c.path)
}

async function onCommand(command: string) {
  if (command === 'profile') {
    profileRef.value?.open()
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return // 用户取消
    }
    // 先显式收起确认框遮罩（其关闭动画与后续跳转/状态重置存在时序竞争，曾残留遮罩拦截登录页）
    ElMessageBox.close()
    await auth.logout()
    // 硬跳转：彻底重置前端内存态与所有弹层，进入干净的登录页
    window.location.assign('/login')
  }
}
</script>

<style scoped>
.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #f8fafc;
  font-weight: 600;
  font-size: 15px;
  border-bottom: 1px solid #1f2937;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5e7eb;
}

.header .title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  outline: none;
}

.user .name {
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.bell-badge {
  margin-top: 4px;
}

.bell {
  font-size: 20px;
  color: #4b5563;
  cursor: pointer;
}

.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.notify-empty {
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  padding: 24px 0;
}

.notify-item {
  display: flex;
  gap: 8px;
  padding: 8px 4px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.notify-item:hover {
  background: #f8fafc;
}

.notify-item .lv {
  flex-shrink: 0;
  margin-top: 2px;
}

.notify-item .body {
  flex: 1;
  min-width: 0;
}

.notify-item .t {
  font-size: 12.5px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-item .t.unread {
  color: #111827;
  font-weight: 600;
}

.notify-item .time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}

.notify-foot {
  text-align: center;
  padding-top: 6px;
  border-top: 1px solid #f0f0f0;
}
</style>
