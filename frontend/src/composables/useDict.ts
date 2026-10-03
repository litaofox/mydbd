import { ref, computed } from 'vue'
import { getDictItemsByCode, type DictItem } from '@/api/system'

const dictCache = new Map<string, DictItem[]>()

/**
 * 按字典编码获取字典项，带前端缓存。
 * 返回 items 列表与 toMap( value->label ) 映射。
 */
export function useDict(code: string) {
  const items = ref<DictItem[]>(dictCache.get(code) || [])

  if (!dictCache.has(code)) {
    getDictItemsByCode(code)
      .then((list) => {
        dictCache.set(code, list)
        items.value = list
      })
      .catch(() => {
        // 失败时移除占位，允许下次重试
        dictCache.delete(code)
      })
  }

  const toMap = computed(() =>
    Object.fromEntries(items.value.map((i) => [i.itemValue, i.itemLabel]))
  )

  return { items, toMap }
}
