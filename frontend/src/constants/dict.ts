/**
 * 主数据枚举字典（F35 字典中心上线后迁移到字典表）
 */

export interface DictItem {
  value: number | string
  label: string
  tag?: 'success' | 'warning' | 'danger' | 'info' | 'primary'
}

/** 车牌颜色 */
export const PLATE_COLORS = ['蓝色', '黄色', '绿色', '白色', '黑色']

/** 运营类型 */
export const OPERATION_TYPES: DictItem[] = [
  { value: 1, label: '货运' },
  { value: 2, label: '客运' },
  { value: 3, label: '危化品', tag: 'danger' },
  { value: 9, label: '其他', tag: 'info' }
]

/** 组织类型 */
export const DEPT_TYPES: DictItem[] = [
  { value: 1, label: '运输企业' },
  { value: 2, label: '车队/部门' }
]

/** 终端通信协议 */
export const PROTOCOL_TYPES = ['JT808', 'JT1078', 'OTHER']

/** 终端设备类型 */
export const EQUIPMENT_TYPES: DictItem[] = [
  { value: '1', label: '一体机' },
  { value: '2', label: '分体机' },
  { value: '4', label: '视频智能终端' }
]

/** 终端状态 */
export const TERMINAL_STATUS: DictItem[] = [
  { value: 1, label: '正常', tag: 'success' },
  { value: 2, label: '维修停用', tag: 'warning' },
  { value: 3, label: '报废', tag: 'info' }
]

/** 司机性别 */
export const DRIVER_SEX: DictItem[] = [
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]

/** 司机在职状态 */
export const DRIVER_STATUS: DictItem[] = [
  { value: 1, label: '在岗', tag: 'success' },
  { value: 2, label: '离岗', tag: 'warning' },
  { value: 3, label: '停用', tag: 'info' }
]

/** 绑定类型 */
export const BIND_TYPES: DictItem[] = [
  { value: 1, label: '正式安装' },
  { value: 2, label: '临时换装' }
]

/** 司机班别 */
export const DRIVER_TYPES: DictItem[] = [
  { value: 1, label: '主班', tag: 'danger' },
  { value: 2, label: '副班', tag: 'primary' }
]

/** 审计模块（F34） */
export const AUDIT_MODULES: DictItem[] = [
  { value: 'AUTH', label: '登录认证' },
  { value: 'MDM', label: '主数据' },
  { value: 'MONITOR', label: '实时监控' },
  { value: 'CEP', label: '风控规则' },
  { value: 'WORK_ORDER', label: '处置工单' },
  { value: 'VIDEO', label: '视频' },
  { value: 'AUDIT', label: '审计' },
  { value: 'IAM', label: '用户权限' },
  { value: 'CONFIG', label: '系统配置' }
]

/** 审计动作（F34） */
export const AUDIT_ACTIONS: DictItem[] = [
  { value: 'LOGIN', label: '登录成功', tag: 'success' },
  { value: 'LOGIN_FAIL', label: '登录失败', tag: 'danger' },
  { value: 'LOGOUT', label: '退出登录', tag: 'info' },
  { value: 'CREATE', label: '新增', tag: 'primary' },
  { value: 'UPDATE', label: '修改', tag: 'warning' },
  { value: 'DELETE', label: '删除', tag: 'danger' },
  { value: 'EXPORT', label: '数据导出', tag: 'warning' },
  { value: 'VIDEO_VIEW', label: '视频调阅', tag: 'warning' },
  { value: 'HANDLE', label: '业务处置', tag: 'primary' },
  { value: 'QUERY', label: '敏感查询', tag: 'info' }
]

/** 用户状态（F33 IAM） */
export const USER_STATUS: DictItem[] = [
  { value: 1, label: '正常', tag: 'success' },
  { value: 0, label: '停用', tag: 'info' }
]

/** 动态口令状态（F33 IAM） */
export const MFA_STATUS: DictItem[] = [
  { value: 1, label: '已开启', tag: 'warning' },
  { value: 0, label: '未开启', tag: 'info' }
]

/** 角色数据范围（F33 IAM） */
export const DATA_SCOPES: DictItem[] = [
  { value: 1, label: '全部数据', tag: 'danger' },
  { value: 2, label: '本企业及以下', tag: 'warning' },
  { value: 3, label: '本部门及以下', tag: 'primary' },
  { value: 4, label: '仅本部门', tag: 'info' },
  { value: 5, label: '自定义部门', tag: 'success' }
]

/** 审计结果（F34） */
export const AUDIT_STATUS: DictItem[] = [
  { value: 1, label: '成功', tag: 'success' },
  { value: 0, label: '失败', tag: 'danger' }
]

export function labelOf(list: DictItem[], value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '--'
  return list.find((i) => i.value === value)?.label ?? String(value)
}

export function tagOf(list: DictItem[], value: number | string | null | undefined) {
  return list.find((i) => i.value === value)?.tag ?? 'info'
}
