<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth } from '@/api'
import { useUserStore } from '@/store/user'
import { maskMobile } from '@/utils'
import { isMockEnabled } from '@/api/mock'

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

const passwordForm = reactive({ username: '', password: '' })
const phoneForm = reactive({ phone: '', code: '' })
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
})

async function loginByPassword() {
  if (!passwordForm.username || !passwordForm.password) {
    return ElMessage.warning('请输入账号和密码')
  }
  submitting.value = true
  try {
    const { data } = await auth.loginByPassword(passwordForm.username, passwordForm.password)
    handleLoginSuccess(data)
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
  user.setToken(data.token, { id: data.customerId || data.userId, name: data.nickname || data.displayName, role: data.role || 'CUSTOMER', channel: data.channel })
  // 取 CSRF Token（后端通过登录响应下发）
  if (data.csrf) localStorage.setItem('cs_csrf', data.csrf)
  const next = route.query.next
  const map = { customer: 'customer', agent: 'agent', admin: 'admin' }
  router.push({ name: map[next] || 'customer' })
}

async function oauthLogin(provider) {
  // 开箱即用：axios 拿 JSON 形式的 redirect URL（避免 PC 端 302 弹窗被拦截）
  // 1. 先调 authorize-json 拿到微信授权 URL（带上当前域名作为 redirect_uri）
  // 2. window.location.href = url 跳转（这一步是必要的，PC 端会显示二维码）
  const redirectUri = `${location.origin}/auth/${provider}/callback`
  try {
    // v2.2.39: 根据设备推荐 scope（微信内静默 / 其他 snsapi_userinfo）
    const { data } = await auth.oauthAuthorizeJson(provider, redirectUri, recommendedScope.value)
    if (data.code === 0 && data.data?.url) {
      // mock 模式下 url 是 redirect_uri?code=MOCK-xxx，需要前端路由处理
      // 真实模式下 url 是 open.weixin.qq.com/...，浏览器直接跳转
      if (data.data.url.includes('mock=true')) {
        // mock: 直接 window.location 跳转，前端路由会自动跳到 /auth/{provider}/callback
        window.location.href = data.data.url
      } else {
        // 真实微信: 跳转
        window.location.href = data.data.url
      }
      return
    }
    ElMessage.error(data.msg || '授权初始化失败')
  } catch (e) {
    ElMessage.error('网络错误: ' + (e.message || 'unknown'))
    // 兜底: 用 location.href 302 流程
    const redirectUri = `${location.origin}/auth/${provider}/callback`
    location.href = `/auth/${provider}/authorize?redirect_uri=${encodeURIComponent(redirectUri)}`
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <div class="logo">💬</div>
        <h2>在线客服</h2>
        <p>Spring Cloud · Vue 3 · Element Plus</p>
      </div>

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
        <el-tab-pane label="账号密码" name="password">
          <el-form @submit.prevent="loginByPassword" label-position="top">
            <el-form-item label="账号">
              <el-input v-model="passwordForm.username" placeholder="请输入账号" clearable :prefix-icon="'User'" maxlength="32" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="passwordForm.password" type="password" placeholder="请输入密码" show-password maxlength="64" @keyup.enter="loginByPassword" />
            </el-form-item>
            <el-button type="primary" :loading="submitting" class="full" @click="loginByPassword">登录</el-button>
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
                  {{ sendCooldown > 0 ? `${sendCooldown}s` : '获取验证码' }}
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
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="registerForm.confirm" type="password" maxlength="64" show-password />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="full" @click="register">注册</el-button>
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