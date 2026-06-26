import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

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
  baseURL: '/api',
  timeout: 15000,
  withCredentials: false
})

// ============ Request 拦截：Token + CSRF + 时间戳防重放 ============
service.interceptors.request.use(config => {
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