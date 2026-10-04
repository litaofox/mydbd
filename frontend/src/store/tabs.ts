import { defineStore } from 'pinia'

export interface TabItem {
  /** 唯一键：一功能一标签，取 route.path（不含 query） */
  path: string
  title: string
  /** 最近一次访问的完整地址（含 query），切换标签时按它跳转 */
  fullPath: string
  /** 固定标签不可关闭（/monitor） */
  affix?: boolean
}

const STORAGE_KEY = 'mydbd-tabs'

interface TabsState {
  tabs: TabItem[]
  /** 刷新当前标签的序号：自增后 viewKey 变化触发 router-view 重建 */
  refreshSeq: number
}

function load(): TabItem[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return []
    return arr.filter((t: TabItem) => t && typeof t.path === 'string' && t.path.startsWith('/'))
  } catch {
    return []
  }
}

function save(tabs: TabItem[]) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs))
  } catch {
    // 存储失败不影响功能（仅 F5 后不恢复）
  }
}

export const useTabsStore = defineStore('tabs', {
  state: (): TabsState => ({
    tabs: load(),
    refreshSeq: 0
  }),

  actions: {
    /** 确保固定标签（/monitor）存在且置首，历史数据中的同名普通标签会被合并 */
    ensureAffix(path: string, title: string) {
      const restored = this.tabs.filter((t) => t.path !== path || t.affix)
      if (!restored.some((t) => t.affix)) {
        restored.unshift({ path, title, fullPath: path, affix: true })
        this.tabs = restored
        save(this.tabs)
      } else {
        this.tabs = restored
      }
    },

    /** 路由进入子页时登记：已存在则只更新 fullPath/标题，否则新建标签 */
    visit(path: string, fullPath: string, title: string) {
      const exist = this.tabs.find((t) => t.path === path)
      if (exist) {
        exist.fullPath = fullPath
        if (title) exist.title = title
      } else {
        this.tabs.push({ path, fullPath, title: title || path })
      }
      save(this.tabs)
    },

    /**
     * 关闭标签。返回关闭当前激活标签时应跳转的相邻标签 fullPath；
     * 关闭的是非激活标签、或固定标签时返回 null。
     */
    remove(path: string, activePath: string): string | null {
      const idx = this.tabs.findIndex((t) => t.path === path)
      if (idx < 0 || this.tabs[idx].affix) return null
      this.tabs.splice(idx, 1)
      save(this.tabs)
      if (path !== activePath) return null
      const next = this.tabs[Math.min(idx, this.tabs.length - 1)]
      return next ? next.fullPath : null
    },

    /** 关闭其他（保留右键目标与固定标签），返回应停留的 fullPath */
    closeOthers(path: string): string {
      const keep = this.tabs.find((t) => t.path === path)
      this.tabs = keep ? this.tabs.filter((t) => t.path === path || t.affix) : this.tabs.filter((t) => t.affix)
      save(this.tabs)
      return keep ? keep.fullPath : (this.tabs[0]?.fullPath ?? '/monitor')
    },

    /** 关闭全部（仅留固定标签），返回应跳转的 fullPath */
    closeAll(): string {
      const affix = this.tabs.find((t) => t.affix)
      this.tabs = affix ? [affix] : []
      save(this.tabs)
      return affix ? affix.fullPath : '/monitor'
    },

    /** 刷新当前标签：viewKey 变化触发当前页组件重建 */
    refresh() {
      this.refreshSeq++
    },

    /** 退出登录时清空会话标签 */
    reset() {
      this.tabs = []
      this.refreshSeq = 0
      sessionStorage.removeItem(STORAGE_KEY)
    }
  }
})
