import http from './http'

export interface DictType {
  id?: number
  dictCode: string
  dictName: string
  status?: number
  remark?: string
}

export interface DictItem {
  id?: number
  dictTypeId: number
  itemLabel: string
  itemValue: string
  sort?: number
  status?: number
  cssClass?: string
  remark?: string
}

export interface SysConfig {
  id?: number
  configKey: string
  configValue: string
  configName: string
  valueType: 'STRING' | 'INT' | 'BOOL' | 'JSON'
  isSystem?: number
  remark?: string
}

export interface PageData<T> {
  total: number
  current: number
  size: number
  records: T[]
}

// ---------- 字典 ----------
export const getDictTypes = (params: { page: number; size: number; keyword?: string }) =>
  http.get<PageData<DictType>>('/api/system/dict/types', { params })

export const addDictType = (data: DictType) => http.post('/api/system/dict/types', data)
export const updateDictType = (id: number, data: DictType) => http.put(`/api/system/dict/types/${id}`, data)
export const deleteDictType = (id: number) => http.delete(`/api/system/dict/types/${id}`)

export const getDictItems = (typeId: number) =>
  http.get<DictItem[]>(`/api/system/dict/types/${typeId}/items`)

export const addDictItem = (data: DictItem) => http.post('/api/system/dict/items', data)
export const updateDictItem = (id: number, data: DictItem) => http.put(`/api/system/dict/items/${id}`, data)
export const deleteDictItem = (id: number) => http.delete(`/api/system/dict/items/${id}`)

/** 按字典编码查启用项（对外查询） */
export const getDictItemsByCode = (code: string) =>
  http.get<DictItem[]>(`/api/system/dict/items/${code}`)

/** 全量字典（code -> items） */
export const getAllDict = () =>
  http.get<Record<string, DictItem[]>>('/api/system/dict/all')

// ---------- 参数 ----------
export const getConfigs = (params: { page: number; size: number; keyword?: string }) =>
  http.get<PageData<SysConfig>>('/api/system/config', { params })

export const addConfig = (data: SysConfig) => http.post('/api/system/config', data)
export const updateConfig = (id: number, data: SysConfig) => http.put(`/api/system/config/${id}`, data)
export const deleteConfig = (id: number) => http.delete(`/api/system/config/${id}`)

export const getConfigValue = (key: string, defaultValue?: string) =>
  http.get<string>(`/api/system/config/value/${key}`, { params: { defaultValue } })
