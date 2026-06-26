import request from './request'

// ============= Auth =============
export const auth = {
  loginByPassword: (username, password) => request.post('/auth/login', { username, password }),
  loginByPhone: (phone, code) => request.post('/auth/login-phone', { phone, code }),
  sendSms: phone => request.post('/auth/sms-code', { phone }),
  register: data => request.post('/auth/register', data),
  adminLogin: data => request.post('/auth/admin/login', data),
  silent: data => request.post('/auth/silent', data),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout')
}

// ============= IM =============
export const im = {
  send: data => request.post('/im/send', data),
  recall: msgId => request.post(`/im/recall/${msgId}`),
  react: (msgId, emoji) => request.post(`/im/react/${msgId}`, { emoji }),
  history: sessionId => request.get(`/im/history/${sessionId}`),
  upload: (formData, onProgress) => request.post('/im/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: e => onProgress?.(Math.round(e.loaded * 100 / e.total))
  })
}

// ============= Robot =============
export const robot = {
  ask: (question, sessionId) => request.post('/robot/ask', { question, sessionId }),
  history: sessionId => request.get(`/robot/history/${sessionId}`)
}

// ============= Agent =============
export const agent = {
  queue: () => request.get('/agent/queue'),
  take: sessionId => request.post(`/agent/take/${sessionId}`),
  sessions: () => request.get('/agent/sessions'),
  stats: () => request.get('/agent/stats')
}

// ============= Admin =============
export const admin = {
  users: params => request.get('/admin/users', { params }),
  sessions: params => request.get('/admin/sessions', { params }),
  forceHangup: (sessionId, reason) => request.post(`/admin/force-hangup/${sessionId}`, { reason }),
  audit: params => request.get('/admin/audit', { params }),
  dashboard: () => request.get('/admin/dashboard'),
  blacklist: data => request.post('/admin/blacklist', data)
}

export default request