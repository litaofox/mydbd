import { onMounted, onBeforeUnmount } from 'vue'

/**
 * F15 1920/4K rem 流式自适应（MOD-MON-002 §7.3）：
 * 挂载与 resize（rAF 节流）时设置根字号 = clamp(12, clientWidth/1920*16, 32)px；
 * 卸载时恢复 16px，避免污染常规页面。
 */
export function useAdaptive(onResize?: () => void) {
  let raf = 0

  function apply() {
    const px = Math.min(32, Math.max(12, (document.documentElement.clientWidth / 1920) * 16))
    document.documentElement.style.fontSize = px + 'px'
    onResize?.()
  }

  function throttled() {
    if (raf) return
    raf = requestAnimationFrame(() => {
      raf = 0
      apply()
    })
  }

  onMounted(() => {
    apply()
    window.addEventListener('resize', throttled)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', throttled)
    if (raf) cancelAnimationFrame(raf)
    document.documentElement.style.fontSize = ''
  })
}
