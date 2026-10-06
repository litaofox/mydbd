import http from '@/api/http'

/**
 * 带 JWT 的媒体文件加载（GATEWAY-PLAN-001）
 * <img> 标签无法携带 Authorization 头，统一走 http(blob) + objectURL。
 * http 响应拦截器对 Blob 响应原样返回（无 code 包装）。
 */

/** 报警附件媒体流地址 */
export function alarmMediaUrl(id: number | string): string {
  return `/api/monitor/alarm/media/${id}`
}

/** 拉取媒体 Blob 并生成 objectURL（调用方负责 URL.revokeObjectURL 释放） */
export async function fetchMediaObjectUrl(url: string): Promise<string> {
  const blob = await http.get<Blob>(url, { responseType: 'blob' })
  return URL.createObjectURL(blob as unknown as Blob)
}

/** 以 blob 方式下载媒体文件（保留鉴权） */
export async function downloadMediaFile(id: number | string, fileName?: string | null): Promise<void> {
  const url = await fetchMediaObjectUrl(alarmMediaUrl(id))
  const a = document.createElement('a')
  a.href = url
  a.download = fileName || `alarm-media-${id}`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
