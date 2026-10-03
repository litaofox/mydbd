import http from './http'
import type { PageResult } from './mdm'
import type { MenuNode } from './auth'

// ===== 用户 =====

export interface UserListRow {
  id: string
  username: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  deptName?: string | null
  status: number
  mfaEnabled: number
  failCount: number
  lockedUntil?: string | null
  lastLoginTime?: string | null
  remark?: string | null
  createDate?: string | null
  roleNames?: string | null
  locked?: boolean | null
}

export interface UserDetail {
  id: string
  username: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  deptName?: string | null
  status: number
  mfaEnabled: number
  remark?: string | null
  pwdUpdateTime?: string | null
  lastLoginTime?: string | null
  lastLoginIp?: string | null
  createDate?: string | null
  roleIds: string[]
  roleCodes: string[]
}

export interface UserSaveBody {
  username?: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  status: number
  remark?: string | null
  password?: string
}

export interface UserQuery {
  page: number
  size: number
  username?: string
  realName?: string
  status?: number | null
  deptId?: number | null
  roleId?: number | null
}

export function pageUsers(query: UserQuery): Promise<PageResult<UserListRow>> {
  return http.get('/api/iam/users', { params: query })
}

export function getUser(id: string): Promise<UserDetail> {
  return http.get(`/api/iam/users/${id}`)
}

export function createUser(body: UserSaveBody): Promise<string> {
  return http.post('/api/iam/users', body)
}

export function updateUser(id: string, body: UserSaveBody): Promise<void> {
  return http.put(`/api/iam/users/${id}`, body)
}

export function changeUserStatus(id: string, status: number): Promise<void> {
  return http.put(`/api/iam/users/${id}/status`, null, { params: { status } })
}

export function resetUserPassword(id: string, newPassword: string): Promise<void> {
  return http.put(`/api/iam/users/${id}/reset-password`, { newPassword })
}

export function assignUserRoles(id: string, roleIds: string[]): Promise<void> {
  return http.put(`/api/iam/users/${id}/roles`, { roleIds })
}

export function unlockUser(id: string): Promise<void> {
  return http.put(`/api/iam/users/${id}/unlock`)
}

export function adminDisableUserMfa(id: string): Promise<void> {
  return http.put(`/api/iam/users/${id}/mfa/disable`)
}

export function deleteUser(id: string): Promise<void> {
  return http.delete(`/api/iam/users/${id}`)
}

// ===== 角色 =====

export interface RoleListRow {
  id: string
  roleCode: string
  roleName: string
  dataScope: number
  builtIn: number
  status: number
  remark?: string | null
  createDate?: string | null
  userCount: number
}

export interface RoleDetail {
  id: string
  roleCode: string
  roleName: string
  dataScope: number
  builtIn: number
  status: number
  remark?: string | null
  menuIds: number[]
  deptIds: number[]
}

export interface RoleOption {
  id: string
  roleCode: string
  roleName: string
  dataScope: number
}

export interface RoleSaveBody {
  roleCode?: string
  roleName: string
  dataScope: number
  status: number
  remark?: string | null
  menuIds?: number[]
  deptIds?: number[]
}

export function pageRoles(
  page: number,
  size: number,
  keyword?: string,
  status?: number | null
): Promise<PageResult<RoleListRow>> {
  return http.get('/api/iam/roles', { params: { page, size, keyword, status } })
}

export function listAllRoles(): Promise<RoleOption[]> {
  return http.get('/api/iam/roles/all')
}

export function getRole(id: string): Promise<RoleDetail> {
  return http.get(`/api/iam/roles/${id}`)
}

export function createRole(body: RoleSaveBody): Promise<string> {
  return http.post('/api/iam/roles', body)
}

export function updateRole(id: string, body: RoleSaveBody): Promise<void> {
  return http.put(`/api/iam/roles/${id}`, body)
}

export function deleteRole(id: string): Promise<void> {
  return http.delete(`/api/iam/roles/${id}`)
}

// ===== 菜单树（角色授权用） =====

export function getMenuTree(): Promise<MenuNode[]> {
  return http.get('/api/iam/menus/tree')
}
