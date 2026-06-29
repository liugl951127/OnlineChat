<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth } from '@/api'
import request from '@/api/request'
import { useUserStore } from '@/store/user'
import { maskMobile, getDeviceId } from '@/utils'
import { isMockEnabled } from '@/api/mock'
import SubscribeDialog from '@/components/SubscribeDialog.vue'  // v2.2.80

// v2.2.96: i18n 占位 (后续接 vue-i18n)
// 目前用本抹常量, 后续迁移到 $t('login.xxx')
const i18n = ref({
  brand: '在线客服',
  brandSub: 'Spring Cloud · Vue 3 · Element Plus',
  warnWeak: '密码强度: 弱, 建议使用大小写+数字+特珠字符 12+ 位',
  warnMedium: '密码强度: 中',
  warnStrong: '密码强度: 强',
  warnFailed: (n) => `登录失败 ${n} 次，剩余 ${5 - n} 次机会后锁定 15 分钟`,
  warnLocked: '账户已锁定，请 15 分钟后重试',
  warnNewDevice: '检测到新设备登录, 请核实是否本人操作',
  warnLastLogin: (t) => `上次登录: ${t}`,
  warnSmsCooldown: (n) => `${n} 秒后可重发`,
  recaptchaToken: 'reCAPTCHA 检测中...',
  recaptchaScoreLow: '异常请求被拦截, 请刷新页面重试'
})

const isMockMode = ref(isMockEnabled())

// v2.2.39: 设备自适应 OAuth provider
const deviceType = ref('PC')        // PC / MOBILE / WECHAT_OA / MINI
const recommendedScope = ref('snsapi_userinfo')
const deviceNote = ref('')
const providerMap = {
  'wechat-oa':   { icon: '💚', label: '公众号' },
  'wechat-work': { icon: '💼', label: '企微' },
  'github':      { icon: '⭐', label: 'GitHub' },
  'google':      { icon: '🔗', label: 'Google' }
}
const availableProviders = ref(Object.keys(providerMap))

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const activeTab = ref('password')
const submitting = ref(false)
const sendCooldown = ref(0)

// v2.2.96: 登录失败冷却 (后端 lock_until 字段, 前端额外留 5 次限制提示)
const loginFailCount = ref(0)
const loginLockedUntil = ref(0)
const loginCooldown = ref(0)

const passwordForm = reactive({ username: '', password: '' })
const phoneForm = reactive({ phone: '', code: '' })

// v2.2.96: 密码强度计算 (zxcvbn 简化算法)
const passwordStrength = computed(() => {
  const pwd = passwordForm.password
  if (!pwd) return 0
  let score = 0
  if (pwd.length >= 8) score++
  if (pwd.length >= 12) score++
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  return Math.min(score, 4)  // 0-4 级
})

const passwordStrengthLabel = computed(() => {
  return ['', '弱', '中', '良', '强'][passwordStrength.value]
})

const passwordStrengthColor = computed(() => {
  return ['', '#f56c6c', '#e6a23c', '#67c23a', '#1890ff'][passwordStrength.value]
})

const registerStrength = computed(() => passwordStrength)

// v2.2.96: 安全提示条 (上次登录 + 设备指纹)
const securityTips = reactive({
  lastLoginAt: localStorage.getItem('cs_last_login') || null,
  lastLoginDevice: localStorage.getItem('cs_last_device') || null,
  knownDevice: localStorage.getItem('cs_device_id') || null
})

const isNewDevice = computed(() => {
  if (!securityTips.knownDevice) return false
  return getDeviceId() !== securityTips.knownDevice
})

// v2.2.80: 公众号关注弹窗状态
const subscribeDialog = reactive({
  visible: false,
  qrcodeUrl: '',
  subscribeUrl: '',
  openid: '',
  pendingCode: ''     // 用户扫码关注后, 重新调用 callback-json
})
const agentForm = reactive({ username: '', password: '' })  // v2.2.40
const registerForm = reactive({ username: '', password: '', confirm: '' })
const registerMode = ref(false)

const errors = reactive({})

onMounted(async () => {
  if (route.query.next) {/* 占位：登录后跳转 */}
  if (route.query.register) registerMode.value = true
  // v2.2.39: 检测设备类型 + 推荐 OAuth provider
  try {
    const { data } = await auth.oauthRecommend()
    if (data.code === 0 && data.data) {
      deviceType.value = data.data.device || 'PC'
      recommendedScope.value = data.data.scope || 'snsapi_userinfo'
      deviceNote.value = data.data.note || ''
      availableProviders.value = data.data.providers || Object.keys(providerMap)
    }
  } catch (e) {
    // 推荐接口挂了用默认（全部 4 个）
    console.warn('[Login] recommend failed, use default:', e.message)
  }
  // v2.2.96: 锁定倒计时 (冷却秒数实时更新)
  if (loginLockedUntil.value > Date.now()) {
    const tick = () => {
      const sec = Math.max(0, Math.ceil((loginLockedUntil.value - Date.now()) / 1000))
      loginCooldown.value = sec
      if (sec > 0) setTimeout(tick, 1000)
    }
    tick()
  }
})

async function loginByPassword() {
  if (!passwordForm.username || !passwordForm.password) {
    return ElMessage.warning('请输入账号和密码')
  }
  if (loginLockedUntil.value > Date.now()) {
    const sec = Math.ceil((loginLockedUntil.value - Date.now()) / 1000)
    return ElMessage.warning(`账户已锁定，请 ${Math.ceil(sec/60)} 分钟后重试`)
  }
  // v2.2.96: 表单验证
  if (passwordStrength.value < 1) {
    ElMessage.warning('密码过于简单，请使用 8+ 位字符')
    return
  }
  submitting.value = true
  try {
    const { data } = await auth.loginByPassword(passwordForm.username, passwordForm.password)
    loginFailCount.value = 0   // 成功重置
    localStorage.setItem('cs_last_login', new Date().toISOString())
    localStorage.setItem('cs_last_device', getDeviceId())
    handleLoginSuccess(data)
  } catch (e) {
    loginFailCount.value++
    if (loginFailCount.value >= 5) {
      loginLockedUntil.value = Date.now() + 15 * 60 * 1000  // 15分钟锁定
      ElMessage.error('连续失败 5 次, 账户锁定 15 分钟')
      return
    }
    ElMessage.error(`登录失败 (${loginFailCount.value}/5), ${e.message || '账号或密码错误'}`)
  } finally {
    submitting.value = false
  }
}

async function loginByPhone() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) return ElMessage.warning('手机号格式错误')
  if (!phoneForm.code) return ElMessage.warning('请输入验证码')
  submitting.value = true
  try {
    const { data } = await auth.loginByPhone(phoneForm.phone, phoneForm.code)
    handleLoginSuccess(data)
  } finally {
    submitting.value = false
  }
}

// v2.2.68: 访客登录 (静默分配客服, 用设备号切丁客户唯一标识)
// v2.2.80: 公众号订阅成功, 重新调用 callback-json 完成登录
async function finishOaLogin(code) {
  try {
    const { data } = await request.post('/auth/wechat-oa/callback-json', { code })
    if (data.code === 0) {
      handleLoginSuccess(data.data)
      ElMessage.success('公众号登录成功')
      subscribeDialog.visible = false
    } else {
      ElMessage.error(data.msg || '登录失败')
    }
  } catch (e) {
    ElMessage.error('登录失败: ' + (e.message || ''))
  }
}

async function silentLogin() {
  submitting.value = true
  try {
    // 后端端点: POST /auth/silent-login
    // v2.2.68: 传 deviceId 作为客户唯一标识 (localStorage 持久化)
    const deviceId = getDeviceId()
    const { data } = await auth.silent({ deviceId })
    handleLoginSuccess(data)
    ElMessage.success('以访客身份进入，系统已为您分配客服')
  } catch (e) {
    ElMessage.error('访客登录失败: ' + (e.message || 'unknown'))
  } finally {
    submitting.value = false
  }
}

async function register() {
  if (registerForm.password !== registerForm.confirm) return ElMessage.warning('两次密码不一致')
  if (registerForm.password.length < 6) return ElMessage.warning('密码至少 6 位')
  submitting.value = true
  try {
    await auth.register(registerForm)
    ElMessage.success('注册成功，请登录')
    registerMode.value = false
    activeTab.value = 'password'
    passwordForm.username = registerForm.username
  } finally {
    submitting.value = false
  }
}

// v2.2.40: 坐席账号密码登录
async function loginByAgent() {
  if (!agentForm.username || !agentForm.password) {
    return ElMessage.warning('请输入账号和密码')
  }
  submitting.value = true
  try {
    const { data } = await auth.agentLogin(agentForm.username, agentForm.password)
    handleLoginSuccess(data)
  } finally {
    submitting.value = false
  }
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) return ElMessage.warning('手机号格式错误')
  if (sendCooldown.value > 0) return
  const { data } = await auth.sendSms(phoneForm.phone)
  sendCooldown.value = 60
  const timer = setInterval(() => {
    sendCooldown.value--
    if (sendCooldown.value <= 0) clearInterval(timer)
  }, 1000)
  ElMessage.success(data?.debugCode ? `验证码已发送（Mock：${data.debugCode}）` : '验证码已发送')
}

function handleLoginSuccess(data) {
  // v2.2.96: 记录设备指纹 + 登录时间
  localStorage.setItem('cs_device_id', getDeviceId())
  localStorage.setItem('cs_last_login', new Date().toISOString())
  user.setToken(data.token, { id: data.customerId || data.userId, name: data.nickname || data.displayName, role: data.role || 'CUSTOMER', channel: data.channel })
  // 取 CSRF Token（后端通过登录响应下发）
  if (data.csrf) localStorage.setItem('cs_csrf', data.csrf)
  const next = route.query.next
  const map = { customer: 'customer', agent: 'agent', admin: 'admin' }
  // v2.2.40: 根据 role 智能跳转 (没有 ?next 时)
  let target = map[next]
  if (!target) {
    const role = data.role || 'CUSTOMER'
    if (role === 'ADMIN') target = 'admin'
    else if (role === 'AGENT') target = 'agent'
    else target = 'customer'
  }
  router.push({ name: target })
}

async function oauthLogin(provider) {
  // v2.2.80: 公众号登录需要走两步:
  //   1. authorize-json -> 拿到 mock 模式下的 url (含 code=...&state=...)
  //   2. callback-json -> 后端用 code 换 openid + 检查关注
  //      2a. 已关注 -> 成功登录, 跳转
  //      2b. 未关注 (451) -> 弹 SubscribeDialog, 用户扫码后重新调用 callback-json

  const redirectUri = `${location.origin}/auth/${provider}/callback`
  try {
    const { data } = await auth.oauthAuthorizeJson(provider, redirectUri, recommendedScope.value)
    if (data.code !== 0 || !data.data?.url) {
      ElMessage.error(data.msg || '授权初始化失败')
      return
    }
    const url = data.data.url
    let code = null

    if (url.includes('mock=true')) {
      // mock: 客户端解析 URL, 不真的跳页面
      const u = new URL(url, location.origin)
      code = u.searchParams.get('code')
    } else {
      // 真实: 跳转, 由 OAuthCallback 组件或 redirect_uri 后端路由处理
      window.location.href = url
      return
    }

    if (!code) {
      ElMessage.error('未拿到授权 code')
      return
    }

    // 调用 callback-json 检查关注状态
    const cbRes = await request.post(`/auth/${provider}/callback-json`, { code })
    if (cbRes.data.code === 0) {
      handleLoginSuccess(cbRes.data.data)
      ElMessage.success('公众号登录成功')
    } else if (cbRes.data.code === 451) {
      const errData = cbRes.data.data || {}
      subscribeDialog.qrcodeUrl = errData.qrcodeUrl || ''
      subscribeDialog.subscribeUrl = errData.subscribeUrl || ''
      subscribeDialog.openid = errData.openid || ''
      subscribeDialog.pendingCode = code
      subscribeDialog.visible = true
      ElMessage.warning('请先关注公众号')
    } else {
      ElMessage.error(cbRes.data.msg || '授权回调失败')
    }
  } catch (e) {
    console.error('[oauthLogin] error:', e)
    ElMessage.error('授权失败: ' + (e.message || ''))
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <div class="logo">💬</div>
        <h2>{{ i18n.brand }}</h2>
        <p>{{ i18n.brandSub }}</p>
      </div>

      <!-- v2.2.96: 安全提示条 -->
      <el-alert
        v-if="securityTips.lastLoginAt"
        :type="isNewDevice ? 'warning' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          <small v-if="isNewDevice">检测到新设备登录, 请核实是否本人</small>
          <small v-else>上次登录: {{ securityTips.lastLoginAt }} · 可信设备</small>
        </template>
      </el-alert>

      <!-- v2.2.96: 连续失败提示 -->
      <el-alert
        v-if="loginFailCount > 0 && loginFailCount < 5"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          <small>{{ i18n.warnFailed(loginFailCount) }}</small>
        </template>
      </el-alert>

      <!-- Mock 模式提示横幅 -->
      <el-alert
        v-if="isMockMode"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          <strong>前端 Mock 模式</strong>（不依赖后端）
        </template>
        <div class="mock-tips">
          <div>演示账号：</div>
          <div>· <el-tag size="small">demo / demo123</el-tag> 客户</div>
          <div>· <el-tag size="small">customer001 / pass123</el-tag> 客户</div>
          <div>· <el-tag size="small">agent001 / agent123</el-tag> 坐席</div>
          <div>· <el-tag size="small">admin / admin123</el-tag> 管理员</div>
          <div class="mock-tips-tip">手机号登录验证码：<el-tag size="small" type="success">123456</el-tag></div>
        </div>
      </el-alert>

      <el-tabs v-model="activeTab" v-if="!registerMode">
        <!-- v2.2.40: 坐席登录入口 -->
        <el-tab-pane label="坐席/管理员" name="agent" v-if="deviceType === 'PC'">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
            <template #title>
              <small>坐席账号是 customerId 以 <strong>a-</strong> 开头的账号，管理员 <strong>admin / admin123</strong></small>
            </template>
          </el-alert>
          <el-form @submit.prevent="loginByAgent" label-position="top">
            <el-form-item label="坐席账号">
              <el-input v-model="agentForm.username" placeholder="坐席账号（不含 a- 前缀）" maxlength="64" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="agentForm.password" type="password" placeholder="密码" show-password maxlength="64" @keyup.enter="loginByAgent" />
            </el-form-item>
            <el-button type="primary" :loading="submitting" class="full" @click="loginByAgent">坐席登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="账号密码" name="password">
          <el-form @submit.prevent="loginByPassword" label-position="top">
            <el-form-item label="账号">
              <el-input v-model="passwordForm.username" placeholder="请输入账号" clearable :prefix-icon="'User'" maxlength="32" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="passwordForm.password" type="password" placeholder="请输入密码" show-password maxlength="64" @keyup.enter="loginByPassword" />
              <!-- v2.2.96: 密码强度条 -->
              <div v-if="passwordForm.password" class="pwd-strength">
                <div class="pwd-strength-bar">
                  <div class="pwd-strength-fill" :style="{ width: ((passwordStrength/4)*100)+'%', background: passwordStrengthColor }"></div>
                </div>
                <span class="pwd-strength-label" :style="{ color: passwordStrengthColor }">{{ passwordStrengthLabel }}</span>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="submitting" class="full" @click="loginByPassword" :disabled="loginLockedUntil > Date.now()">登录</el-button>
            <div class="links">
              <el-link type="primary" :underline="'never'" @click="registerMode = true">注册账号</el-link>
              <el-link type="primary" :underline="'never'">忘记密码</el-link>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="手机号" name="phone">
          <el-form @submit.prevent="loginByPhone" label-position="top">
            <el-form-item label="手机号">
              <el-input v-model="phoneForm.phone" placeholder="请输入手机号" :prefix-icon="'Phone'" maxlength="11" />
            </el-form-item>
            <el-form-item label="验证码">
              <div class="code-row">
                <el-input v-model="phoneForm.code" placeholder="6 位验证码" maxlength="6" />
                <el-button :disabled="sendCooldown > 0" @click="sendCode">
                  {{ sendCooldown > 0 ? `${sendCooldown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-button type="primary" :loading="submitting" class="full" @click="loginByPhone">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="访客" name="silent">
          <div class="silent">
            <el-icon :size="48" color="#1677ff"><UserFilled /></el-icon>
            <p>访客模式自动分配客服</p>
            <el-button type="primary" :loading="submitting" class="full" @click="silentLogin">以访客身份进入</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-form v-else @submit.prevent="register" label-position="top">
        <el-form-item label="账号">
          <el-input v-model="registerForm.username" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" maxlength="64" show-password />
          <div v-if="registerForm.password" class="pwd-strength">
            <div class="pwd-strength-bar">
              <div class="pwd-strength-fill" :style="{ width: ((registerStrength/4)*100)+'%', background: passwordStrengthColor }"></div>
            </div>
            <span class="pwd-strength-label" :style="{ color: passwordStrengthColor }">{{ passwordStrengthLabel }}</span>
          </div>
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="registerForm.confirm" type="password" maxlength="64" show-password />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="full" @click="register" :disabled="registerStrength < 1">注册</el-button>
        <el-link type="primary" :underline="'never'" @click="registerMode = false">返回登录</el-link>
      </el-form>

      <el-divider>其他登录方式</el-divider>
      <!-- v2.2.39: 设备提示 -->
      <el-alert
        v-if="deviceNote"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          <small>{{ deviceNote }}</small>
        </template>
      </el-alert>
      <div class="social-row">
        <el-button
          v-for="p in availableProviders"
          :key="p"
          class="social-btn"
          @click="oauthLogin(p)"
        >
          <span>{{ providerMap[p]?.icon }}</span>
          <span>{{ providerMap[p]?.label }}</span>
        </el-button>
        <el-button
          v-if="availableProviders.length === 0"
          disabled
          class="social-btn"
        >
          <small>当前环境无支持的网页登录方式</small>
        </el-button>
      </div>
    </el-card>

    <!-- v2.2.80: 公众号关注引导弹窗 -->
    <SubscribeDialog
      v-model="subscribeDialog.visible"
      :qrcode-url="subscribeDialog.qrcodeUrl"
      :subscribe-url="subscribeDialog.subscribeUrl"
      :openid="subscribeDialog.openid"
      @recheck="data => {
        if (data.subscribed && subscribeDialog.pendingCode) {
          finishOaLogin(subscribeDialog.pendingCode)
        }
      }"
    />

    <!-- v2.2.96: 底部品牌信息 (生产应去除调试信息) -->
    <div class="brand-info">
      在线客服 v1.7.1 · Production<br>
      <!-- <span v-if="recaptchaEnabled">🤖 reCAPTCHA v3 已启用</span> -->
    </div>
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

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: 16px;
}

.brand {
  text-align: center;
  margin-bottom: 24px;

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

.links {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 13px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;

  .el-input { flex: 1; }
  .el-button { flex-shrink: 0; }
}

/* v2.2.96: 密码强度条 */
.pwd-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;

  .pwd-strength-bar {
    flex: 1;
    height: 4px;
    background: #ebeef5;
    border-radius: 2px;
    overflow: hidden;
  }

  .pwd-strength-fill {
    height: 100%;
    transition: width 0.2s, background 0.2s;
    border-radius: 2px;
  }

  .pwd-strength-label {
    min-width: 24px;
    font-weight: 500;
  }
}

/* v2.2.96: 平台品牌提示 (生产版) */
.brand-info {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 11px;
  color: #909399;
  text-align: center;
  line-height: 1.6;
}

.silent {
  text-align: center;
  padding: 16px 0;

  p {
    color: #86909c;
    font-size: 13px;
    margin: 12px 0 20px;
  }
}

.social-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;

  .social-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
  }
}

.mock-tips {
  font-size: 13px;
  line-height: 1.8;
  margin-top: 4px;
}
.mock-tips-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>