/**
 * 车辆类型地图图标配置（F-监控图标）
 *
 * - traj_vehicle.vehicle_type 存中文类型名（冷链车/牵引车/轻型货车/中型货车/重型货车）
 * - 图标为俯视视角、车头朝上，由监控页按定位方向角整体旋转
 * - 形状区分车型；颜色默认取各类型主色，监控页会用车牌颜色覆盖
 *   （蓝牌=蓝、黄牌=黄，见 plateColorHex），状态（报警）由红色脉冲光晕表达
 *
 * 扩展方式（预留接口）：
 * 1. 静态扩展：在 ICONS 中加一条配置；
 * 2. 运行时扩展：调用 registerVehicleIcon({ type, color, inner })。
 * 未登记的类型自动回退 DEFAULT（灰色通用车辆）。
 */
export interface VehicleIconConfig {
  /** 车辆类型名（与 traj_vehicle.vehicle_type 一致） */
  type: string
  /** 类型默认主色（hex），车牌颜色未知时兜底 */
  color: string
  /** viewBox 0 0 24 24 的 SVG 内层片段（车头朝上，主色用 currentColor） */
  inner: string
}

/** 货厢/挡风玻璃高光 */
const GLASS = 'rgba(255,255,255,.55)'
const LINE = 'rgba(255,255,255,.35)'

const DEFAULT: VehicleIconConfig = {
  type: '__default__',
  color: '#6b7280',
  inner: `
    <rect class="bd" x="8" y="3.5" width="8" height="5" rx="2"/>
    <rect x="8.8" y="4.6" width="6.4" height="1.5" rx="0.75" fill="${GLASS}"/>
    <rect class="bd" x="8" y="9.5" width="8" height="10" rx="1.5"/>`
}

const ICONS: Record<string, VehicleIconConfig> = {
  重型货车: {
    type: '重型货车',
    color: '#1d4ed8',
    inner: `
    <rect class="bd" x="6" y="3" width="12" height="6" rx="1.5"/>
    <rect x="7.2" y="4.4" width="9.6" height="1.6" rx="0.8" fill="${GLASS}"/>
    <rect class="bd" x="5" y="10" width="14" height="11" rx="1"/>
    <path d="M6.5 13.5h11M6.5 17.5h11" stroke="${LINE}" stroke-width="1"/>`
  },
  中型货车: {
    type: '中型货车',
    color: '#ea580c',
    inner: `
    <rect class="bd" x="7" y="3" width="10" height="6" rx="1.5"/>
    <rect x="8" y="4.4" width="8" height="1.6" rx="0.8" fill="${GLASS}"/>
    <rect class="bd" x="6.5" y="10" width="11" height="10" rx="1"/>`
  },
  轻型货车: {
    type: '轻型货车',
    color: '#16a34a',
    inner: `
    <rect class="bd" x="7.5" y="4" width="9" height="5" rx="1.5"/>
    <rect x="8.5" y="5.2" width="7" height="1.5" rx="0.75" fill="${GLASS}"/>
    <rect class="bd" x="7.5" y="10" width="9" height="8" rx="1"/>`
  },
  牵引车: {
    type: '牵引车',
    color: '#7c3aed',
    inner: `
    <rect class="bd" x="7" y="2.5" width="10" height="5.5" rx="1.5"/>
    <rect x="8" y="3.8" width="8" height="1.5" rx="0.75" fill="${GLASS}"/>
    <rect class="bd" x="11.2" y="8" width="1.6" height="2.2" opacity=".7"/>
    <rect class="bd" x="6" y="10.5" width="12" height="11" rx="1" opacity=".82"/>
    <path d="M8.5 14.5h7" stroke="${LINE}" stroke-width="1"/>`
  },
  冷链车: {
    type: '冷链车',
    color: '#0891b2',
    inner: `
    <rect class="bd" x="7" y="3" width="10" height="6" rx="1.5"/>
    <rect x="8" y="4.4" width="8" height="1.6" rx="0.8" fill="${GLASS}"/>
    <rect class="bd" x="6.5" y="10" width="11" height="10.5" rx="1"/>
    <path d="M12 11.4v7.2M9.2 13.1l5.6 3.4M14.8 13.1l-5.6 3.4" stroke="#fff" stroke-width="1.1" stroke-linecap="round"/>`
  }
}

/** 车辆图标统一主色（当前版本全量绿色） */
export const VEHICLE_ICON_COLOR = '#16a34a'

/** 车牌颜色 → 图标色（备用映射；当前版本图标统一绿色，未启用） */
const PLATE_COLOR_HEX: Record<string, string> = {
  蓝色: '#2563eb',
  黄色: '#f59e0b',
  绿色: '#16a34a',
  黑色: '#1f2937',
  白色: '#94a3b8'
}

/** 车牌颜色映射图标色，未登记颜色返回 null（回退类型主色） */
export function plateColorHex(color?: string | null): string | null {
  if (!color) return null
  return PLATE_COLOR_HEX[color] ?? null
}

/** 登记新类型的图标（管理功能预留接口） */
export function registerVehicleIcon(cfg: VehicleIconConfig) {
  ICONS[cfg.type] = cfg
}

/** 取类型图标，未登记类型回退通用图标 */
export function getVehicleIcon(type?: string | null): VehicleIconConfig {
  return (type && ICONS[type]) || DEFAULT
}

/** 已登记的图标配置列表（管理功能预留接口） */
export function listVehicleIcons(): VehicleIconConfig[] {
  return Object.values(ICONS)
}

/** 生成内联 SVG（监控 divIcon / 其他地图场景复用）；colorOverride 覆盖颜色（如车牌色） */
export function vehicleIconSvg(type?: string | null, size = 20, colorOverride?: string | null): string {
  const ic = getVehicleIcon(type)
  const color = colorOverride || ic.color
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" style="color:${color};fill:currentColor" aria-hidden="true">${ic.inner}</svg>`
}
