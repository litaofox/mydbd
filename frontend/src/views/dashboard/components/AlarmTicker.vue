<template>
  <div class="ticker">
    <div class="tk-head">
      <span class="tk-title">最新未处理报警</span>
      <span class="tk-count">{{ items.length }}</span>
    </div>
    <div v-if="items.length === 0" class="tk-empty">暂无未处理报警</div>
    <ul v-else class="tk-list">
      <li v-for="a in items" :key="a.id" class="tk-item" @click="$emit('pick', a)">
        <span class="tk-plate">{{ a.plateNo }}</span>
        <span class="tk-type" :class="gradeClass(a.gradeLevel)">{{ a.typeName || '类型 ' + a.typeId }}</span>
        <span class="tk-time">{{ fmt(a.startWarnTime) }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { AlarmVO } from '@/api/alarm'

defineProps<{ items: AlarmVO[] }>()
defineEmits<{ (e: 'pick', alarm: AlarmVO): void }>()

function fmt(v: string | null): string {
  if (!v) return '--'
  return v.replace('T', ' ').slice(11, 19)
}

function gradeClass(level: number | null): string {
  return level === 3 ? 'g3' : level === 2 ? 'g2' : 'g1'
}
</script>

<style scoped>
.ticker {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}
.tk-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: #cfe0ff;
  padding-bottom: 0.4rem;
}
.tk-count {
  background: #b91c1c;
  color: #fff;
  border-radius: 0.7rem;
  font-size: 0.75rem;
  padding: 0 0.45rem;
  min-width: 1.3rem;
  text-align: center;
}
.tk-empty {
  color: #64748b;
  font-size: 0.85rem;
  padding: 1rem 0;
  text-align: center;
}
.tk-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}
.tk-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.4rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
  font-size: 0.82rem;
  cursor: pointer;
}
.tk-item:hover {
  background: rgba(59, 130, 246, 0.12);
}
.tk-plate {
  color: #e2e8f0;
  font-weight: 600;
  flex-shrink: 0;
}
.tk-type {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tk-type.g3 { color: #f87171; }
.tk-type.g2 { color: #fb923c; }
.tk-type.g1 { color: #facc15; }
.tk-time {
  color: #94a3b8;
  flex-shrink: 0;
}
</style>
