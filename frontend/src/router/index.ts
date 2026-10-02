import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/monitor',
    children: [
      {
        path: 'monitor',
        name: 'monitor',
        component: () => import('@/views/monitor/Monitor.vue'),
        meta: { title: '实时导航监控' }
      },
      {
        path: 'playback',
        name: 'playback',
        component: () => import('@/views/playback/Playback.vue'),
        meta: { title: '历史轨迹回放' }
      },
      {
        path: 'risk',
        name: 'risk',
        component: () => import('@/views/risk/RiskEvents.vue'),
        meta: { title: '风险预警分析' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('mydbd-token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/monitor' }
  }
  return true
})

export default router
