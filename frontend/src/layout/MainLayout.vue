<template>
  <el-container style="height: 100%">
    <el-aside :width="asideCollapsed ? '64px' : '210px'" class="aside" style="background: #111827">
      <div class="logo" :class="{ mini: asideCollapsed }">
        <span v-if="!asideCollapsed">mydbd 北斗业务平台</span>
        <span v-else>北斗</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="asideCollapsed"
        :collapse-transition="false"
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
        <div class="header-left">
          <el-icon
            class="aside-toggle"
            role="button"
            :aria-label="asideCollapsed ? '展开导航菜单' : '收起导航菜单'"
            tabindex="0"
            @click="toggleAside"
            @keydown.enter="toggleAside"
          >
            <Expand v-if="asideCollapsed" />
            <Fold v-else />
          </el-icon>
          <div class="title">{{ route.meta.title || '' }}</div>
        </div>
        <div class="header-right">
          <el-tooltip content="监控总览大屏" placement="bottom">
            <el-icon
              class="dash-entry"
              role="button"
              aria-label="监控总览大屏"
              tabindex="0"
              @click="router.push('/dashboard')"
              @keydown.enter="router.push('/dashboard')"
            ><DataBoard /></el-icon>
          </el-tooltip>
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

      <!-- 多标签栏：一功能一标签（path 唯一），/monitor 固定不可关 -->
      <div class="tab-bar" role="tablist" aria-label="功能标签页">
        <div
          v-for="t in tabs.tabs"
          :key="t.path"
          class="tab-item"
          :class="{ active: route.path === t.path, affix: t.affix }"
          role="tab"
          :aria-selected="route.path === t.path"
          :aria-label="`标签页：${t.title}${t.affix ? '（固定）' : ''}`"
          tabindex="0"
          @click="activateTab(t)"
          @keydown.enter="activateTab(t)"
          @contextmenu.prevent="openTabMenu($event, t)"
        >
          <span class="tab-title">{{ t.title }}</span>
          <el-icon
            v-if="!t.affix"
            class="tab-close"
            role="button"
            :aria-label="`关闭 ${t.title}`"
            @click.stop="closeTab(t)"
            @keydown.enter.stop="closeTab(t)"
          ><Close /></el-icon>
        </div>
      </div>

      <el-main style="padding: 0; background: #f0f2f5">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="viewKey" />
        </router-view>
      </el-main>
    </el-container>

    <!-- 标签右键菜单 -->
    <Teleport to="body">
      <div v-if="tabMenu.visible" class="tab-menu-mask" @click="closeTabMenu" @contextmenu.prevent="closeTabMenu"></div>
      <div
        v-if="tabMenu.visible"
        class="tab-menu"
        role="menu"
        aria-label="标签操作"
        :style="{ left: tabMenu.x + 'px', top: tabMenu.y + 'px' }"
      >
        <div class="tab-menu-item" role="menuitem" tabindex="0" @click="onMenuRefresh">刷新当前</div>
        <div class="tab-menu-item" role="menuitem" tabindex="0" @click="onMenuCloseOthers">关闭其他</div>
        <div class="tab-menu-item" role="menuitem" tabindex="0" @click="onMenuCloseAll">关闭全部</div>
      </div>
    </Teleport>

    <ProfileDialog ref="profileRef" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { useTabsStore, type TabItem } from '@/store/tabs'
import { useNotify } from '@/composables/useNotify'
import { readAll } from '@/api/notify'
import type { MenuNode } from '@/api/auth'
import type { NotifyMessage } from '@/api/notify'
import ProfileDialog from '@/components/ProfileDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const tabs = useTabsStore()
const notify = useNotify()
const { unread: notifyUnread, latest: notifyLatest } = notify
notify.init()

// ========================= 侧栏折叠 =========================
const asideCollapsed = ref(localStorage.getItem('mydbd-aside-collapsed') === '1')

function toggleAside() {
  asideCollapsed.value = !asideCollapsed.value
  localStorage.setItem('mydbd-aside-collapsed', asideCollapsed.value ? '1' : '0')
}

// ========================= 多标签页 =========================
// 固定标签：/monitor 不可关闭（sessionStorage 恢复后缺失则补回）
tabs.ensureAffix('/monitor', '实时导航监控')

// 当前页视图 key：path + 刷新序号，"刷新当前"靠序号自增触发组件重建
const viewKey = computed(() => route.path + '#' + tabs.refreshSeq)

// 路由变化登记标签（一功能一标签）；/dashboard 与 /login 不在布局内，双保险跳过
watch(
  () => route.fullPath,
  () => {
    if (route.path === '/login' || route.path === '/dashboard') return
    tabs.visit(route.path, route.fullPath, (route.meta.title as string) || route.path)
  },
  { immediate: true }
)

function activateTab(t: TabItem) {
  if (route.fullPath !== t.fullPath) router.push(t.fullPath).catch(() => undefined)
}

function closeTab(t: TabItem) {
  if (t.affix) return
  const next = tabs.remove(t.path, route.path)
  if (next) {
    if (route.fullPath !== next) router.push(next)
  } else if (route.path === t.path) {
    router.push('/monitor')
  }
}

const tabMenu = ref({ visible: false, x: 0, y: 0, path: '' })

function openTabMenu(ev: MouseEvent, t: TabItem) {
  tabMenu.value = { visible: true, x: ev.clientX, y: ev.clientY, path: t.path }
}

function closeTabMenu() {
  tabMenu.value.visible = false
}

function onMenuRefresh() {
  closeTabMenu()
  tabs.refresh()
}

function onMenuCloseOthers() {
  closeTabMenu()
  const stay = tabs.closeOthers(tabMenu.value.path)
  if (route.fullPath !== stay) router.push(stay).catch(() => undefined)
}

function onMenuCloseAll() {
  closeTabMenu()
  const dest = tabs.closeAll()
  if (route.path !== dest) router.push(dest).catch(() => undefined)
}

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
    tabs.reset() // 清空会话标签，避免下个会话恢复上个账号的标签
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
  white-space: nowrap;
  overflow: hidden;
}

.logo.mini {
  font-size: 14px;
  letter-spacing: 1px;
}

.aside {
  transition: width 0.25s ease;
  overflow: hidden;
}

.aside-toggle {
  font-size: 18px;
  color: #4b5563;
  cursor: pointer;
  outline: none;
}

.aside-toggle:hover {
  color: #2563eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
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

.dash-entry {
  font-size: 20px;
  color: #4b5563;
  cursor: pointer;
}
.dash-entry:hover {
  color: #2563eb;
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

/* ========================= 多标签栏 ========================= */
.tab-bar {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 6px 12px 0;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  overflow-x: auto;
  flex-shrink: 0;
}

.tab-bar::-webkit-scrollbar {
  height: 3px;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
  color: #6b7280;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  position: relative;
  outline: none;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-bottom: none;
  border-radius: 8px 8px 0 0;
}

.tab-item:hover {
  color: #2563eb;
  background: #e8eefb;
}

/* 激活标签：白底卡片 + 蓝描边 + 加粗蓝字 + 3px 下划线 + 投影，与普通标签强对比 */
.tab-item.active {
  color: #1d4ed8;
  font-weight: 600;
  background: #fff;
  border-color: #93c5fd;
  box-shadow: 0 -2px 6px rgba(37, 99, 235, 0.1);
  z-index: 1;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 0;
  height: 3px;
  background: #2563eb;
  border-radius: 2px 2px 0 0;
}

.tab-item.affix .tab-title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #60a5fa;
  margin-right: 5px;
  vertical-align: 1px;
}

.tab-close {
  font-size: 12px;
  border-radius: 3px;
  padding: 1px;
  color: #9ca3af;
}

.tab-close:hover {
  background: #e5e7eb;
  color: #374151;
}

/* 标签右键菜单 */
.tab-menu-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.tab-menu {
  position: fixed;
  z-index: 3001;
  min-width: 110px;
  padding: 4px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.tab-menu-item {
  padding: 6px 12px;
  font-size: 12.5px;
  color: #374151;
  border-radius: 4px;
  cursor: pointer;
  outline: none;
}

.tab-menu-item:hover {
  background: #eff6ff;
  color: #2563eb;
}
</style>
