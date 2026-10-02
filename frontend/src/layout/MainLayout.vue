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
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon>
          <span>实时导航监控</span>
        </el-menu-item>
        <el-menu-item index="/playback">
          <el-icon><VideoPlay /></el-icon>
          <span>历史轨迹回放</span>
        </el-menu-item>
        <el-menu-item index="/risk">
          <el-icon><Warning /></el-icon>
          <span>风险预警分析</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="title">{{ route.meta.title || '' }}</div>
        <div class="user">
          <span>{{ auth.username }}（{{ auth.role }}）</span>
          <el-button link type="primary" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main style="padding: 0; background: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)

function onLogout() {
  auth.logout()
  router.push('/login')
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

.header .user {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #4b5563;
  font-size: 13px;
}
</style>
