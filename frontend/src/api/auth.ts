import http from './http'

/** 登录/二步验证明文响应（与后端 LoginVO 对齐） */
export interface LoginResult {
  mfaRequired: boolean
  token?: string
  mfaToken?: string
  userId?: string
  username?: string
  realName?: string | null
}

export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: number // 1 目录 / 2 菜单 / 3 按钮
  permCode?: string | null
  path?: string | null
  icon?: string | null
  sortNo?: number
  visible?: number
  children?: MenuNode[]
}

export interface UserProfile {
  id: string
  username: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  deptName?: string | null
  mfaEnabled: number
  lastLoginTime?: string | null
}

export interface ProfileResult {
  user: UserProfile
  roles: string[]
  perms: string[]
  menus: MenuNode[]
}

export interface MfaSetupInfo {
  secret: string
  otpauthUri: string
}

export function login(username: string, password: string): Promise<LoginResult> {
  return http.post('/api/auth/login', { username, password })
}

export function verifyMfa(mfaToken: string, totpCode: string): Promise<LoginResult> {
  return http.post('/api/auth/mfa/verify', { mfaToken, totpCode })
}

export function logout(): Promise<void> {
  return http.post('/api/auth/logout', {})
}

export function getProfile(): Promise<ProfileResult> {
  return http.get('/api/auth/profile')
}

export function updateProfile(phone: string, email: string): Promise<void> {
  return http.put('/api/auth/profile', { phone, email })
}

export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return http.put('/api/auth/password', { oldPassword, newPassword })
}

export function setupMfa(): Promise<MfaSetupInfo> {
  return http.get('/api/auth/mfa/setup')
}

export function enableMfa(totpCode: string): Promise<void> {
  return http.post('/api/auth/mfa/enable', { totpCode })
}

export function disableMfa(password: string, totpCode: string): Promise<void> {
  return http.post('/api/auth/mfa/disable', { password, totpCode })
}
