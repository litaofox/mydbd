<template>
  <div class="auth-image">
    <el-image
      v-if="src"
      :src="src"
      :preview-src-list="[src]"
      preview-teleported
      fit="cover"
      class="thumb"
      :title="title"
    />
    <div v-else-if="failed" class="placeholder">加载失败</div>
    <div v-else class="placeholder" v-loading="true" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchMediaObjectUrl } from '@/composables/useAuthMedia'

const props = defineProps<{ url: string; title?: string }>()

const src = ref('')
const failed = ref(false)

async function load() {
  revoke()
  src.value = ''
  failed.value = false
  try {
    src.value = await fetchMediaObjectUrl(props.url)
  } catch {
    failed.value = true
  }
}

function revoke() {
  if (src.value) {
    URL.revokeObjectURL(src.value)
    src.value = ''
  }
}

watch(() => props.url, load)
onMounted(load)
onBeforeUnmount(revoke)
</script>

<style scoped>
.auth-image {
  width: 96px;
  height: 96px;
}
.thumb {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px solid #e3e8f0;
  cursor: zoom-in;
}
.placeholder {
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: 1px dashed #e3e8f0;
  color: #9ca3af;
  font-size: 12px;
}
</style>
