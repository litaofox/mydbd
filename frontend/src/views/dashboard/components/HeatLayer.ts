import L from 'leaflet'
import type { DashboardHeatCell } from '@/api/monitor'

/**
 * F15 风险热力层（MOD-MON-002 §7.4）：divIcon 聚合圆，零新增依赖。
 * 半径 min(6 + count*2, 40) px、颜色按 maxLevel（红3/橙2/黄1）、opacity 随 count 递增。
 */
const GRID = 0.02

function keyOf(lng: number, lat: number): string {
  return `${Math.round(lng / GRID)}:${Math.round(lat / GRID)}`
}

function colorOf(level: number): string {
  return level >= 3 ? '#dc2626' : level === 2 ? '#f97316' : '#eab308'
}

export class HeatLayer {
  private group: L.LayerGroup
  private cells = new Map<string, { marker: L.Marker; count: number; maxLevel: number }>()

  constructor(map: L.Map) {
    this.group = L.layerGroup().addTo(map)
  }

  /** 30s summary 全量校正 */
  render(list: DashboardHeatCell[]) {
    this.group.clearLayers()
    this.cells.clear()
    for (const c of list) {
      this.add(c.lng, c.lat, c.count, c.maxLevel)
    }
  }

  /** WS RISK 增量：同网格 +1 即时更新 */
  bump(lng: number, lat: number, level: number) {
    const key = keyOf(lng, lat)
    const exist = this.cells.get(key)
    if (exist) {
      exist.count += 1
      exist.maxLevel = Math.max(exist.maxLevel, level)
      this.group.removeLayer(exist.marker)
      exist.marker = this.icon(exist)
      this.group.addLayer(exist.marker)
    } else {
      const cellLng = Math.round(lng / GRID) * GRID
      const cellLat = Math.round(lat / GRID) * GRID
      this.add(cellLng, cellLat, 1, level)
    }
  }

  private add(lng: number, lat: number, count: number, maxLevel: number) {
    const entry = { marker: null as unknown as L.Marker, count, maxLevel }
    entry.marker = this.icon(entry)
    entry.marker.setLatLng([lat, lng])
    this.cells.set(keyOf(lng, lat), entry)
    this.group.addLayer(entry.marker)
  }

  private icon(entry: { count: number; maxLevel: number }): L.Marker {
    const size = Math.min(6 + entry.count * 2, 40)
    const opacity = Math.min(0.35 + entry.count * 0.05, 0.85)
    return L.marker([0, 0], {
      interactive: false,
      icon: L.divIcon({
        className: '',
        iconSize: [size, size],
        iconAnchor: [size / 2, size / 2],
        html: `<div style="width:${size}px;height:${size}px;border-radius:50%;
          background:${colorOf(entry.maxLevel)};opacity:${opacity};
          border:1px solid rgba(255,255,255,.5);"></div>`
      })
    })
  }

  destroy() {
    this.group.remove()
    this.cells.clear()
  }
}
