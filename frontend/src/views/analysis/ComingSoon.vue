<template>
  <div class="coming-wrap">
    <el-result
      icon="info"
      title="功能建设中"
      :sub-title="`「${name}」正在规划建设中，敬请期待`"
    >
      <template #extra>
        <el-tag size="small" type="info" effect="plain">{{ code }}</el-tag>
      </template>
    </el-result>
  </div>
</template>

<script lang="ts">
/** 占位功能 code → 名称（与 17-analysis-menu.sql 菜单保持一致） */
const COMING_NAMES: Record<string, string> = {
  // 报表查询
  'track-report': '轨迹报表',
  mileage: '里程报表',
  driving: '行驶报表',
  parking: '停车报表',
  idling: '怠速报表',
  'driver-driving': '司机驾驶报表',
  offline: '离线报表',
  'faulty-vehicle': '故障车报表',
  'fuel-consumption': '油耗报表',
  refuel: '加油报表',
  'online-rate': '实时在线率',
  'vehicle-work': '车辆工作情况',
  'service-charge': '服务费报表',
  // 统计分析
  'vehicle-analysis': '车辆分析',
  'enterprise-analysis': '企业分析',
  'ent-security-analysis': '企业安全分析',
  'driver-analysis': '司机分析',
  'business-opportunity': '车辆商机分析',
  'track-integrity': '轨迹完整率'
}

/** 供 MainLayout 解析多标签标题：仅匹配占位路由，其余返回空串 */
export function comingTitle(path: string): string {
  const prefix = '/analysis/coming/'
  if (!path.startsWith(prefix)) {
    return ''
  }
  return COMING_NAMES[path.slice(prefix.length)] || '功能建设中'
}

export default { name: 'ComingSoon' }
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const code = computed(() => (route.params.code as string) || '')
const name = computed(() => COMING_NAMES[code.value] || code.value)
</script>

<style scoped>
.coming-wrap {
  display: flex;
  justify-content: center;
  padding-top: 60px;
}
</style>
