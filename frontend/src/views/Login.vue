<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">mydbd</div>
      <div class="subtitle">北斗导航数据业务平台</div>

      <!-- 第一步：账号密码 -->
      <el-form v-if="step === 1" :model="form" @keyup.enter="onLogin">
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

      <!-- 第二步：动态口令 -->
      <div v-else>
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
          <template #title>账号「{{ form.username }}」已开启动态口令</template>
          请输入身份验证器中 6 位数字验证码
        </el-alert>
        <el-form @keyup.enter="onVerify">
          <el-form-item>
            <el-input
              v-model="totpCode"
              placeholder="6 位动态验证码"
              size="large"
              maxlength="6"
              :prefix-icon="Key"
              style="letter-spacing: 6px; text-align: center"
            />
          </el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onVerify">
            验 证
          </el-button>
          <el-button link style="width: 100%; margin-top: 8px" @click="backToLogin">返回上一步</el-button>
        </el-form>
      </div>

      <!-- 测试账号快捷填充 -->
      <div v-if="step === 1" class="quick">
        <div class="quick-head">测试账号（点击自动填充）</div>
        <div v-for="g in testAccounts" :key="g.group" class="quick-group">
          <span class="quick-label">{{ g.group }}</span>
          <button
            v-for="a in g.accounts"
            :key="a.username"
            type="button"
            class="quick-chip"
            :class="{ active: form.username === a.username }"
            @click="fillAccount(a)"
          >
            {{ a.label }}
          </button>
        </div>
      </div>

      <div class="tip">默认管理员：admin / admin123（首次登录请及时修改密码）</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const step = ref<1 | 2>(1)
const form = reactive({ username: 'admin', password: 'admin123' })
const totpCode = ref('')
const mfaToken = ref('')
const loading = ref(false)

/** 测试账号清单（与 16/17 号种子一致；省份账号密码统一 test123456） */
interface TestAccount {
  username: string
  password: string
  label: string
}
const PROV_PWD = 'test123456'
const testAccounts: { group: string; accounts: TestAccount[] }[] = [
  {
    group: '管理员',
    accounts: [{ username: 'admin', password: 'admin123', label: 'admin 超管' }]
  },
  {
    group: '上海',
    accounts: [
      { username: 'shanghai_disp', password: PROV_PWD, label: 'shanghai_disp 调度员' },
      { username: 'shanghai_capt', password: PROV_PWD, label: 'shanghai_capt 车队长' }
    ]
  },
  {
    group: '山东',
    accounts: [
      { username: 'shandong_disp', password: PROV_PWD, label: 'shandong_disp 调度员' },
      { username: 'shandong_capt', password: PROV_PWD, label: 'shandong_capt 车队长' }
    ]
  },
  {
    group: '江苏',
    accounts: [
      { username: 'jiangsu_disp', password: PROV_PWD, label: 'jiangsu_disp 调度员' },
      { username: 'jiangsu_capt', password: PROV_PWD, label: 'jiangsu_capt 车队长' }
    ]
  },
  {
    group: '安徽',
    accounts: [
      { username: 'anhui_disp', password: PROV_PWD, label: 'anhui_disp 调度员' },
      { username: 'anhui_capt', password: PROV_PWD, label: 'anhui_capt 车队长' }
    ]
  }
]

/** 点击测试账号：自动填入用户名与密码 */
function fillAccount(a: TestAccount) {
  form.username = a.username
  form.password = a.password
}

async function onLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const result = await auth.login(form.username, form.password)
    if (result.mfaRequired) {
      mfaToken.value = result.mfaToken || ''
      step.value = 2
      totpCode.value = ''
      ElMessage.info('请输入动态口令')
    } else {
      ElMessage.success('登录成功')
      goLanding()
    }
  } finally {
    loading.value = false
  }
}

async function onVerify() {
  if (!/^\d{6}$/.test(totpCode.value)) {
    ElMessage.warning('请输入 6 位数字验证码')
    return
  }
  loading.value = true
  try {
    await auth.verifyMfa(mfaToken.value, totpCode.value)
    ElMessage.success('登录成功')
    goLanding()
  } finally {
    loading.value = false
  }
}

function backToLogin() {
  step.value = 1
  mfaToken.value = ''
  totpCode.value = ''
}

function goLanding() {
  const redirect = route.query.redirect as string
  if (redirect && redirect.startsWith('/')) {
    router.push(redirect)
  } else {
    router.push(auth.firstMenuPath)
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

/* 测试账号快捷填充区 */
.quick {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #e5e7eb;
}

.quick-head {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}

.quick-group {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.quick-label {
  flex: none;
  width: 38px;
  font-size: 12px;
  color: #374151;
}

.quick-chip {
  border: 1px solid #dcdfe6;
  background: #f4f4f5;
  color: #606266;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 20px;
  cursor: pointer;
  transition: all 0.15s;
}

.quick-chip:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.quick-chip.active {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
</style>
