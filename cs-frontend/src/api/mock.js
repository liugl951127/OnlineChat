/**
 * 前端 Mock 登录 / OAuth / SMS 模块
 *
 * <p>前端独立运行时（如本地预览 / 演示 / 后端未启），使用浏览器 localStorage 模拟登录态，
 * 不依赖任何后端服务。生产环境设置 VITE_USE_MOCK=false 关闭。
 *
 * <p>支持的 Mock：
 * <ul>
 *   <li>auth.loginByPassword → 任何账号密码直接登录，返回 JWT 假 token</li>
 *   <li>auth.loginByPhone     → 手机号 + 任意 6 位数字验证码登录</li>
 *   <li>auth.sendSms          → 任意手机号返回 debugCode=123456</li>
 *   <li>auth.register         → 用户名写入 localStorage，返回成功</li>
 *   <li>auth.me               → 从 token 解析身份</li>
 *   <li>auth.logout           → 清空 token</li>
 * </ul>
 */

const MOCK_USERS_KEY = 'mock_users'

// 简易 JWT 生成（仅前端解析，不校验签名）
function fakeJwt(payload) {
  const header = { alg: 'none', typ: 'JWT' }
  const encode = obj => btoa(JSON.stringify(obj)).replace(/=+$/, '')
  return [encode(header), encode(payload), 'mock-signature'].join('.')
}

function readUsers() {
  try { return JSON.parse(localStorage.getItem(MOCK_USERS_KEY) || '{}') }
  catch { return {} }
}

function saveUsers(users) {
  localStorage.setItem(MOCK_USERS_KEY, JSON.stringify(users))
}

/** 默认演示账号（首次访问自动注入） */
function ensureSeedUsers() {
  const users = readUsers()
  const seeds = {
    'demo':         { password: 'demo123',         role: 'CUSTOMER', nickname: '演示客户', customerId: 'cust-demo-001' },
    'customer001':  { password: 'pass123',         role: 'CUSTOMER', nickname: '张三', customerId: 'cust-001' },
    'agent001':     { password: 'agent123',        role: 'AGENT',    nickname: '客服小李', customerId: 'agent-001' },
    'admin':        { password: 'admin123',        role: 'ADMIN',    nickname: '管理员', customerId: 'admin-001' },
    '13800138000':  { password: '',                role: 'CUSTOMER', nickname: '13800138000', customerId: 'cust-13800138000' }
  }
  let changed = false
  Object.entries(seeds).forEach(([k, v]) => {
    if (!users[k]) { users[k] = v; changed = true }
  })
  if (changed) saveUsers(users)
  return users
}

/** Mock 登录返回统一格式 */
function ok(data) {
  return Promise.resolve({ data: { code: 0, msg: 'success', data, ts: Date.now() } })
}

function fail(code, msg) {
  return Promise.resolve({ data: { code, msg, data: null, ts: Date.now() } })
}

export const mockAuth = {
  /** 账号密码登录（demo/customer001/agent001/admin） */
  loginByPassword: (username, password) => {
    const users = ensureSeedUsers()
    const u = users[username]
    if (!u) return fail(404, `账号不存在（Mock 提示：demo/demo123, agent001/agent123, admin/admin123）`)
    if (u.password && u.password !== password) return fail(401, '密码错误')

    const token = fakeJwt({
      sub: u.customerId,
      userId: u.customerId,
      role: u.role,
      displayName: u.nickname,
      channel: 'MOCK',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 86400
    })
    const csrf = 'mock-csrf-' + Math.random().toString(36).slice(2, 10)
    return ok({
      token,
      csrf,
      customerId: u.customerId,
      userId: u.customerId,
      nickname: u.nickname,
      displayName: u.nickname,
      role: u.role,
      channel: 'MOCK'
    })
  },

  /** 手机号 + 验证码登录（任意手机号 + 123456） */
  loginByPhone: (phone, code) => {
    if (!/^1[3-9]\d{9}$/.test(phone)) return fail(400, '手机号格式错误')
    if (code !== '123456') return fail(401, '验证码错误（Mock：123456）')
    return mockAuth.loginByPassword(phone, '')
  },

  /** 发送验证码（返回 debugCode=123456） */
  sendSms: (phone) => {
    if (!/^1[3-9]\d{9}$/.test(phone)) return fail(400, '手机号格式错误')
    return ok({ debugCode: '123456', ttl: 300, mock: true })
  },

  /** 注册（任意用户名 + 密码） */
  register: ({ username, password }) => {
    if (!username || !password) return fail(400, '用户名和密码必填')
    if (password.length < 6) return fail(400, '密码至少 6 位')
    const users = ensureSeedUsers()
    if (users[username]) return fail(409, '用户名已存在')
    users[username] = { password, role: 'CUSTOMER', nickname: username, customerId: 'cust-' + Date.now() }
    saveUsers(users)
    return ok({ username, role: 'CUSTOMER' })
  },

  /** v2.2.68: 静默登录 (用 deviceId 作为唯一标识) */
  silent: (deviceId) => {
    if (!deviceId) return fail(401, '无 deviceId')
    // mock 模式下生成一个 fake token (payload 含 deviceId)
    const fakeToken = 'mock.' + btoa(JSON.stringify({
      userId: 'c-' + deviceId.substring(0, 8),
      displayName: '访客',
      role: 'CUSTOMER',
      channel: 'MOCK',
      deviceId
    })) + '.sig'
    return ok({ token: fakeToken, customerId: 'c-' + deviceId.substring(0, 8), nickname: '访客', role: 'CUSTOMER', channel: 'MOCK' })
  },

  /** 取当前用户 */
  me: () => {
    const t = localStorage.getItem('cs_token')
    if (!t) return fail(401, '未登录')
    try {
      const payload = JSON.parse(atob(t.split('.')[1] || ''))
      return ok({
        customerId: payload.userId,
        userId: payload.userId,
        nickname: payload.displayName,
        role: payload.role,
        channel: payload.channel
      })
    } catch {
      return fail(401, 'token 无效')
    }
  },

  /** 登出 */
  logout: () => ok({ success: true }),

  /** OAuth 模拟（GitHub/Google/Wechat 等） */
  oauthAuthorize: (provider, redirectUri) => {
    const token = fakeJwt({
      sub: provider + '-user',
      userId: provider + '-user',
      role: 'CUSTOMER',
      displayName: provider + '用户',
      channel: provider.toUpperCase(),
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 86400
    })
    // 重定向到 callback，带 token
    const url = new URL(redirectUri)
    url.searchParams.set('token', token)
    url.searchParams.set('provider', provider)
    location.href = url.toString()
    return new Promise(() => {})  // 永不 resolve，因为会跳转
  }
}

/** 检测是否启用 Mock：默认开启（环境变量可关闭） */
export function isMockEnabled() {
  // Vite 在编译时把 import.meta.env.VITE_USE_MOCK 内联
  const flag = import.meta.env.VITE_USE_MOCK
  // 默认 true（前端可独立运行）；生产配置 .env.production 设 false
  return flag !== 'false' && flag !== false
}

/** 初始化 Mock（页面加载时调用，注入种子账号） */
export function initMock() {
  if (isMockEnabled()) ensureSeedUsers()
}