<template>
  <div class="oauth-callback">
    <el-card class="callback-card" shadow="always">
      <div class="logo">💬</div>
      <h2>{{ title }}</h2>
      <p v-if="status === 'pending'" class="loading">
        <el-icon class="rotating"><Loading /></el-icon>
        {{ message }}
      </p>
      <p v-else-if="status === 'success'" class="success">
        <el-icon><CircleCheckFilled /></el-icon>
        {{ message }}
      </p>
      <p v-else class="error">
        <el-icon><CircleCloseFilled /></el-icon>
        {{ message }}
      </p>

      <div v-if="provider" class="provider-tag">
        <el-tag size="small">{{ providerLabel }}</el-tag>
        <span v-if="mock" class="mock-tag">MOCK</span>
      </div>

      <div v-if="userInfo" class="user-info">
        <div><strong>昵称：</strong>{{ userInfo.nickname }}</div>
        <div><strong>角色：</strong>{{ userInfo.role }}</div>
        <div><strong>OpenID：</strong>{{ userInfo.openid }}</div>
      </div>

      <div class="actions">
        <el-button v-if="status === 'success'" type="primary" @click="goHome">进入工作台</el-button>
        <el-button v-else-if="status === 'error'" @click="goLogin">返回登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const status = ref('pending')   // pending | success | error
const message = ref('处理微信授权...')
const title = ref('微信授权')
const provider = ref('')
const providerLabel = ref('')
const mock = ref(false)
const userInfo = ref(null)

onMounted(async () => {
  // URL: /auth/wechat-oa/callback?code=xxx&state=yyy&mock=true
  // URL: /auth/github/callback?code=xxx&state=yyy (or ?token= for direct)
  // URL: /auth/google/callback?code=xxx&state=yyy
  // URL: /auth/wx-work/callback?code=xxx&state=yyy

  // 直接拿 token 模式（mock oauthAuthorize 重定向）
  const tokenDirect = route.query.token
  if (tokenDirect) {
    return handleDirectToken(tokenDirect)
  }

  // code 模式（后端 /auth/{provider}/callback 处理）
  const code = route.query.code
  if (!code) {
    return fail('缺少授权 code')
  }

  const path = route.path
  const providerMatch = path.match(/\/auth\/(.+?)\/callback/)
  if (!providerMatch) return fail('无法识别 provider')
  const prov = providerMatch[1]
  provider.value = prov
  providerLabel.value = labelOf(prov)
  mock.value = route.query.mock === 'true'

  // 后端 callback 通常自己处理 token 重定向，但这里 callback 是前端路由
  // 调后端 /auth/{provider}/callback 让它返回 token JSON
  message.value = `正在交换 ${labelOf(prov)} 授权码...`
  try {
    const { data } = await request.get(`/auth/${prov}/callback-json`, { params: { code } })
    if (data.code !== 0) throw new Error(data.msg || '授权失败')
    handleToken(data.data)
  } catch (e) {
    // 后端可能直接重定向（302），前端没收到 JSON 也不报错——给个 mock token 给演示
    if (mock.value || e.message?.includes('Network')) {
      ElMessage.warning('后端未实现 callback-json，使用前端 Mock 登录')
      const { mockAuth } = await import('@/api/mock')
      const r = await mockAuth.loginByPassword('demo', 'demo123')
      if (r.data.code === 0) {
        handleToken(r.data.data, true)
        return
      }
    }
    fail('授权失败：' + (e.response?.data?.msg || e.message))
  }
})

function labelOf(p) {
  return ({ 'wechat-oa': '微信公众号', 'wx-oa': '微信公众号', 'wx-work': '企业微信', 'github': 'GitHub', 'google': 'Google' })[p] || p
}

function handleDirectToken(token) {
  user.setToken(token, { id: 'oauth-user', name: '微信用户', role: 'CUSTOMER', channel: 'OA' })
  success('微信登录成功！', { nickname: '微信用户', role: 'CUSTOMER', openid: 'oauth-direct' })
}

function handleToken(data, isMock = false) {
  user.setToken(data.token, {
    id: data.customerId || data.userId,
    name: data.nickname || data.displayName,
    role: data.role || 'CUSTOMER',
    channel: data.channel || 'OA'
  })
  if (data.csrf) localStorage.setItem('cs_csrf', data.csrf)
  if (isMock) mock.value = true
  success(`登录成功${isMock ? '（前端 Mock 回退）' : ''}`, {
    nickname: data.nickname || data.displayName || '微信用户',
    role: data.role || 'CUSTOMER',
    openid: data.openid || data.customerId || 'unknown'
  })
}

function success(msg, info) {
  status.value = 'success'
  message.value = msg
  userInfo.value = info
  ElMessage.success(msg)
  setTimeout(() => router.push({ name: 'customer' }), 1500)
}

function fail(msg) {
  status.value = 'error'
  message.value = msg
  ElMessage.error(msg)
}

function goHome() { router.push({ name: 'customer' }) }
function goLogin() { router.push({ name: 'login' }) }
</script>

<style scoped>
.oauth-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}
.callback-card {
  width: 100%;
  max-width: 420px;
  text-align: center;
  padding: 24px 0;
}
.logo { font-size: 56px; margin-bottom: 8px; }
h2 { margin: 0 0 16px; font-weight: 600; }
.loading, .success, .error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border-radius: 6px;
  font-size: 14px;
}
.loading { background: #f4f4f5; color: #909399; }
.success { background: #f0f9eb; color: #67c23a; }
.error { background: #fef0f0; color: #f56c6c; }
.provider-tag {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}
.mock-tag {
  font-size: 11px;
  color: #e6a23c;
  border: 1px solid #e6a23c;
  padding: 1px 6px;
  border-radius: 4px;
}
.user-info {
  margin-top: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  text-align: left;
  font-size: 13px;
  line-height: 1.8;
}
.actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
.rotating { animation: spin 1s linear infinite; font-size: 18px; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>