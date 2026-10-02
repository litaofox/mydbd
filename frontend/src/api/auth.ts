import http from './http'

export interface LoginResult {
  token: string
  username: string
  role: string
}

export function login(username: string, password: string): Promise<LoginResult> {
  return http.post('/api/auth/login', { username, password })
}
