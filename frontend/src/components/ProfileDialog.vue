<template>
  <el-dialog v-model="visible" title="个人中心" width="520px" @closed="onClosed">
    <el-tabs v-model="activeTab">
      <!-- 基本资料 -->
      <el-tab-pane label="基本资料" name="profile">
        <el-form :model="profileForm" label-width="90px">
          <el-form-item label="用户名">
            <el-input :model-value="auth.profile?.username" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input :model-value="auth.profile?.realName" disabled />
          </el-form-item>
          <el-form-item label="所属组织">
            <el-input :model-value="auth.profile?.deptName || '--'" disabled />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="profileForm.phone" maxlength="20" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" maxlength="80" />
          </el-form-item>
          <el-form-item label="最近登录">
            <el-input :model-value="auth.profile?.lastLoginTime || '--'" disabled />
          </el-form-item>
        </el-form>
        <div class="tab-actions">
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </div>
      </el-tab-pane>

      <!-- 修改密码 -->
      <el-tab-pane label="修改密码" name="password">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="密码须为 8~20 位，且同时包含字母和数字"
          style="margin-bottom: 16px"
        />
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="110px">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password maxlength="32" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password maxlength="20" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm">
            <el-input v-model="pwdForm.confirm" type="password" show-password maxlength="20" />
          </el-form-item>
        </el-form>
        <div class="tab-actions">
          <el-button type="primary" :loading="savingPwd" @click="savePassword">修改密码</el-button>
        </div>
      </el-tab-pane>

      <!-- 动态口令 -->
      <el-tab-pane :label="mfaOn ? '动态口令（已开启）' : '动态口令'" name="mfa">
        <!-- 未开启：绑定流程 -->
        <template v-if="!mfaOn">
          <template v-if="!setupInfo">
            <el-empty description="尚未绑定动态口令，绑定后登录需二次验证" :image-size="80">
              <el-button type="primary" :loading="loadingSetup" @click="startSetup">开始绑定</el-button>
            </el-empty>
          </template>
          <template v-else>
            <div class="mfa-box">
              <div class="mfa-qr">
                <img v-if="qrDataUrl" :src="qrDataUrl" alt="TOTP 二维码" width="170" height="170" />
              </div>
              <div class="mfa-secret">
                <div class="hint">使用 Google Authenticator / 微软 Authenticator 等扫码：</div>
                <el-input :model-value="setupInfo.secret" readonly>
                  <template #append>
                    <el-button @click="copySecret">复制密钥</el-button>
                  </template>
                </el-input>
              </div>
              <el-input
                v-model="enableCode"
                placeholder="输入 App 中 6 位验证码完成绑定"
                maxlength="6"
                style="margin-top: 14px"
              />
              <div class="tab-actions">
                <el-button type="primary" :loading="enabling" @click="confirmEnable">确认绑定</el-button>
                <el-button @click="cancelSetup">取消</el-button>
              </div>
            </div>
          </template>
        </template>

        <!-- 已开启：关闭流程 -->
        <template v-else>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="动态口令保护中。关闭后登录将仅校验密码，请谨慎操作。"
            style="margin-bottom: 16px"
          />
          <el-form :model="disableForm" label-width="110px">
            <el-form-item label="登录密码">
              <el-input v-model="disableForm.password" type="password" show-password maxlength="32" />
            </el-form-item>
            <el-form-item label="当前验证码">
              <el-input v-model="disableForm.totpCode" maxlength="6" placeholder="6 位动态验证码" />
            </el-form-item>
          </el-form>
          <div class="tab-actions">
            <el-button type="danger" :loading="disabling" @click="confirmDisable">关闭动态口令</el-button>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import QRCode from 'qrcode'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import {
  updateProfile,
  changePassword,
  setupMfa,
  enableMfa,
  disableMfa,
  type MfaSetupInfo
} from '@/api/auth'

const auth = useAuthStore()

const visible = ref(false)
const activeTab = ref('profile')
const mfaOn = computed(() => auth.profile?.mfaEnabled === 1)

function open() {
  visible.value = true
  activeTab.value = 'profile'
  if (auth.profile) {
    profileForm.phone = auth.profile.phone || ''
    profileForm.email = auth.profile.email || ''
  }
}

defineExpose({ open })

// ===== 基本资料 =====
const profileForm = reactive({ phone: '', email: '' })
const savingProfile = ref(false)

async function saveProfile() {
  savingProfile.value = true
  try {
    await updateProfile(profileForm.phone, profileForm.email)
    await auth.loadProfile(true)
    ElMessage.success('资料已更新')
  } finally {
    savingProfile.value = false
  }
}

// ===== 修改密码 =====
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const savingPwd = ref(false)

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (!value) return cb(new Error('请输入新密码'))
        if (value.length < 8 || value.length > 20) return cb(new Error('长度须为 8~20 位'))
        if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
          return cb(new Error('须同时包含字母和数字'))
        }
        cb()
      },
      trigger: 'blur'
    }
  ],
  confirm: [
    {
      validator: (_r, value: string, cb) => {
        if (value !== pwdForm.newPassword) return cb(new Error('两次输入的新密码不一致'))
        cb()
      },
      trigger: 'blur'
    }
  ]
}

async function savePassword() {
  await pwdFormRef.value?.validate()
  savingPwd.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
  } finally {
    savingPwd.value = false
  }
}

// ===== 动态口令绑定 =====
const setupInfo = ref<MfaSetupInfo | null>(null)
const qrDataUrl = ref('')
const enableCode = ref('')
const loadingSetup = ref(false)
const enabling = ref(false)
const disabling = ref(false)
const disableForm = reactive({ password: '', totpCode: '' })

async function startSetup() {
  loadingSetup.value = true
  try {
    setupInfo.value = await setupMfa()
    qrDataUrl.value = await QRCode.toDataURL(setupInfo.value.otpauthUri, { width: 170, margin: 1 })
  } finally {
    loadingSetup.value = false
  }
}

async function copySecret() {
  if (!setupInfo.value) return
  await navigator.clipboard.writeText(setupInfo.value.secret)
  ElMessage.success('密钥已复制')
}

function cancelSetup() {
  setupInfo.value = null
  qrDataUrl.value = ''
  enableCode.value = ''
}

async function confirmEnable() {
  if (!/^\d{6}$/.test(enableCode.value)) {
    ElMessage.warning('请输入 6 位数字验证码')
    return
  }
  enabling.value = true
  try {
    await enableMfa(enableCode.value)
    ElMessage.success('动态口令绑定成功')
    cancelSetup()
    await auth.loadProfile(true)
  } finally {
    enabling.value = false
  }
}

async function confirmDisable() {
  if (!disableForm.password || !/^\d{6}$/.test(disableForm.totpCode)) {
    ElMessage.warning('请输入登录密码和 6 位验证码')
    return
  }
  disabling.value = true
  try {
    await disableMfa(disableForm.password, disableForm.totpCode)
    ElMessage.success('动态口令已关闭')
    disableForm.password = ''
    disableForm.totpCode = ''
    await auth.loadProfile(true)
  } finally {
    disabling.value = false
  }
}

function onClosed() {
  cancelSetup()
  disableForm.password = ''
  disableForm.totpCode = ''
}
</script>

<style scoped>
.tab-actions {
  margin-top: 8px;
  text-align: right;
}

.mfa-box {
  padding: 0 8px;
}

.mfa-qr {
  display: flex;
  justify-content: center;
  margin-bottom: 14px;
}

.mfa-qr img {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
}

.mfa-secret .hint {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
}
</style>
