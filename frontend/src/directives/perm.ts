import type { Directive } from 'vue'
import { useAuthStore } from '@/store/auth'

/**
 * v-perm 功能权限指令：无权限直接移除 DOM 元素。
 * 用法：v-perm="'mdm:vehicle:edit'" 或 v-perm="['a:b:edit','x:y:edit']"（AND）
 */
export const vPerm: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuthStore()
    const need = binding.value
    if (!need) return
    const codes = Array.isArray(need) ? need : [need]
    if (codes.length === 0) return
    if (!codes.every((c) => auth.has(c))) {
      el.parentNode?.removeChild(el)
    }
  }
}
