import http from './http'

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface Option {
  id: number
  label: string
}

/** 组织 */
export interface Dept {
  id: number
  parentId: number
  deptName: string
  deptCode?: string | null
  deptType: number
  contactPerson?: string | null
  contactPhone?: string | null
  provinceCode?: string | null
  cityCode?: string | null
  countyCode?: string | null
  address?: string | null
  sortNo: number
  validMark: number
  vehicleCount?: number
  children?: Dept[]
}

export interface DeptSaveRequest {
  parentId?: number | null
  deptName: string
  deptCode?: string
  deptType: number
  contactPerson?: string
  contactPhone?: string
  address?: string
  sortNo?: number
}

/** 车辆 */
export interface Vehicle {
  id: number
  deptId?: number | null
  vehicleNo: string
  vehiclePlateColor: string
  vin?: string | null
  vehicleType?: string | null
  operationType?: number | null
  vehicleIndustry?: string | null
  roadLicenseNo?: string | null
  vehicleColor?: string | null
  vehicleBrand?: string | null
  ownerName?: string | null
  ownerPhone?: string | null
  remark?: string | null
  validMark: number
  deptName?: string | null
  terminalIdentity?: string | null
  mainDriverName?: string | null
}

export interface VehicleSaveRequest {
  deptId?: number | null
  vehicleNo: string
  vehiclePlateColor: string
  vin?: string
  vehicleType?: string
  operationType?: number | null
  vehicleIndustry?: string
  roadLicenseNo?: string
  vehicleColor?: string
  vehicleBrand?: string
  ownerName?: string
  ownerPhone?: string
  remark?: string
}

/** 终端 */
export interface Terminal {
  id: number
  identityCode: string
  tlMac?: string | null
  oemCode?: string | null
  tlModel?: string | null
  simAccount?: string | null
  protocolType?: string | null
  equipmentType?: string | null
  videoChannel?: number | null
  status: number
  remark?: string | null
  validMark: number
  boundVehicleNo?: string | null
  gatewayTruckId?: string | null
  onlineStatus?: number | null
  lastHeartbeatTime?: string | null
}

export interface TerminalSaveRequest {
  identityCode: string
  tlMac?: string
  oemCode?: string
  tlModel?: string
  simAccount?: string
  gatewayTruckId?: string
  protocolType?: string
  equipmentType?: string
  videoChannel?: number | null
  status: number
  remark?: string
}

/** 驾驶员 */
export interface Driver {
  id: number
  driverName: string
  sex: number
  idcard?: string | null
  contactPhone?: string | null
  licenseCode: string
  licenceCategory?: string | null
  driverImg?: string | null
  status: number
  remark?: string | null
  validMark: number
  boundVehicleNo?: string | null
}

export interface DriverSaveRequest {
  driverName: string
  sex: number
  idcard?: string
  contactPhone?: string
  licenseCode: string
  licenceCategory?: string
  driverImg?: string
  status: number
  remark?: string
}

/** 绑定关系 */
export interface VehicleTerminalBind {
  id: number
  vehicleId: number
  terminalId: number
  bindType: number
  bindTime?: string | null
  installTime?: string | null
  installer?: string | null
  unbindTime?: string | null
  status: number
  remark?: string | null
  vehicleNo?: string | null
  terminalIdentity?: string | null
  tlModel?: string | null
  simAccount?: string | null
}

export interface VehicleDriverBind {
  id: number
  vehicleId: number
  driverId: number
  driverType: number
  bindTime?: string | null
  unbindTime?: string | null
  status: number
  remark?: string | null
  vehicleNo?: string | null
  driverName?: string | null
  contactPhone?: string | null
  licenceCategory?: string | null
}

export interface VehicleBinding {
  vehicleId: number
  vehicleNo: string
  currentTerminal: VehicleTerminalBind | null
  activeDrivers: VehicleDriverBind[]
  terminalHistory: VehicleTerminalBind[]
  driverHistory: VehicleDriverBind[]
}

// ===== 组织 =====
export const getDeptTree = (): Promise<Dept[]> => http.get('/api/mdm/depts/tree')
export const getDeptOptions = (): Promise<Option[]> => http.get('/api/mdm/depts/options')
export const createDept = (data: DeptSaveRequest): Promise<number> => http.post('/api/mdm/depts', data)
export const updateDept = (id: number, data: DeptSaveRequest): Promise<void> =>
  http.put(`/api/mdm/depts/${id}`, data)
export const deleteDept = (id: number): Promise<void> => http.delete(`/api/mdm/depts/${id}`)

// ===== 车辆 =====
export const getVehicles = (params: {
  page?: number
  size?: number
  keyword?: string
  deptId?: number
  plateColor?: string
  operationType?: number
}): Promise<PageResult<Vehicle>> => http.get('/api/mdm/vehicles', { params })
export const getVehicleOptions = (): Promise<Option[]> => http.get('/api/mdm/vehicles/options')
export const getVehicle = (id: number): Promise<Vehicle> => http.get(`/api/mdm/vehicles/${id}`)
export const createVehicle = (data: VehicleSaveRequest): Promise<number> =>
  http.post('/api/mdm/vehicles', data)
export const updateVehicle = (id: number, data: VehicleSaveRequest): Promise<void> =>
  http.put(`/api/mdm/vehicles/${id}`, data)
export const deleteVehicle = (id: number): Promise<void> => http.delete(`/api/mdm/vehicles/${id}`)

// ===== 终端 =====
export const getTerminals = (params: {
  page?: number
  size?: number
  keyword?: string
  simAccount?: string
  status?: number
  protocolType?: string
  equipmentType?: string
}): Promise<PageResult<Terminal>> => http.get('/api/mdm/terminals', { params })
export const getTerminalOptions = (): Promise<Option[]> => http.get('/api/mdm/terminals/options')
export const createTerminal = (data: TerminalSaveRequest): Promise<number> =>
  http.post('/api/mdm/terminals', data)
export const updateTerminal = (id: number, data: TerminalSaveRequest): Promise<void> =>
  http.put(`/api/mdm/terminals/${id}`, data)
export const deleteTerminal = (id: number): Promise<void> => http.delete(`/api/mdm/terminals/${id}`)

// ===== 驾驶员 =====
export const getDrivers = (params: {
  page?: number
  size?: number
  keyword?: string
  status?: number
}): Promise<PageResult<Driver>> => http.get('/api/mdm/drivers', { params })
export const getDriverOptions = (): Promise<Option[]> => http.get('/api/mdm/drivers/options')
export const createDriver = (data: DriverSaveRequest): Promise<number> =>
  http.post('/api/mdm/drivers', data)
export const updateDriver = (id: number, data: DriverSaveRequest): Promise<void> =>
  http.put(`/api/mdm/drivers/${id}`, data)
export const deleteDriver = (id: number): Promise<void> => http.delete(`/api/mdm/drivers/${id}`)

// ===== 绑定关系 =====
export const getVehicleBindings = (vehicleId: number): Promise<VehicleBinding> =>
  http.get(`/api/mdm/vehicles/${vehicleId}/bindings`)
export const bindTerminal = (
  vehicleId: number,
  data: { terminalId: number; bindType?: number; installer?: string; remark?: string }
): Promise<void> => http.post(`/api/mdm/vehicles/${vehicleId}/bind-terminal`, data)
export const unbindTerminal = (vehicleId: number, remark?: string): Promise<void> =>
  http.post(`/api/mdm/vehicles/${vehicleId}/unbind-terminal`, { remark })
export const bindDriver = (
  vehicleId: number,
  data: { driverId: number; driverType?: number; remark?: string }
): Promise<void> => http.post(`/api/mdm/vehicles/${vehicleId}/bind-driver`, data)
export const unbindDriver = (vehicleId: number, driverId: number, remark?: string): Promise<void> =>
  http.post(`/api/mdm/vehicles/${vehicleId}/unbind-driver`, { driverId, remark })
