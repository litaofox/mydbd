<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">mydbd</div>
      <div class="subtitle">北斗导航数据业务平台</div>

      <el-form :model="form" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onLogin">
          登 录
        </el-button>
      </el-form>

      <div class="tip">演示账号：admin / admin123</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function onLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/monitor'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a8a 0%, #0f172a 100%);
}

.login-card {
  width: 380px;
  padding: 24px 16px 8px;
  border-radius: 8px;
}

.brand {
  font-size: 30px;
  font-weight: 700;
  color: #1d4ed8;
  text-align: center;
  letter-spacing: 2px;
}

.subtitle {
  text-align: center;
  color: #6b7280;
  margin: 6px 0 24px;
  font-size: 14px;
}

.tip {
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  margin-top: 14px;
}
</style>
