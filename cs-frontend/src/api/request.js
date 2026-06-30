import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useErrorStore } from '@/store/error'
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

// ============ v2.3.1: 错误分类映射 ============
//   401 → 登录过期 (弹 toast + 自动 logout, 不强制跳 login)
//   403 → 权限不足
//   404 → 资源不存在
//   429 → 操作太频繁
//   5xx → 服务器繁忙
//   网络异常 → 网络/超时

const ERROR_TITLES = {
  401: '登录已过期',
  403: '权限不足',
  404: '资源不存在',
  429: '操作太频繁',
  500: '服务器异常',
  502: '网关异常',
  503: '服务暂不可用',
  504: '网关超时',
  ECONNABORTED: '请求超时',
  NETWORK: '网络异常'
}

function classifyError(status, data) {
  if (status === 401) return 'warning'   // 登录过期 — warning 不那么刺眼
  if (status === 403) return 'warning'
  if (status === 429) return 'info'      // 限流 — info 温和提示
  if (status === 404) return 'info'
  if (status >= 500) return 'error'
  return 'error'
}

function getTitle(status) {
  return ERROR_TITLES[status] || '请求失败'
}

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
      else if (url === '/auth/register' && method === 'post') result = await mockAuth.register(body)
      else if (url === '/auth/me' && method === 'get') result = await mockAuth.me()
      else if (url === '/auth/logout' && method === 'post') result = await mockAuth.logout()

      if (result) {
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

  const csrf = localStorage.getItem('cs_csrf')
  if (csrf && ['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase())) {
    config.headers['X-CSRF-Token'] = csrf
  }

  if (['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase())) {
    config.headers['X-Request-Time'] = Date.now().toString()
  }

  if (config.params) {
    Object.keys(config.params).forEach(k => {
      if (typeof config.params[k] === 'string') {
        config.params[k] = config.params[k].replace(/[<>"'%;()&+]/g, '')
      }
    })
  }

  return config
})

// ============ Response 拦截：业务错误 + HTTP 错误 → 顶部 banner ============
// v2.3.1: 错误用 errorStore.push 推到顶部 banner (而不是覆盖全屏)
//   401 自动 logout 但不强制跳 login 页 (用户可手动去 /login)
let isLogoutPrompting = false

service.interceptors.response.use(
  resp => {
    const data = resp.data
    if (data && typeof data.code !== 'undefined' && data.code !== 0 && resp.status < 400) {
      // 业务错误（HTTP 2xx 但 code != 0）
      handleBusinessError(data)
      return Promise.reject(data)
    }
    return data
  },
  err => {
    handleHttpError(err)
    return Promise.reject(err)
  }
)

function handleBusinessError(data) {
  // 401 业务错误 = token 无效, 自动 logout 但不强制跳
  if (data.code === 401) {
    return handle401()
  }
  // 其他业务错误: 用 banner 显示 (不弹 toast, 因为 banner 更醒目且可关闭)
  const errorStore = useErrorStore()
  errorStore.push({
    type: classifyError(data.code, data),
    title: getTitle(data.code),
    message: data.msg || '操作失败, 请稍后重试',
    source: 'api'
  })
}

function handleHttpError(err) {
  const status = err.response?.status
  const data = err.response?.data
  const errorStore = useErrorStore()

  if (status === 401) return handle401()

  let type, title, message

  if (status === 403) {
    type = 'warning'
    title = '权限不足'
    message = data?.msg || '您没有执行此操作的权限'
  } else if (status === 429) {
    type = 'info'
    title = '操作太频繁'
    message = data?.msg || '请稍后再试'
  } else if (status === 404) {
    type = 'info'
    title = '资源不存在'
    message = data?.msg || '请求的资源已被删除或不存在'
  } else if (status >= 500) {
    type = 'error'
    title = getTitle(status)
    message = data?.msg || '服务器繁忙, 请稍后重试'
  } else if (err.code === 'ECONNABORTED') {
    type = 'warning'
    title = '请求超时'
    message = '网络响应慢, 请检查连接后重试'
  } else if (!err.response) {
    type = 'error'
    title = '网络异常'
    message = err.message || '无法连接到服务器, 请检查网络'
  } else {
    type = 'error'
    title = getTitle(status)
    message = data?.msg || err.message || '请求失败'
  }

  errorStore.push({ type, title, message, source: 'api' })

  // 关键操作 (写) 额外弹 toast 提醒 — banner 也能看到
  const method = err.config?.method?.toLowerCase()
  if (method && ['post', 'put', 'delete', 'patch'].includes(method)) {
    ElMessage({ type: type === 'info' ? 'info' : type === 'warning' ? 'warning' : 'error', message: `${title}: ${message}`, duration: 4000, grouping: true })
  }
}

function handle401() {
  if (isLogoutPrompting) return Promise.reject(new Error('未登录'))
  isLogoutPrompting = true

  const user = useUserStore()
  const errorStore = useErrorStore()

  // v2.3.1: 不强制跳 login 页面, 而是用 banner 提示用户
  //   - 自动清除本地登录态
  //   - 在 banner 顶部显示 "登录已过期, 请前往登录"
  //   - 提供"去登录"按钮
  user.logout()

  // 把错误加到 banner (sticky = 不自动消失)
  const id = errorStore.push({
    type: 'warning',
    title: '登录已过期',
    message: '请重新登录后继续操作',
    source: 'auth',
    sticky: true
  })

  ElMessage({
    type: 'warning',
    message: '登录已过期, 请前往登录页',
    duration: 4000
  })

  isLogoutPrompting = false

  // 路由保护: 如果当前页需要登录, 自动跳 login (但保留 hash 便于回来)
  const currentRoute = router.currentRoute.value
  if (currentRoute.meta?.role && !currentRoute.meta?.public) {
    router.push({ name: 'login', query: { next: currentRoute.name } })
  }

  return Promise.reject(new Error('未登录'))
}

export default service