import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'
import { mockAuth, isMockEnabled } from './mock'

// ============ 防 XSS：通用清理（DOMPurify） ============
import DOMPurify from 'dompurify'

export function safeText(html) {
  if (typeof html !== 'string') return html
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['b', 'i', 'u', 'strong', 'em', 'br', 'p', 'span'],
    ALLOWED_ATTR: []
  })
}

const service = axios.create({
  baseURL: '',           // 直接调用 Gateway 路径（不含 /api 前缀，与 Gateway 路由一致）
  timeout: 15000,
  withCredentials: false
})

// ============ Request 拦截：Token + CSRF + 时间戳防重放 ============
service.interceptors.request.use(async config => {
  // === Mock 拦截（仅 auth 模块，且仅当 Mock 模式启用） ===
  if (isMockEnabled() && config.url && config.url.startsWith('/auth/')) {
    const url = config.url
    const method = (config.method || 'get').toLowerCase()
    const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data || {})

    try {
      let result
      if (url === '/auth/login' && method === 'post') result = await mockAuth.loginByPassword(body.username, body.password)
      else if (url === '/auth/login-phone' && method === 'post') result = await mockAuth.loginByPhone(body.phone, body.code)
      else if (url === '/auth/sms-code' && method === 'post') result = await mockAuth.sendSms(body.phone)
      else if (url === '/auth/register' && method === 'post') result = await mockAuth.register(body)
      else if (url === '/auth/me' && method === 'get') result = await mockAuth.me()
      else if (url === '/auth/logout' && method === 'post') result = await mockAuth.logout()
      else if (url === '/auth/silent-login' && method === 'post') result = await mockAuth.silent(body?.token)
      else if (url.endsWith('/authorize')) {
        return mockAuth.oauthAuthorize(url.split('/')[2], config.params?.redirect_uri)
      }

      if (result) {
        // 短路：直接返回模拟响应，不走网络
        config.adapter = () => Promise.resolve({
          data: result.data,
          status: result.data.code === 0 ? 200 : 400,
          statusText: result.data.code === 0 ? 'OK' : 'Bad Request',
          headers: {},
          config,
          request: {}
        })
        return config
      }
    } catch (e) {
      config.adapter = () => Promise.reject(e)
      return config
    }
  }
  const user = useUserStore()
  if (user.token) config.headers['Authorization'] = `Bearer ${user.token}`

  // CSRF Token（双提交模式）
  const csrf = localStorage.getItem('cs_csrf')
  if (csrf && ['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase())) {
    config.headers['X-CSRF-Token'] = csrf
  }

  // 防重放：写操作带时间戳（后端校验 5 分钟内）
  if (['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase())) {
    config.headers['X-Request-Time'] = Date.now().toString()
  }

  // 防 URL 注入：清理 query 中的危险字符
  if (config.params) {
    Object.keys(config.params).forEach(k => {
      if (typeof config.params[k] === 'string') {
        config.params[k] = config.params[k].replace(/[<>"'%;()&+]/g, '')
      }
    })
  }

  return config
})

// ============ Response 拦截：401/403/500 ============
let isLogoutPrompting = false

service.interceptors.response.use(
  resp => {
    const data = resp.data
    if (data && typeof data.code !== 'undefined' && data.code !== 0 && resp.status < 400) {
      // 业务错误（4xx 由后端约定）
      if (data.code === 401) return handle401()
      ElMessage.error(data.msg || '操作失败')
      return Promise.reject(data)
    }
    return data
  },
  err => {
    const status = err.response?.status
    const data = err.response?.data
    if (status === 401) return handle401()
    if (status === 403) {
      ElMessage.error(data?.msg || '权限不足')
    } else if (status === 429) {
      ElMessage.warning('请求过于频繁，请稍后再试')
    } else if (status >= 500) {
      ElMessage.error('服务器繁忙，请稍后再试')
    } else if (err.code === 'ECONNABORTED') {
      ElMessage.error('请求超时')
    } else {
      ElMessage.error(data?.msg || err.message || '网络异常')
    }
    return Promise.reject(err)
  }
)

function handle401() {
  if (isLogoutPrompting) return Promise.reject(new Error('未登录'))
  isLogoutPrompting = true
  ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
    confirmButtonText: '去登录',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const user = useUserStore()
    user.logout()
    router.push({ name: 'login', query: { next: router.currentRoute.value.name } })
  }).catch(() => {}).finally(() => { isLogoutPrompting = false })
  return Promise.reject(new Error('未登录'))
}

export default service