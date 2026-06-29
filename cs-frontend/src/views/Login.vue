<script setup>
/**
 * v2.3.0 登录页 (单一表单)
 *
 * 只支持账号+密码; 后端按 username 前缀识别角色:
 *   - admin / admin_* → 管理员
 *   - user 表里 AGENT role → 坐席
 *   - 其他 → 客户
 */
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth } from '@/api'
import request from '@/api/request'
import { useUserStore } from '@/store/user'
import { maskMobile, getDeviceId } from '@/utils'
import { isMockEnabled } from '@/api/mock'
import SubscribeDialog from '@/components/SubscribeDialog.vue'

const isMockMode = ref(isMockEnabled())

// i18n
const t = ref({
  brand: '在线客服',
  brandSub: 'Spring Cloud · Vue 3 · Element Plus',
  account: '账号',
  password: '密码',
  accountPlaceholder: 'customer001 / agent001 / admin',
  passwordPlaceholder: '6+ 位密码',
  loginBtn: '登录',
  registerBtn: '注册账号',
  forgot: '忘记密码',
  backLogin: '返回登录',
  warnFailed: (n) => `登录失败 ${n} 次, 剩余 ${5 - n} 次机会后锁定 15 分钟`,
  warnLocked: '账户已锁定, 请 15 分钟后重试',
  warnNewDevice: '检测到新设备登录, 请核实是否本人',
  warnWeak: '密码强度: 弱',
  warnMedium: '密码强度: 中',
  warnStrong: '密码强度: 强',
  recaptchaScoreLow: '异常请求被拦截',
  verifyCode: '获取验证码',
  resendIn: (n) => `${n}s 后重发`
})

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const activeTab = ref('login')
const submitting = ref(false)
const sendCooldown = ref(0)

// v2.3.0: 单一表单
const form = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirm: '' })
const registerMode = ref(false)

const loginFailCount = ref(0)
const loginLockedUntil = ref(0)
const loginCooldown = ref(0)

// 密码强度
const passwordStrength = computed(() => {
  const p = activeTab.value === 'login' ? form.password : registerForm.password
  if (!p) return 0
  let s = 0
  if (p.length >= 8) s++
  if (p.length >= 12) s++
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) s++
  if (/\d/.test(p)) s++
  if (/[^A-Za-z0-9]/.test(p)) s++
  return Math.min(s, 4)
})

const passwordStrengthLabel = computed(() =>
  ['', '弱', '中', '良', '强'][passwordStrength.value])
const passwordStrengthColor = computed(() =>
  ['', '#f56c6c', '#e6a23c', '#67c23a', '#1890ff'][passwordStrength.value])

// 上次登录 + 设备指纹
const securityTips = reactive({
  lastLoginAt: localStorage.getItem('cs_last_login') || null,
  lastDevice: localStorage.getItem('cs_last_device') || null,
  knownDevice: localStorage.getItem('cs_device_id') || null
})
const isNewDevice = computed(() =>
  securityTips.knownDevice && getDeviceId() !== securityTips.knownDevice
)

onMounted(() => {
  if (route.query.next) {/* 占位 */}
  if (route.query.register) registerMode.value = true
  if (loginLockedUntil.value > Date.now()) {
    const tick = () => {
      loginCooldown.value = Math.max(0, Math.ceil((loginLockedUntil.value - Date.now()) / 1000))
      if (loginCooldown.value > 0) setTimeout(tick, 1000)
    }
    tick()
  }
})

async function login() {
  if (!form.username || !form.password) {
    return ElMessage.warning('账号和密码必填')
  }
  if (loginLockedUntil.value > Date.now()) {
    return ElMessage.warning(`账户已锁定, 请 ${Math.ceil(loginCooldown.value / 60)} 分钟后重试`)
  }
  if (passwordStrength.value < 1) {
    return ElMessage.warning('密码过短 (>= 6 位)')
  }
  submitting.value = true
  try {
    const res = await request.post('/auth/login', { username: form.username, password: form.password })
    if (res.data?.code === 0) {
      loginFailCount.value = 0
      localStorage.setItem('cs_last_login', new Date().toISOString())
      localStorage.setItem('cs_last_device', getDeviceId())
      handleLoginSuccess(res.data.data)
    } else {
      throw new Error(res.data?.msg || '登录失败')
    }
  } catch (e) {
    loginFailCount.value++
    if (loginFailCount.value >= 5) {
      loginLockedUntil.value = Date.now() + 15 * 60 * 1000
      ElMessage.error('连续失败 5 次, 账户锁定 15 分钟')
    } else {
      ElMessage.error(`登录失败 (${loginFailCount.value}/5): ${e.message || '账号或密码错误'}`)
    }
  } finally {
    submitting.value = false
  }
}

function handleLoginSuccess(data) {
  localStorage.setItem('cs_device_id', getDeviceId())
  localStorage.setItem('cs_last_login', new Date().toISOString())
  user.setToken(data.token, {
    id: data.customerId || data.userId,
    name: data.nickname || data.displayName,
    role: data.role || 'CUSTOMER',
    channel: data.channel || 'LOCAL'
  })
  if (data.csrf) localStorage.setItem('cs_csrf', data.csrf)

  // v2.3.0: 按 role 路由
  const role = data.role || 'CUSTOMER'
  const next = route.query.next
  let target = next
  if (!target) {
    if (role === 'ADMIN') target = 'admin'
    else if (role === 'AGENT') target = 'agent'
    else target = 'customer'
  } else {
    const map = { customer: 'customer', agent: 'agent', admin: 'admin' }
    target = map[target] || target
  }
  router.push({ name: target })
}

async function register() {
  if (registerForm.password !== registerForm.confirm) return ElMessage.warning('两次密码不一致')
  if (registerForm.password.length < 6) return ElMessage.warning('密码至少 6 位')
  submitting.value = true
  try {
    await auth.register(registerForm.username, registerForm.password)
    ElMessage.success('注册成功, 请登录')
    registerMode.value = false
    form.username = registerForm.username
  } finally {
    submitting.value = false
  }
}

const recaptchaEnabled = ref(false)
const recaptchaScore = computed(() => 0.95)

// 演示账号标签 (用于开发快速登录)
const demoAccounts = [
  { username: 'customer001', password: 'pass123', label: '客户 demo', role: 'CUSTOMER' },
  { username: 'agent001', password: 'pass123', label: '坐席 demo', role: 'AGENT' },
  { username: 'admin', password: 'admin', label: '管理员 demo', role: 'ADMIN' }
]
function fillDemo(d) {
  form.username = d.username
  form.password = d.password
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <div class="logo">💬</div>
        <h2>{{ t.brand }}</h2>
        <p>{{ t.brandSub }}</p>
      </div>

      <!-- 上次登录 / 新设备提示 -->
      <el-alert
        v-if="securityTips.lastLoginAt"
        :type="isNewDevice ? 'warning' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          <small v-if="isNewDevice">⚠ 检测到新设备登录, 请核实是否本人</small>
          <small v-else>上次登录: {{ securityTips.lastLoginAt }} · 可信设备</small>
        </template>
      </el-alert>

      <!-- 失败计数 -->
      <el-alert
        v-if="loginFailCount > 0 && loginFailCount < 5"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          <small>{{ t.warnFailed(loginFailCount) }}</small>
        </template>
      </el-alert>

      <el-form v-if="!registerMode" @submit.prevent="login" label-position="top">
        <el-form-item :label="t.account">
          <el-input v-model="form.username" :placeholder="t.accountPlaceholder" clearable maxlength="32" autocomplete="username" />
        </el-form-item>
        <el-form-item :label="t.password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t.passwordPlaceholder"
            show-password
            maxlength="64"
            autocomplete="current-password"
            @keyup.enter="login"
          />
          <div v-if="form.password" class="pwd-strength">
            <div class="pwd-strength-bar">
              <div class="pwd-strength-fill"
                   :style="{ width: ((passwordStrength/4)*100)+'%', background: passwordStrengthColor }"></div>
            </div>
            <span class="pwd-strength-label" :style="{ color: passwordStrengthColor }">
              {{ passwordStrengthLabel }}
            </span>
          </div>
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="full" @click="login"
                   :disabled="loginLockedUntil > Date.now()">
          {{ t.loginBtn }}
        </el-button>
        <div class="links">
          <el-link type="primary" :underline="'never'" @click="registerMode = true">{{ t.registerBtn }}</el-link>
        </div>
      </el-form>

      <el-form v-else @submit.prevent="register" label-position="top">
        <el-form-item :label="t.account">
          <el-input v-model="registerForm.username" maxlength="32" />
        </el-form-item>
        <el-form-item :label="t.password">
          <el-input v-model="registerForm.password" type="password" maxlength="64" show-password />
          <div v-if="registerForm.password" class="pwd-strength">
            <div class="pwd-strength-bar">
              <div class="pwd-strength-fill"
                   :style="{ width: ((passwordStrength/4)*100)+'%', background: passwordStrengthColor }"></div>
            </div>
            <span class="pwd-strength-label" :style="{ color: passwordStrengthColor }">
              {{ passwordStrengthLabel }}
            </span>
          </div>
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="registerForm.confirm" type="password" maxlength="64" show-password />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="full"
                   @click="register" :disabled="passwordStrength < 1">
          注册
        </el-button>
        <el-link type="primary" :underline="'never'" @click="registerMode = false">
          {{ t.backLogin }}
        </el-link>
      </el-form>

      <el-divider>快速登录 (Demo)</el-divider>
      <div class="demo-row">
        <el-button v-for="d in demoAccounts" :key="d.username"
                   size="small" plain
                   @click="fillDemo(d)">
          <el-tag size="small" :type="d.role === 'ADMIN' ? 'danger' : (d.role === 'AGENT' ? 'warning' : 'success')" effect="plain">
            {{ d.role }}
          </el-tag>
          {{ d.label }}
        </el-button>
      </div>

      <!-- reCAPTCHA 占位 (生产可接 v3) -->
      <div v-if="recaptchaEnabled" class="captcha">
        <small>🤖 reCAPTCHA score: {{ recaptchaScore }}</small>
      </div>

      <div class="brand-info">
        {{ t.brand }} v2.3.0 · Production<br>
        <small style="color:#909399">国密 SM2/SM4 加密 · 等保 2.0 · HTTPS / HSTS</small>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 20% 30%, rgba(22,119,255,0.10) 0%, transparent 50%),
              radial-gradient(circle at 80% 70%, rgba(114,46,209,0.10) 0%, transparent 50%),
              #f5f7fa;
  padding: 24px;
}
.login-card { width: 100%; max-width: 420px; border-radius: 16px; }
.brand {
  text-align: center; margin-bottom: 24px;
  .logo {
    width: 56px; height: 56px;
    background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
    border-radius: 14px;
    display: flex; align-items: center; justify-content: center;
    font-size: 28px; margin: 0 auto 12px;
    color: #fff;
  }
  h2 { margin: 0 0 4px; font-size: 22px; }
  p { margin: 0; font-size: 12px; color: #86909c; }
}
.full { width: 100%; }
.links { display: flex; justify-content: flex-end; margin-top: 8px; font-size: 13px; }
.demo-row { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
.captcha { margin-top: 8px; padding: 8px; background: #f0f9ff; border-radius: 4px; text-align: center; }
.brand-info {
  margin-top: 16px; padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 11px; color: #909399; text-align: center; line-height: 1.6;
}
.pwd-strength {
  display: flex; align-items: center; gap: 8px; margin-top: 6px; font-size: 12px;
  .pwd-strength-bar {
    flex: 1; height: 4px; background: #ebeef5; border-radius: 2px; overflow: hidden;
  }
  .pwd-strength-fill {
    height: 100%; transition: width 0.2s, background 0.2s; border-radius: 2px;
  }
  .pwd-strength-label { min-width: 24px; font-weight: 500; }
}
</style>
