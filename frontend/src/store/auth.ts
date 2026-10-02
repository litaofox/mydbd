import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'

interface AuthState {
  token: string
  username: string
  role: string
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('mydbd-token') || '',
    username: localStorage.getItem('mydbd-username') || '',
    role: localStorage.getItem('mydbd-role') || ''
  }),

  getters: {
    isLogin: (state) => !!state.token
  },

  actions: {
    async login(username: string, password: string) {
      const result = await loginApi(username, password)
      this.token = result.token
      this.username = result.username
      this.role = result.role
      localStorage.setItem('mydbd-token', result.token)
      localStorage.setItem('mydbd-username', result.username)
      localStorage.setItem('mydbd-role', result.role)
    },

    logout() {
      this.token = ''
      this.username = ''
      this.role = ''
      localStorage.removeItem('mydbd-token')
      localStorage.removeItem('mydbd-username')
      localStorage.removeItem('mydbd-role')
    }
  }
})
