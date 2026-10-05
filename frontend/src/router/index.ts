import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    // F15 监控总览大屏：顶层全屏路由（脱离 MainLayout，MOD-MON-002 §7.1）
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/Dashboard.vue'),
    meta: { title: '监控总览大屏', perm: 'monitor:dashboard:view' }
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
        meta: { title: '实时导航监控', perm: 'monitor:view' }
      },
      {
        path: 'alarms',
        name: 'alarm-center',
        component: () => import('@/views/alarm/AlarmCenter.vue'),
        meta: { title: '终端报警中心', perm: 'alarm:view' }
      },
      {
        path: 'playback',
        name: 'playback',
        component: () => import('@/views/playback/Playback.vue'),
        meta: { title: '历史轨迹回放', perm: 'playback:view' }
      },
      {
        path: 'risk',
        name: 'risk',
        component: () => import('@/views/risk/RiskEvents.vue'),
        meta: { title: '风险预警分析', perm: 'risk:view' }
      },
      {
        path: 'risk/orders',
        name: 'risk-orders',
        component: () => import('@/views/risk/RiskOrders.vue'),
        meta: { title: '处置工单', perm: 'risk:order:view' }
      },
      {
        path: 'system/rules',
        name: 'system-rules',
        component: () => import('@/views/system/RiskRuleList.vue'),
        meta: { title: '风控规则配置', perm: 'risk:rule:view' }
      },
      {
        path: 'system/fences',
        name: 'system-fences',
        component: () => import('@/views/system/GeoFenceList.vue'),
        meta: { title: '电子围栏管理', perm: 'risk:fence:view' }
      },
      {
        path: 'mdm/org',
        name: 'mdm-org',
        component: () => import('@/views/mdm/OrgTree.vue'),
        meta: { title: '组织架构', perm: 'mdm:org:view' }
      },
      {
        path: 'mdm/vehicles',
        name: 'mdm-vehicles',
        component: () => import('@/views/mdm/VehicleList.vue'),
        meta: { title: '车辆档案', perm: 'mdm:vehicle:view' }
      },
      {
        path: 'mdm/terminals',
        name: 'mdm-terminals',
        component: () => import('@/views/mdm/TerminalList.vue'),
        meta: { title: '终端档案', perm: 'mdm:terminal:view' }
      },
      {
        path: 'mdm/drivers',
        name: 'mdm-drivers',
        component: () => import('@/views/mdm/DriverList.vue'),
        meta: { title: '驾驶员档案', perm: 'mdm:driver:view' }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', perm: 'iam:user:view' }
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: '角色管理', perm: 'iam:role:view' }
      },
      {
        path: 'system/audit',
        name: 'system-audit',
        component: () => import('@/views/system/AuditLog.vue'),
        meta: { title: '操作审计', perm: 'audit:view' }
      },
      {
        path: 'system/dict',
        name: 'system-dict',
        component: () => import('@/views/system/DictList.vue'),
        meta: { title: '数据字典', perm: 'system:dict:view' }
      },
      {
        path: 'system/config',
        name: 'system-config',
        component: () => import('@/views/system/ConfigList.vue'),
        meta: { title: '系统参数', perm: 'system:config:view' }
      },
      {
        path: 'system/messages',
        name: 'system-messages',
        component: () => import('@/views/system/MessageCenter.vue'),
        meta: { title: '消息中心', perm: 'system:message:view' }
      },
      {
        path: 'analysis/scores',
        name: 'analysis-scores',
        component: () => import('@/views/analysis/ScoreList.vue'),
        meta: { title: '驾驶评分', perm: 'analysis:score:view' }
      },
      {
        path: 'analysis/profiles',
        name: 'analysis-profiles',
        component: () => import('@/views/analysis/RiskProfile.vue'),
        meta: { title: '风险趋势与画像', perm: 'analysis:profile:view' }
      },
      {
        // 查询分析占位：菜单挂全部规划功能，点击进入"功能建设中"
        path: 'analysis/coming/:code',
        name: 'analysis-coming',
        component: () => import('@/views/analysis/ComingSoon.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('mydbd-token')
  if (to.meta.public) {
    if (token && to.path === '/login') {
      return { path: '/' }
    }
    return true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const auth = useAuthStore()
  if (!auth.loaded) {
    try {
      await auth.loadProfile()
    } catch {
      auth.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const needPerm = to.meta.perm as string | undefined
  if (needPerm && !auth.has(needPerm)) {
    // 无权访问目标页：回落到其首个有权限的菜单
    const fallback = auth.firstMenuPath
    if (fallback !== to.path) {
      return { path: fallback }
    }
    return false
  }
  return true
})

export default router
