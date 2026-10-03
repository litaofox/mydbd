import { defineStore } from 'pinia'
import {
  login as loginApi,
  verifyMfa as verifyMfaApi,
  logout as logoutApi,
  getProfile,
  type LoginResult,
  type MenuNode,
  type UserProfile
} from '@/api/auth'

interface AuthState {
  token: string
  userId: string | null
  username: string
  realName: string
  roles: string[]
  perms: string[]
  menus: MenuNode[]
  profile: UserProfile | null
  loaded: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('mydbd-token') || '',
    userId: null,
    username: '',
    realName: '',
    roles: [],
    perms: [],
    menus: [],
    profile: null,
    loaded: false
  }),

  getters: {
    isLogin: (state) => !!state.token,
    /** 第一个可访问的叶子菜单路径，用于登录后落地页 */
    firstMenuPath(state): string {
      const walk = (nodes: MenuNode[]): MenuNode | null => {
        for (const n of nodes) {
          if (n.menuType === 2 && n.path) return n
          if (n.children?.length) {
            const hit = walk(n.children)
            if (hit) return hit
          }
        }
        return null
      }
      return walk(state.menus)?.path || '/monitor'
    }
  },

  actions: {
    /** 密码登录：返回明文结果，由页面决定进入 MFA 二步还是落地 */
    async login(username: string, password: string): Promise<LoginResult> {
      const result = await loginApi(username, password)
      if (!result.mfaRequired && result.token) {
        this.acceptToken(result)
        await this.loadProfile()
      }
      return result
    },

    /** MFA 二步验证 */
    async verifyMfa(mfaToken: string, totpCode: string) {
      const result = await verifyMfaApi(mfaToken, totpCode)
      if (result.mfaRequired || !result.token) {
        throw new Error('动态口令校验未通过')
      }
      this.acceptToken(result)
      await this.loadProfile()
    },

    acceptToken(result: LoginResult) {
      this.token = result.token || ''
      this.userId = result.userId ? String(result.userId) : null
      this.username = result.username || ''
      this.realName = result.realName || ''
      localStorage.setItem('mydbd-token', this.token)
    },

    async loadProfile(force = false) {
      if (this.loaded && !force) return
      const profile = await getProfile()
      this.profile = profile.user
      this.userId = profile.user.id
      this.username = profile.user.username
      this.realName = profile.user.realName
      this.roles = profile.roles || []
      this.perms = profile.perms || []
      this.menus = profile.menus || []
      this.loaded = true
    },

    /** 功能权限判定 */
    has(code: string): boolean {
      if (this.roles.includes('SUPER_ADMIN')) return true
      return this.perms.includes(code)
    },

    async logout() {
      try {
        if (this.token) {
          await logoutApi()
        }
      } catch {
        // 即使留痕接口失败也要清理本地会话
      }
      this.reset()
    },

    /** 401 会话失效时本地清理 */
    reset() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.realName = ''
      this.roles = []
      this.perms = []
      this.menus = []
      this.profile = null
      this.loaded = false
      localStorage.removeItem('mydbd-token')
    }
  }
})
