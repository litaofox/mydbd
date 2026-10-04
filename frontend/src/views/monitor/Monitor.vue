<template>
  <div class="monitor-page">
    <!-- 页面标题条 -->
    <div class="m-head">
      <div>
        <div class="m-title">实时监控</div>
      </div>
      <span class="m-sub">车辆位置 · 状态分布 · 报警总览</span>
      <div class="m-head-right">
        <span class="conn-pill" :class="{ off: !connected }">
          <span class="conn-dot" :class="{ off: !connected }"></span>
          {{ connected ? '实时连接 · 1s 推送' : '降级轮询 · 5s' }}
        </span>
        <el-select v-model="windowMin" size="small" style="width: 128px" title="在线判定窗口">
          <el-option :value="10" label="窗口 10 分钟" />
          <el-option :value="30" label="窗口 30 分钟" />
          <el-option :value="60" label="窗口 1 小时" />
          <el-option :value="1440" label="窗口 24 小时" />
        </el-select>
        <el-button size="small" :icon="Refresh" circle title="手动刷新" @click="manualRefresh" />
      </div>
    </div>

    <div class="m-workspace">
      <!-- 左栏：搜索 + 组织树 + 状态统计 -->
      <div class="m-left">
        <div class="m-search">
          <el-input v-model="keyword" size="small" placeholder="车牌号 / 终端号 / SIM 卡号" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>

        <div class="m-tree">
          <el-tree
            ref="treeRef"
            :data="treeData"
            node-key="key"
            show-checkbox
            :default-expand-all="true"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            @check="onTreeCheck"
            @node-contextmenu="onTreeContextMenu"
          >
            <template #default="{ data }">
              <div v-if="data.nodeType === 'dept'" class="tree-row">
                <span class="tree-label">{{ data.label }}</span>
                <span class="tree-count">{{ data.count }}</span>
              </div>
              <div v-else class="tree-row vehicle" :title="`右键 ${data.label} 打开车辆操作`">
                <span class="v-dot" :class="'d-' + statusOf(vmMap.get(data.plate))"></span>
                <span class="tree-label plate">{{ data.label }}</span>
                <span v-if="statusOf(vmMap.get(data.plate)) === 'alarm'" class="tree-alarm">警</span>
              </div>
            </template>
          </el-tree>
          <div v-if="treeData.length === 0" class="tree-empty">暂无可查看的车辆</div>
        </div>

        <div class="m-stats">
          <div class="stat-cell" :class="{ active: activeTab === 'all' }" @click="pickTab('all')">
            <span class="stat-dot s-total"></span><span class="stat-num">{{ stats.total }}</span><span class="stat-name">车辆总数</span>
          </div>
          <div class="stat-cell">
            <span class="stat-dot s-online"></span><span class="stat-num" style="color:#16a34a">{{ stats.online }}</span><span class="stat-name">在线</span>
          </div>
          <div class="stat-cell" :class="{ active: activeTab === 'drive' }" @click="pickTab('drive')">
            <span class="stat-dot s-drive"></span><span class="stat-num">{{ stats.drive }}</span><span class="stat-name">行驶中</span>
          </div>
          <div class="stat-cell" :class="{ active: activeTab === 'stop' }" @click="pickTab('stop')">
            <span class="stat-dot s-stop"></span><span class="stat-num">{{ stats.stop }}</span><span class="stat-name">停车</span>
          </div>
          <div class="stat-cell" :class="{ active: activeTab === 'offline' }" @click="pickTab('offline')">
            <span class="stat-dot s-off"></span><span class="stat-num">{{ stats.offline }}</span><span class="stat-name">离线</span>
          </div>
          <div class="stat-cell" :class="{ active: activeTab === 'alarm' }" @click="pickTab('alarm')">
            <span class="stat-dot s-alarm"></span><span class="stat-num stat-alarm-blink">{{ stats.alarm }}</span><span class="stat-name">报警</span>
          </div>
        </div>
      </div>

      <!-- 右区：地图 + 底部车辆表 -->
      <div class="m-right">
        <div ref="mapCardRef" class="m-mapcard">
          <div ref="mapRef" class="m-map"></div>

          <div class="map-conn-badge">
            <span class="conn-dot" :class="{ off: !connected }"></span>
            实时推送：{{ connected ? '已连接' : '降级轮询' }}
          </div>

          <div class="map-toolbar">
            <div class="addr-box">
              <el-input
                v-model="addrKeyword"
                size="small"
                placeholder="搜索地点"
                class="addr-input"
                @keyup.enter="doPlaceSearch"
              />
              <button class="addr-go" title="搜索" @click="doPlaceSearch">→</button>
            </div>
            <button class="map-hbtn" title="切换底图（标准/卫星）" @click="toggleLayer">🗺</button>
            <button class="map-hbtn" title="复位视野" @click="resetView">⤢</button>
          </div>

          <div class="map-legend">
            <span><i style="background:#2563eb"></i>行驶</span>
            <span><i style="background:#9ca3af"></i>停车</span>
            <span><i style="background:#dc2626"></i>报警</span>
            <span class="legend-tip">车辆图标右键可操作</span>
          </div>
        </div>

        <div class="m-bottom" :class="{ collapsed: bottomCollapsed }">
          <div class="bottom-head">
            <div class="m-tabs">
              <div class="m-tab" :class="{ active: activeTab === 'all' }" @click="pickTab('all')">全部<span class="tab-badge">({{ stats.total }})</span></div>
              <div class="m-tab" :class="{ active: activeTab === 'drive' }" @click="pickTab('drive')">行驶中<span class="tab-badge">({{ stats.drive }})</span></div>
              <div class="m-tab" :class="{ active: activeTab === 'stop' }" @click="pickTab('stop')">停车<span class="tab-badge">({{ stats.stop }})</span></div>
              <div class="m-tab" :class="{ active: activeTab === 'offline' }" @click="pickTab('offline')">离线<span class="tab-badge">({{ stats.offline }})</span></div>
              <div class="m-tab" :class="{ active: activeTab === 'alarm' }" @click="pickTab('alarm')">报警<span class="tab-badge alarm-badge">({{ stats.alarm }})</span></div>
            </div>
            <div class="bottom-tools">
              <button class="map-hbtn" :title="followPlate ? '停止跟踪' : '提示：可在车辆右键菜单中持续跟踪'" @click="toggleFollow()">
                🎯
              </button>
              <button class="map-hbtn" :title="bottomCollapsed ? '展开' : '折叠'" @click="toggleBottom">
                {{ bottomCollapsed ? '▴' : '▾' }}
              </button>
            </div>
          </div>
          <div v-show="!bottomCollapsed" class="tbl-wrap">
            <el-table
              :data="tableRows"
              size="small"
              height="100%"
              @row-click="focusVehicle"
              @row-contextmenu="onRowContextMenu"
            >
              <el-table-column type="index" label="序号" width="56" />
              <el-table-column label="车牌号" min-width="110">
                <template #default="{ row }">
                  <span class="mini-plate"><span class="v-dot" :class="'d-' + statusOf(row)"></span>{{ row.plate }}</span>
                </template>
              </el-table-column>
              <el-table-column label="所属车队" prop="deptName" min-width="150" show-overflow-tooltip />
              <el-table-column label="定位时间" min-width="168">
                <template #default="{ row }">{{ fmtTime(row.point?.gpsTime) }}</template>
              </el-table-column>
              <el-table-column label="速度" width="92">
                <template #default="{ row }">{{ row.point ? row.point.speed + ' km/h' : '—' }}</template>
              </el-table-column>
              <el-table-column label="方向" width="76">
                <template #default="{ row }">{{ row.point?.direction != null ? row.point.direction + '°' : '—' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="132">
                <template #default="{ row }">
                  <span class="state-tag" :class="'st-' + statusOf(row)">{{ statusText(row) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>

    <VehicleContextMenu
      :visible="ctx.visible"
      :x="ctx.x"
      :y="ctx.y"
      :plate="ctx.plate"
      :identity-code="ctx.identityCode"
      :vehicle-id="ctx.vehicleId"
      :following="ctx.plate === followPlate"
      @close="ctx.visible = false"
      @detail="openDetailFromCtx"
      @follow="toggleFollow(ctx.plate)"
    />

    <VehicleDetailDrawer
      v-model="panelVisible"
      :vehicle-id="panelVehicleId"
      :live-point="panelLivePoint"
      :online="panelOnline"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import dayjs from 'dayjs'
import { getLatestPoints, type GpsPoint } from '@/api/traj'
import { getVehicles, getDeptTree, type Dept, type Vehicle } from '@/api/mdm'
import { reverseGeocode, placeSearch, sendCommand } from '@/api/terminal'
import { useRealtime, type RiskBrief, type AlarmBrief } from '@/composables/useRealtime'
import VehicleDetailDrawer from '@/components/VehicleDetailDrawer.vue'
import VehicleContextMenu from '@/components/VehicleContextMenu.vue'

const router = useRouter()
const route = useRoute()

type VStatus = 'drive' | 'stop' | 'offline' | 'alarm'

interface Vm {
  plate: string
  identityCode: string
  vehicleId: string | null
  deptId: number | null
  deptName: string
  point: GpsPoint | null
}

interface TreeNode {
  key: string
  label: string
  nodeType: 'dept' | 'vehicle'
  count?: number
  plate?: string
  children?: TreeNode[]
}

// ========================= 状态 =========================
const mapRef = ref<HTMLDivElement>()
const mapCardRef = ref<HTMLDivElement>()
const treeRef = ref<any>()

const ledger = ref<Vehicle[]>([])
const points = ref<GpsPoint[]>([])
const deptTree = ref<Dept[]>([])

const keyword = ref('')
const windowMin = ref(10)
const activeTab = ref<'all' | 'drive' | 'stop' | 'offline' | 'alarm'>('all')
const checkedPlates = ref<Set<string>>(new Set())
const followPlate = ref<string | null>(null)
const bottomCollapsed = ref(false)
const addrKeyword = ref('')

// F16 详情抽屉
const panelVisible = ref(false)
const panelVehicleId = ref<string | null>(null)
const panelPlate = ref<string | null>(null)
const panelLivePoint = computed(() =>
  panelPlate.value ? points.value.find((x) => x.plateNo === panelPlate.value) ?? null : null
)
// 抽屉在线状态与页面统一口径（refTime + windowMin），避免数据滞后墙钟时误判离线
const panelOnline = computed(() => {
  const plate = panelPlate.value
  if (!plate) return undefined
  const vm = vmMap.value.get(plate)
  return vm ? statusOf(vm) !== 'offline' : undefined
})

// 右键菜单
const ctx = ref({ visible: false, x: 0, y: 0, plate: '', identityCode: '', vehicleId: null as string | null })

// ========================= 数据视图模型 =========================
const vmMap = computed(() => {
  const map = new Map<string, Vm>()
  for (const v of ledger.value) {
    map.set(v.vehicleNo, {
      plate: v.vehicleNo,
      identityCode: v.terminalIdentity || '',
      vehicleId: v.id != null ? String(v.id) : null,
      deptId: v.deptId ?? null,
      deptName: v.deptName || '未分组',
      point: null
    })
  }
  for (const p of points.value) {
    const ex = map.get(p.plateNo)
    if (ex) {
      ex.point = p
      if (!ex.identityCode) ex.identityCode = p.identityCode
    } else {
      map.set(p.plateNo, {
        plate: p.plateNo,
        identityCode: p.identityCode,
        vehicleId: null,
        deptId: null,
        deptName: '未分组',
        point: p
      })
    }
  }
  return map
})

// 在线判定以"平台数据时基"（全局最新点时间）为参考：
// 真实终端在线时该时基≈墙钟，历史数据集演示时同样能正确区分活跃/离线车辆
const refTime = computed(() => {
  let m = ''
  for (const p of points.value) if (p.gpsTime > m) m = p.gpsTime
  return m
})
const cutoff = computed(() =>
  refTime.value ? dayjs(refTime.value).subtract(windowMin.value, 'minute') : dayjs(0)
)

function rawStatus(vm: Vm | undefined): 'drive' | 'stop' | 'offline' {
  if (!vm?.point) return 'offline'
  if (dayjs(vm.point.gpsTime).isBefore(cutoff.value)) return 'offline'
  return (vm.point.speed ?? 0) > 0 ? 'drive' : 'stop'
}
function statusOf(vm: Vm | undefined): VStatus {
  const s = rawStatus(vm)
  if (s !== 'offline' && vm?.point?.alarmFlag === 1) return 'alarm'
  return s
}
// REST 走 Jackson 配置输出 'yyyy-MM-dd HH:mm:ss'，WebSocket 推送为 ISO 串，统一格式化
function fmtTime(t?: string | null): string {
  if (!t) return '—'
  const d = dayjs(t)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : t
}

function statusText(vm: Vm): string {
  switch (statusOf(vm)) {
    case 'drive': return '在线 · 行驶中'
    case 'stop': return '在线 · 停车'
    case 'alarm': return '在线 · 报警中'
    default: return '离线 · 信号中断'
  }
}

const stats = computed(() => {
  let total = 0, drive = 0, stop = 0, offline = 0, alarm = 0
  for (const vm of vmMap.value.values()) {
    total++
    const s = statusOf(vm)
    if (s === 'drive') drive++
    else if (s === 'stop') stop++
    else if (s === 'alarm') alarm++
    else offline++
  }
  return { total, drive, stop, offline, alarm, online: drive + stop + alarm }
})

// ========================= 组织树 =========================
const UNGROUPED = -1
// 仅首屏自动全选一次；之后用户手动取消勾选不再被强制还原
let treeInitialized = false

// 结构签名：仅当车牌集合 / 部门归属 / 终端绑定变化时才变化。
// 不能直接依赖 vmMap（每秒随实时点重建），否则 el-tree 每秒换 data，
// 会把搜索过滤、展开状态瞬间冲掉。
const rosterSig = computed(() => {
  const inLedger = new Set(ledger.value.map((v) => v.vehicleNo))
  const loose: string[] = []
  for (const p of points.value) {
    if (!inLedger.has(p.plateNo)) loose.push(`${p.plateNo}|${p.identityCode}`)
  }
  const main = ledger.value
    .map((v) => `${v.vehicleNo}@${v.deptId ?? ''}|${v.terminalIdentity ?? ''}`)
    .join(',')
  const deptSig = deptTree.value.map((d) => `${d.id}:${d.parentId}:${d.deptName}`).join(',')
  return `${main}#${[...new Set(loose)].sort().join(',')}#${deptSig}`
})

function collectDeptIds(list: Dept[], acc = new Set<number>()): Set<number> {
  for (const d of list) {
    acc.add(d.id)
    if (d.children) collectDeptIds(d.children, acc)
  }
  return acc
}

function buildTreeData(): TreeNode[] {
  const vehiclesByDept = new Map<number, Vm[]>()
  const loose: Vm[] = []
  const deptIds = collectDeptIds(deptTree.value)
  for (const vm of vmMap.value.values()) {
    if (vm.deptId != null && deptIds.has(vm.deptId)) {
      const arr = vehiclesByDept.get(vm.deptId) || []
      arr.push(vm)
      vehiclesByDept.set(vm.deptId, arr)
    } else {
      loose.push(vm)
    }
  }
  const toVehicleNode = (vm: Vm): TreeNode => ({
    key: 'v' + vm.plate,
    label: vm.plate,
    nodeType: 'vehicle',
    plate: vm.plate
  })
  const walk = (d: Dept): TreeNode => {
    const vms = vehiclesByDept.get(d.id) || []
    const children = [...(d.children || []).map(walk), ...vms.map(toVehicleNode)]
    return {
      key: 'd' + d.id,
      label: d.deptName,
      nodeType: 'dept',
      count: countDeptVehicles(d, vehiclesByDept),
      children
    }
  }
  const roots = deptTree.value.map(walk)
  if (loose.length) {
    roots.push({
      key: 'd' + UNGROUPED,
      label: '未分组车辆',
      nodeType: 'dept',
      count: loose.length,
      children: loose.map(toVehicleNode)
    })
  }
  return roots
}

const treeData = ref<TreeNode[]>([])
watch(rosterSig, () => {
  treeData.value = buildTreeData()
}, { immediate: true })

function countDeptVehicles(d: Dept, map: Map<number, Vm[]>): number {
  let n = map.get(d.id)?.length || 0
  for (const c of d.children || []) n += countDeptVehicles(c, map)
  return n
}

function filterNode(value: string, data: TreeNode) {
  if (!value) return true
  if (data.nodeType === 'vehicle') return data.label.includes(value)
  return true // 部门始终保留，保持树结构完整
}
watch(keyword, (v) => {
  treeRef.value?.filter(v)
  if (v) nextTick(expandAll)
})

function expandAll() {
  const expand = (nodes: TreeNode[]) => {
    for (const n of nodes) {
      const tnode = treeRef.value?.getNode(n.key)
      if (tnode) tnode.expanded = true
      if (n.children) expand(n.children)
    }
  }
  expand(treeData.value)
}

function onTreeCheck() {
  const keys: string[] = treeRef.value.getCheckedKeys(true) // true=仅叶子
  checkedPlates.value = new Set(keys.filter((k: string) => k.startsWith('v')).map((k: string) => k.slice(1)))
}

// 树节点右键：仅车辆叶子
function onTreeContextMenu(ev: MouseEvent, data: TreeNode) {
  if (data.nodeType !== 'vehicle' || !data.plate) return
  ev.preventDefault()
  openCtx(ev, data.plate)
}

// ========================= 底部表格 =========================
const tableRows = computed(() => {
  const rows: Vm[] = []
  for (const vm of vmMap.value.values()) {
    if (!checkedPlates.value.has(vm.plate)) continue
    const s = statusOf(vm)
    if (activeTab.value !== 'all' && s !== activeTab.value) continue
    rows.push(vm)
  }
  // 在线在前，按最新定位时间倒序
  return rows.sort((a, b) => {
    const sa = statusOf(a) === 'offline' ? 1 : 0
    const sb = statusOf(b) === 'offline' ? 1 : 0
    if (sa !== sb) return sa - sb
    return (b.point?.gpsTime || '').localeCompare(a.point?.gpsTime || '')
  })
})

function pickTab(t: typeof activeTab.value) {
  activeTab.value = t
}

function toggleBottom() {
  bottomCollapsed.value = !bottomCollapsed.value
  setTimeout(() => map?.invalidateSize(), 220)
}

// ========================= 地图 =========================
let map: L.Map
let markerLayer: L.LayerGroup
let stdLayer: L.TileLayer
let satLayer: L.TileLayer
let satelliteOn = false
const markerMap = new Map<string, L.Marker>()
const geoCache = new Map<string, string>()
const geoPending = new Set<string>()
const geoFailed = new Set<string>()

/** 为气泡内的地址栏填充逆地理结果；按坐标 3 位小数缓存，失败静默降级 */
function fillAddress(container: HTMLElement | undefined, vm: Vm) {
  if (!container || !vm.point) return
  const span = container.querySelector('.geo-addr') as HTMLElement | null
  if (!span) return
  const p = vm.point
  const key = `${p.lng.toFixed(3)},${p.lat.toFixed(3)}`
  if (geoCache.has(key)) {
    span.textContent = geoCache.get(key)!
    container.querySelector('.geo-hint')?.remove()
    return
  }
  if (geoFailed.has(key)) {
    span.textContent = '经纬度坐标（文字地址服务接入后显示）'
    container.querySelector('.geo-hint')?.remove()
    return
  }
  if (geoPending.has(key)) return
  geoPending.add(key)
  reverseGeocode(p.lng, p.lat)
    .then((r) => {
      if (r?.address) {
        geoCache.set(key, r.address)
        span.textContent = r.address
        container.querySelector('.geo-hint')?.remove()
      }
    })
    .catch(() => {
      // 会话内不再重试同一坐标，避免气泡每秒重绘造成重复请求
      geoFailed.add(key)
      span.textContent = '经纬度坐标（文字地址服务接入后显示）'
      container.querySelector('.geo-hint')?.remove()
    })
    .finally(() => geoPending.delete(key))
}

function makeIcon(vm: Vm): L.DivIcon {
  const s = statusOf(vm)
  const dir = vm.point?.direction ?? 0
  return L.divIcon({
    className: '',
    iconSize: [26, 26],
    iconAnchor: [13, 13],
    html: `<div class="vm-mk vm-${s}" role="button" aria-label="车辆 ${vm.plate}，${statusText(vm)}" data-plate="${vm.plate}"><span style="display:inline-block;transform:rotate(${dir}deg)">▲</span></div>`
  })
}

function popHtml(plate: string): string {
  const vm = vmMap.value.get(plate)
  if (!vm || !vm.point) return `<div class="vm-pop">暂无定位信息</div>`
  const p = vm.point
  const s = statusOf(vm)
  const stateTag =
    s === 'alarm'
      ? '<span class="vm-tag vm-tag-alarm">报警中</span>'
      : `<span class="vm-tag vm-tag-${s}">${statusText(vm)}</span>`
  const cacheKey = `${p.lng.toFixed(3)},${p.lat.toFixed(3)}`
  const addr = geoCache.get(cacheKey)
  return `
  <div class="vm-pop">
    <div class="vm-pop-head">
      <span class="vm-pop-plate">${plate}</span>${stateTag}
    </div>
    <div class="vm-pop-body">
      <div class="vm-row"><span class="vm-k">速度</span><b>${p.speed ?? 0} km/h</b><span class="vm-k" style="margin-left:14px">方向</span><b>${p.direction ?? 0}°</b></div>
      <div class="vm-row"><span class="vm-k">定位时间</span><b>${fmtTime(p.gpsTime)}</b></div>
      <div class="vm-row"><span class="vm-k">坐标</span><b>${Number(p.lng).toFixed(6)}, ${Number(p.lat).toFixed(6)}</b></div>
      <div class="vm-row"><span class="vm-k">位置</span><span class="geo-addr">${addr || '地址解析中…'}</span>${addr ? '' : '<span class="geo-hint">文字地址服务接入后显示</span>'}</div>
    </div>
    <div class="vm-pop-actions">
      <button class="vm-btn primary" data-vact="track" data-plate="${plate}">📍 轨迹回放</button>
      <button class="vm-btn" data-vact="detail" data-plate="${plate}">📋 车辆详情</button>
      <button class="vm-btn soon" data-vact="video" data-plate="${plate}" title="实时视频（预留）">🎥<span class="soon-dot"></span></button>
      <button class="vm-btn soon" data-vact="talk" data-plate="${plate}" title="语音对讲（预留）">🎙️<span class="soon-dot"></span></button>
      <button class="vm-btn soon" data-vact="photo" data-plate="${plate}" title="远程抓拍（预留）">📷<span class="soon-dot"></span></button>
      <button class="vm-btn soon" data-vact="more" data-plate="${plate}" title="更多操作（或右键图标）">···</button>
    </div>
  </div>`
}

function syncMarkers() {
  if (!map) return
  const current = new Set<string>()
  for (const vm of vmMap.value.values()) {
    if (!vm.point) continue
    const visible = checkedPlates.value.size === 0 || checkedPlates.value.has(vm.plate)
    if (!visible) {
      const old = markerMap.get(vm.plate)
      if (old) map.removeLayer(old)
      continue
    }
    current.add(vm.plate)
    const latlng: L.LatLngExpression = [vm.point.lat, vm.point.lng]
    let marker = markerMap.get(vm.plate)
    const newIcon = makeIcon(vm)
    const sig = `${statusOf(vm)}|${vm.point.direction ?? 0}`
    const z = statusOf(vm) === 'alarm' ? 1000 : 0
    if (!marker) {
      marker = L.marker(latlng, { icon: newIcon, zIndexOffset: z })
      ;(marker as any)._plate = vm.plate
      ;(marker as any)._sig = sig
      ;(marker as any)._z = z
      marker.bindPopup(() => popHtml(vm.plate), { closeButton: true, offset: [0, 8], autoPan: false, minWidth: 272 })
      marker.on('contextmenu', (e) => {
        L.DomEvent.preventDefault(e)
        map.closePopup()
        openCtx(e.originalEvent as MouseEvent, vm.plate)
      })
      marker.addTo(markerLayer)
      markerMap.set(vm.plate, marker)
    } else {
      const oldLatLng = marker.getLatLng()
      if (oldLatLng.lat !== vm.point.lat || oldLatLng.lng !== vm.point.lng) marker.setLatLng(latlng)
      // 仅在状态/方向变化时重建图标，避免每秒全量 DOM 抖动
      if ((marker as any)._sig !== sig) {
        marker.setIcon(newIcon)
        ;(marker as any)._sig = sig
      }
      if ((marker as any)._z !== z) {
        marker.setZIndexOffset(z)
        ;(marker as any)._z = z
      }
      if (!map.hasLayer(marker)) marker.addTo(markerLayer)
      // 气泡打开时刷新数据（setContent 会重建 DOM，需重新填充地址）
      if (marker.isPopupOpen()) {
        const popup = marker.getPopup()
        popup?.setContent(popHtml(vm.plate))
        fillAddress(popup?.getElement(), vm)
      }
    }
  }
  // 被移除授权/删除的车辆清理（一般不发生）
  for (const [plate, marker] of markerMap) {
    if (!current.has(plate)) {
      map.removeLayer(marker)
      markerMap.delete(plate)
    }
  }
  // 持续跟踪
  if (followPlate.value) {
    const vm = vmMap.value.get(followPlate.value)
    if (vm?.point) map.panTo([vm.point.lat, vm.point.lng], { animate: false })
  }
}

function focusVehicle(vm: Vm) {
  if (!vm.point) {
    ElMessage.info(`「${vm.plate}」暂无定位点`)
    return
  }
  map.setView([vm.point.lat, vm.point.lng], 13, { animate: true })
  markerMap.get(vm.plate)?.openPopup()
}

function resetView() {
  const latlngs = tableRows.value.filter((v) => v.point).map((v) => [v.point!.lat, v.point!.lng] as [number, number])
  if (latlngs.length === 0) {
    map.setView([33.5, 118.5], 5)
    return
  }
  map.fitBounds(L.latLngBounds(latlngs).pad(0.15))
}

function toggleLayer() {
  if (satelliteOn) {
    map.removeLayer(satLayer)
    stdLayer.addTo(map)
  } else {
    map.removeLayer(stdLayer)
    satLayer.addTo(map)
  }
  satelliteOn = !satelliteOn
}

async function doPlaceSearch() {
  const kw = addrKeyword.value.trim()
  if (!kw) return
  try {
    const list = await placeSearch(kw)
    if (list?.length) {
      const f = list[0]
      map.flyTo([f.lat, f.lng], 13)
      ElMessage.success(`定位到：${f.name}`)
    } else {
      ElMessage.info('未找到匹配地点')
    }
  } catch {
    ElMessage.info('「地点检索」地理编码服务接口已预留，将在下一版本开放')
  }
}

// ========================= 右键菜单 / 详情 =========================
function openCtx(e: MouseEvent, plate: string) {
  const vm = vmMap.value.get(plate)
  ctx.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY,
    plate,
    identityCode: vm?.identityCode || '',
    vehicleId: vm?.vehicleId || null
  }
}

function onRowContextMenu(row: Vm, _col: unknown, ev: MouseEvent) {
  openCtx(ev, row.plate)
}

function openDetailFromCtx() {
  const vm = vmMap.value.get(ctx.value.plate)
  if (!vm?.vehicleId) {
    ElMessage.warning('无权查看该车辆或车辆未建档')
    return
  }
  panelVehicleId.value = vm.vehicleId
  panelPlate.value = vm.plate
  panelVisible.value = true
}

function toggleFollow(plate?: string) {
  const target = plate ?? followPlate.value
  if (!target) return
  if (followPlate.value === target) {
    followPlate.value = null
    ElMessage.success('已取消持续跟踪')
  } else {
    followPlate.value = target
    const vm = vmMap.value.get(target)
    if (vm?.point) map.setView([vm.point.lat, vm.point.lng], 13)
    ElMessage.success(`已开启「${target}」持续跟踪，地图将自动跟随车辆移动`)
  }
}

// ========================= 气泡按钮事件委托 =========================
function onMapCardClick(ev: MouseEvent) {
  const el = (ev.target as HTMLElement).closest('[data-vact]') as HTMLElement | null
  if (!el) return
  const act = el.dataset.vact
  const plate = el.dataset.plate || ''
  const vm = vmMap.value.get(plate)
  if (!vm) return
  switch (act) {
    case 'track':
      handleTrack(plate)
      break
    case 'detail':
      openDetailVm(vm)
      break
    case 'video':
      openCtx(ev, plate)
      break
    case 'talk':
      reservedCommand('语音对讲（JT/T 808 · 0x8400）', vm, 'TAP_MONITOR', { type: 'talk' })
      break
    case 'photo':
      reservedCommand('远程抓拍（JT/T 808 · 0x8801）', vm, 'PHOTO')
      break
    case 'more':
      openCtx(ev, plate)
      break
  }
}

function openDetailVm(vm: Vm) {
  if (!vm.vehicleId) {
    ElMessage.warning('无权查看该车辆或车辆未建档')
    return
  }
  panelVehicleId.value = vm.vehicleId
  panelPlate.value = vm.plate
  panelVisible.value = true
}

function reservedCommand(
  name: string,
  vm: Vm,
  command: string,
  params?: Record<string, unknown>
) {
  if (!vm.identityCode) {
    ElMessage.warning('该车辆未绑定在线终端')
    return
  }
  sendCommand(vm.identityCode, command, params)
    .then(() => ElMessage.success(`「${name}」指令已下发，等待终端应答`))
    .catch(() => ElMessage.info(`「${name}」服务接口已预留，将在下一版本开放`))
}

// 替换上面事件委托中的临时跳转写法
function handleTrack(plate: string) {
  const vm = vmMap.value.get(plate)
  router.push({
    path: '/playback',
    query: { plateNo: plate, identityCode: vm?.identityCode || undefined }
  })
}

// ========================= 实时数据 =========================
/**
 * 后端分页单页上限 100（传 size>100 也只回 100），
 * 监控页需要全量台账构建组织树，故按 total 并发拉满剩余页。
 */
async function fetchAllVehicles(): Promise<Vehicle[]> {
  const size = 100
  const first = await getVehicles({ page: 1, size })
  const all = [...(first.records || [])]
  const total = first.total || 0
  if (all.length < total) {
    const pageCount = Math.ceil(total / size)
    const rest = await Promise.all(
      Array.from({ length: pageCount - 1 }, (_, i) => getVehicles({ page: i + 2, size }))
    )
    for (const p of rest) all.push(...(p.records || []))
  }
  return all
}

function handlePoints(pts: GpsPoint[]) {
  points.value = pts
}
function handleRisk(risks: RiskBrief[]) {
  if (!risks.length) return
  risks.slice(-3).forEach((r) => {
    ElMessage.warning(`新风险事件：${r.plateNo} ${r.eventCode}（等级 ${r.riskLevel}）`)
  })
}
function handleAlarm(alarms: AlarmBrief[]) {
  if (!alarms.length) return
  alarms.slice(-3).forEach((a) => {
    ElMessage.warning(`终端报警：${a.plateNo} 类型 ${a.typeId}`)
  })
}

const { connected } = useRealtime({
  onPoints: handlePoints,
  onRisk: handleRisk,
  onAlarm: handleAlarm
})

async function manualRefresh() {
  try {
    points.value = await getLatestPoints()
    ElMessage.success('已刷新车辆最新位置')
  } catch {
    /* 全局拦截器已提示 */
  }
}

// ========================= 生命周期 =========================
onMounted(async () => {
  // 地图
  map = L.map(mapRef.value!, { zoomControl: false }).setView([33.5, 118.5], 6)
  L.control.zoom({ position: 'bottomright' }).addTo(map)
  stdLayer = L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18, attribution: '&copy; 高德地图' }
  ).addTo(map)
  satLayer = L.tileLayer(
    'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18, attribution: '&copy; 高德地图' }
  )
  markerLayer = L.layerGroup().addTo(map)
  map.on('contextmenu', (e) => L.DomEvent.preventDefault(e))

  // 气泡逆地理（打开时异步解析，失败静默降级）
  map.on('popupopen', (e) => {
    // 当前 Leaflet 版本 Popup 未暴露 getSource()，取内部 _source（bindPopup 的标记）
    const plate = (e.popup as any)._source?._plate || ''
    const vm = plate ? vmMap.value.get(plate) : undefined
    if (!vm?.point) return
    fillAddress(e.popup.getElement(), vm)
  })

  mapCardRef.value?.addEventListener('click', onMapCardClick)

  // 台账 + 组织树（接口已按数据权限裁剪）
  try {
    const [depts, vehicles] = await Promise.all([
      getDeptTree(),
      fetchAllVehicles()
    ])
    deptTree.value = depts || []
    ledger.value = vehicles
    await nextTick()
    // 默认不勾选任何节点；勾选集合为空时地图显示全部车辆（见 syncMarkers）
    treeInitialized = true
  } catch {
    /* 全局拦截器已提示 */
  }

  // 支持 /monitor?plate=xxx 分享链接：选中并定位车辆
  const qPlate = typeof route.query.plate === 'string' ? route.query.plate : ''
  if (qPlate) {
    setTimeout(() => {
      const vm = vmMap.value.get(qPlate)
      if (vm?.point) focusVehicle(vm)
      followPlate.value = null
    }, 1500)
  }
})

// marker/勾选/数据变化时同步
watch([points, checkedPlates, windowMin], () => syncMarkers(), { deep: false })
watch(treeData, () => {
  nextTick(() => {
    // 默认不勾选（空勾选集 = 地图显示全部车辆）；尊重用户手动勾选
    if (!treeInitialized && treeData.value.length) {
      treeInitialized = true
    }
    syncMarkers()
  })
})

onBeforeUnmount(() => {
  mapCardRef.value?.removeEventListener('click', onMapCardClick)
  if (map) map.remove()
})
</script>

<style scoped>
.monitor-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  min-width: 1080px;
}

/* 标题条 */
.m-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.m-title { font-size: 16px; font-weight: 600; color: #1f2937; }
.m-sub { font-size: 12px; color: #9ca3af; }
.m-head-right { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.conn-pill {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; color: #16a34a; background: #f0fdf4;
  border: 1px solid #bbf7d0; border-radius: 999px; padding: 3px 10px;
}
.conn-pill.off { color: #b45309; background: #fffbeb; border-color: #fde68a; }
.conn-dot {
  width: 7px; height: 7px; border-radius: 50%; background: #16a34a; position: relative;
}
.conn-dot.off { background: #f59e0b; }
.conn-dot:not(.off)::after {
  content: ''; position: absolute; inset: -3px; border-radius: 50%;
  border: 2px solid #16a34a; animation: m-pulse 1.8s ease-out infinite;
}
@keyframes m-pulse { 0% { transform: scale(.6); opacity: .7; } 100% { transform: scale(1.6); opacity: 0; } }

/* 工作区 */
.m-workspace { flex: 1; display: flex; gap: 10px; padding: 10px 12px; min-height: 0; }

/* 左栏 */
.m-left {
  width: 272px; flex: 0 0 272px; background: #fff; border: 1px solid #e5e7eb;
  border-radius: 4px; display: flex; flex-direction: column; min-height: 0;
}
.m-search { padding: 10px; border-bottom: 1px solid #f0f0f0; }
.m-tree { flex: 1; overflow-y: auto; padding: 6px 8px; }
.tree-empty { text-align: center; color: #9ca3af; font-size: 12px; padding: 30px 0; }
.tree-row { display: flex; align-items: center; gap: 6px; line-height: 22px; min-width: 0; }
.tree-label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 13px; }
.tree-label.plate { font-weight: 500; color: #1f2937; }
.tree-count { margin-left: auto; font-size: 11px; color: #9ca3af; padding-right: 4px; }
.tree-row.vehicle { cursor: context-menu; }
.tree-alarm {
  font-size: 10px; color: #fff; background: #dc2626; border-radius: 3px;
  padding: 0 4px; line-height: 15px; margin-left: 2px;
}
.v-dot { width: 7px; height: 7px; border-radius: 50%; flex: 0 0 7px; }
.d-drive { background: #2563eb; }
.d-stop { background: #9ca3af; }
.d-offline { background: #d1d5db; }
.d-alarm { background: #dc2626; box-shadow: 0 0 0 3px rgba(220, 38, 60, .18); }

.m-stats {
  flex: 0 0 auto; border-top: 1px solid #f0f0f0; padding: 10px 8px;
  display: grid; grid-template-columns: 1fr 1fr; gap: 4px;
}
.stat-cell {
  display: flex; align-items: center; gap: 7px; padding: 7px 10px; border-radius: 4px;
  cursor: pointer; border: 1px solid transparent;
}
.stat-cell:hover { background: #f8fafc; }
.stat-cell.active { background: #eff6ff; border-color: #bfdbfe; }
.stat-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.s-total { background: #111827; }
.s-online { background: #16a34a; }
.s-drive { background: #2563eb; }
.s-stop { background: #9ca3af; }
.s-off { background: #d1d5db; }
.s-alarm { background: #dc2626; }
.stat-num { font-size: 18px; font-weight: 600; min-width: 34px; color: #1f2937; }
.stat-name { font-size: 12px; color: #6b7280; }
.stat-alarm-blink { color: #dc2626; animation: alarm-blink 1.6s ease-in-out infinite; }
@keyframes alarm-blink { 50% { opacity: .45; } }

/* 右区 */
.m-right { flex: 1; display: flex; flex-direction: column; gap: 10px; min-width: 0; min-height: 0; }
.m-mapcard {
  flex: 1; position: relative; background: #fff; border: 1px solid #e5e7eb;
  border-radius: 4px; overflow: hidden; min-height: 260px;
}
.m-map { position: absolute; inset: 0; }

.map-conn-badge {
  position: absolute; top: 10px; left: 12px; z-index: 600;
  background: rgba(255,255,255,.94); border: 1px solid #e5e7eb; border-radius: 999px;
  padding: 4px 12px; font-size: 12px; color: #6b7280; display: flex; gap: 6px; align-items: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
}
.map-toolbar {
  position: absolute; top: 10px; right: 10px; z-index: 600;
  display: flex; gap: 8px; align-items: center;
}
.addr-box {
  display: flex; align-items: center; background: #fff; border: 1px solid #e5e7eb;
  border-radius: 4px; height: 30px; padding: 0 4px 0 10px; box-shadow: 0 1px 4px rgba(0,0,0,.08);
}
.addr-input { width: 150px; }
.addr-input :deep(.el-input__wrapper) { box-shadow: none !important; padding: 0; }
.addr-go {
  width: 24px; height: 24px; border: none; background: #2563eb; color: #fff;
  border-radius: 3px; cursor: pointer; font-size: 12px;
}
.map-hbtn {
  width: 30px; height: 30px; border: 1px solid #e5e7eb; background: #fff; border-radius: 4px;
  color: #6b7280; cursor: pointer; font-size: 14px; box-shadow: 0 1px 4px rgba(0,0,0,.08);
}
.map-hbtn:hover { color: #2563eb; border-color: #60a5fa; background: #eff6ff; }
.map-legend {
  position: absolute; bottom: 10px; left: 12px; z-index: 600;
  background: rgba(255,255,255,.92); border: 1px solid #e5e7eb; border-radius: 4px;
  padding: 6px 10px; font-size: 11px; color: #6b7280;
  display: flex; gap: 12px; align-items: center; box-shadow: 0 1px 4px rgba(0,0,0,.06);
}
.map-legend i { width: 9px; height: 9px; border-radius: 50%; display: inline-block; margin-right: 3px; }
.legend-tip { color: #9ca3af; }

/* 底部表 */
.m-bottom {
  flex: 0 0 252px; background: #fff; border: 1px solid #e5e7eb; border-radius: 4px;
  display: flex; flex-direction: column; min-height: 0; transition: flex-basis .2s;
}
.m-bottom.collapsed { flex-basis: 42px; }
.bottom-head { display: flex; align-items: center; padding: 0 12px; height: 42px; border-bottom: 1px solid #f0f0f0; flex: 0 0 42px; }
.m-tabs { display: flex; gap: 2px; }
.m-tab {
  padding: 0 12px; height: 42px; line-height: 42px; font-size: 13px; color: #6b7280;
  cursor: pointer; border-bottom: 2px solid transparent;
}
.m-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 500; }
.tab-badge { font-size: 11px; margin-left: 2px; }
.alarm-badge { color: #dc2626; }
.bottom-tools { margin-left: auto; display: flex; gap: 6px; }
.bottom-tools .map-hbtn { box-shadow: none; }
.tbl-wrap { flex: 1; min-height: 0; }
.mini-plate { display: inline-flex; align-items: center; gap: 7px; font-weight: 500; color: #1f2937; }
.state-tag { font-size: 11px; padding: 1px 8px; border-radius: 3px; border: 1px solid; white-space: nowrap; }
.st-drive { color: #2563eb; background: #eff6ff; border-color: #bfdbfe; }
.st-stop { color: #6b7280; background: #f3f4f6; border-color: #e5e7eb; }
.st-alarm { color: #dc2626; background: #fef2f2; border-color: #fecaca; }
.st-offline { color: #9ca3af; background: #f9fafb; border-color: #e5e7eb; }
</style>
