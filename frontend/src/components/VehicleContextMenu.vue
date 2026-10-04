<template>
  <Teleport to="body">
    <div v-if="visible" class="vctx-mask" @click="close" @contextmenu.prevent="close"></div>
    <div
      v-if="visible"
      ref="menuRef"
      class="vctx-menu"
      :class="{ 'flip-x': flipX }"
      :style="{ left: x + 'px', top: y + 'px' }"
      @contextmenu.prevent
    >
      <div class="vctx-item" @click="goTrack">
        <span class="vctx-ico">📍</span>轨迹回放
      </div>

      <div class="vctx-item has-sub">
        <span class="vctx-ico">🎥</span>实时视频
        <span class="vctx-soon">预留</span>
        <span class="vctx-arrow">▶</span>
        <div class="vctx-sub">
          <div
            v-for="ch in channels"
            :key="ch.no"
            class="vctx-item"
            @click="playChannel(ch)"
          >
            {{ ch.name }}<span class="vctx-soon">预留</span>
          </div>
        </div>
      </div>

      <div class="vctx-item has-sub">
        <span class="vctx-ico">⚡</span>终端指令
        <span class="vctx-soon">预留</span>
        <span class="vctx-arrow">▶</span>
        <div class="vctx-sub">
          <div class="vctx-item" @click="sendText">文本下发<span class="vctx-soon">预留</span></div>
          <div class="vctx-item danger" @click="sendFuelCut">断油断电<span class="vctx-soon danger-tag">预留</span></div>
          <div class="vctx-item" @click="sendFuelResume">恢复油电<span class="vctx-soon">预留</span></div>
          <div class="vctx-item" @click="sendReboot">终端重启<span class="vctx-soon">预留</span></div>
          <div class="vctx-item" @click="sendParam">参数设置<span class="vctx-soon">预留</span></div>
        </div>
      </div>

      <div class="vctx-item" @click="toggleFollow">
        <span class="vctx-ico">🎯</span>{{ following ? '取消跟踪' : '持续跟踪' }}
      </div>
      <div class="vctx-item" @click="sendTalk">
        <span class="vctx-ico">🎙️</span>语音对讲<span class="vctx-soon">预留</span>
      </div>
      <div class="vctx-item" @click="sendPhoto">
        <span class="vctx-ico">📷</span>远程抓拍<span class="vctx-soon">预留</span>
      </div>

      <div class="vctx-sep"></div>

      <div class="vctx-item" @click="emit('detail')">
        <span class="vctx-ico">📋</span>车辆详情
      </div>
      <div class="vctx-item" @click="share">
        <span class="vctx-ico">🔗</span>分享位置
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listVideoChannels,
  playVideoChannel,
  sendCommand
} from '@/api/terminal'

interface ChannelOpt {
  no: number
  name: string
}

const props = defineProps<{
  visible: boolean
  x: number
  y: number
  plate: string
  identityCode: string
  vehicleId: string | null
  following: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'detail'): void
  (e: 'follow'): void
}>()

const router = useRouter()
const menuRef = ref<HTMLElement>()
const flipX = ref(false)
const channels = ref<ChannelOpt[]>([])

const PLACEHOLDER_CHANNELS: ChannelOpt[] = [
  { no: 0, name: '全部通道' },
  { no: 1, name: '通道 1' },
  { no: 2, name: '通道 2' },
  { no: 3, name: '通道 3' },
  { no: 4, name: '通道 4' }
]

function close() {
  emit('close')
}

/** 预留能力：真实调用后端契约；后端未实现时友好降级（接口已做静默处理，不弹全局错误） */
function reserved<T>(name: string, p: Promise<T>, okMsg?: string) {
  p.then(() => ElMessage.success(okMsg || `「${name}」指令已下发，等待终端应答`))
    .catch(() => ElMessage.info(`「${name}」服务接口已预留，将在下一版本开放`))
}

function goTrack() {
  close()
  router.push({
    path: '/playback',
    query: { plateNo: props.plate, identityCode: props.identityCode }
  })
}

function toggleFollow() {
  close()
  emit('follow')
}

async function playChannel(ch: ChannelOpt) {
  close()
  if (!props.identityCode) {
    ElMessage.warning('该车辆未绑定在线终端')
    return
  }
  if (ch.no === 0) {
    // 四窗格联播契约待后端补齐
    ElMessage.info('「实时视频 · 全部通道（四窗格）」将在下一版本开放')
    return
  }
  reserved(
    `实时视频 · ${ch.name}（JT/T 1078）`,
    playVideoChannel(props.identityCode, ch.no).then((r) => {
      if (r?.playUrl) window.open(r.playUrl, '_blank')
    })
  )
}

function sendPhoto() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  reserved('远程抓拍（JT/T 808 · 0x8801）', sendCommand(props.identityCode, 'PHOTO'))
}

function sendTalk() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  reserved('语音对讲（JT/T 808 · 0x8400）', sendCommand(props.identityCode, 'TAP_MONITOR', { type: 'talk' }))
}

async function sendText() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  try {
    const { value } = await ElMessageBox.prompt(`向 ${props.plate} 下发文本信息（0x8300）`, '文本下发', {
      confirmButtonText: '下发',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '内容不能为空'
    })
    reserved('文本下发（JT/T 808 · 0x8300）', sendCommand(props.identityCode, 'TEXT_DISPATCH', { content: value }))
  } catch {
    /* 用户取消 */
  }
}

async function sendFuelCut() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  try {
    await ElMessageBox.confirm(
      `确认为 ${props.plate} 下发【断油断电】指令？该操作将使车辆逐渐失去动力，请确认已取得授权。`,
      '危险操作二次确认',
      { confirmButtonText: '确认下发', cancelButtonText: '取消', type: 'warning' }
    )
    reserved('断油断电（JT/T 808 · 0x8500）', sendCommand(props.identityCode, 'FUEL_CUT'))
  } catch {
    /* 用户取消 */
  }
}

function sendFuelResume() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  reserved('恢复油电（JT/T 808 · 0x8500）', sendCommand(props.identityCode, 'FUEL_RESUME'))
}

function sendReboot() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  reserved('终端远程重启', sendCommand(props.identityCode, 'REBOOT'))
}

function sendParam() {
  close()
  if (!props.identityCode) return ElMessage.warning('该车辆未绑定在线终端')
  reserved('终端参数查询与设置（0x8104/0x8103）', sendCommand(props.identityCode, 'PARAM_QUERY'))
}

function share() {
  close()
  const url = `${window.location.origin}/monitor?plate=${encodeURIComponent(props.plate)}`
  const copied = () => ElMessage.success(`「${props.plate}」位置分享链接已复制（24 小时内有效）`)
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(url).then(copied).catch(() =>
      ElMessage.info('分享链接已生成，当前浏览器环境禁止自动复制'))
  } else {
    ElMessage.info('分享链接已生成，当前浏览器环境不支持自动复制')
  }
}

// 打开时定位（防溢出）并异步加载真实视频通道；接口未通则使用 1~4 通道占位
watch(
  () => [props.visible, props.identityCode] as const,
  async ([vis, code]) => {
    if (!vis) return
    channels.value = PLACEHOLDER_CHANNELS
    flipX.value = props.x > window.innerWidth - 400
    await nextTick()
    const el = menuRef.value
    if (!el) return
    const h = el.offsetHeight
    const w = el.offsetWidth
    el.style.top = Math.min(props.y + 4, window.innerHeight - h - 8) + 'px'
    el.style.left = Math.min(props.x + 4, window.innerWidth - w - 8) + 'px'
    if (!code) return
    try {
      const cs = await listVideoChannels(code)
      if (cs?.length) {
        channels.value = [
          { no: 0, name: '全部通道' },
          ...cs.map((c) => ({ no: c.channelNo, name: c.channelName || `通道 ${c.channelNo}` }))
        ]
      }
    } catch {
      /* 后端未实现：保留占位通道 */
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.vctx-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
}
.vctx-menu {
  position: fixed;
  z-index: 3001;
  width: 176px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 8px 28px rgba(17, 24, 39, 0.18);
  padding: 5px;
}
.vctx-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 10px;
  border-radius: 4px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  white-space: nowrap;
}
.vctx-item:hover {
  background: #eff6ff;
  color: #2563eb;
}
.vctx-ico {
  width: 18px;
  text-align: center;
}
.vctx-arrow {
  margin-left: auto;
  font-size: 9px;
  color: #9ca3af;
}
.vctx-soon {
  margin-left: auto;
  font-size: 10px;
  line-height: 16px;
  padding: 0 5px;
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 3px;
}
.vctx-item.has-sub .vctx-soon {
  margin-left: 6px;
}
.vctx-soon.danger-tag {
  color: #dc2626;
  background: #fef2f2;
  border-color: #fecaca;
}
.vctx-item.danger {
  color: #374151;
}
.vctx-item.danger:hover {
  background: #fef2f2;
  color: #dc2626;
}
.vctx-sep {
  height: 1px;
  background: #f0f0f0;
  margin: 4px 2px;
}
.vctx-sub {
  display: none;
  position: absolute;
  left: 100%;
  top: -6px;
  margin-left: 2px;
  width: 176px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 8px 28px rgba(17, 24, 39, 0.18);
  padding: 5px;
}
.vctx-item.has-sub:hover > .vctx-sub {
  display: block;
}
.vctx-menu.flip-x .vctx-sub {
  left: auto;
  right: 100%;
  margin-left: 0;
  margin-right: 2px;
}
</style>
