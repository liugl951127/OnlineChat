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
  logout: () => request.post('/auth/logout'),
  githubUrl: redirect => request.get('/auth/github/authorize', { params: { redirect_uri: redirect } }),
  googleUrl: redirect => request.get('/auth/google/authorize', { params: { redirect_uri: redirect } })
}

// ============= IM 实时聊天（v1.9.0 HTTP 实时 + 离线） =============
export const im = {
  /** 发送消息 */
  send: data => request.post('/im/message/send', data),
  /** 实时拉取（长轮询） */
  poll: (sessionId, lastId = 0) => request.get('/im/message/poll', { params: { sessionId, lastId } }),
  /** 撤回 */
  recall: msgId => request.post(`/im/message/${msgId}/recall`),
  /** 历史消息 */
  history: (sessionId, limit = 100) => request.get(`/im/message/history/${sessionId}`, { params: { limit } }),
  /** 视频回溯 */
  replay: sessionId => request.get(`/im/message/replay/${sessionId}`),
  /** 会话相关 */
  activeSession: () => request.get('/im/customer/session/active'),
  transferToAgent: () => request.post('/im/customer/session/transfer-to-agent'),
  hangup: () => request.post('/im/customer/session/hangup'),
  mySessions: () => request.get('/im/customer/sessions'),
  /** 客户发消息（含机器人应答） */
  chat: data => request.post('/im/customer/chat', data),
  /** 离线消息 */
  drainOffline: sessionId => request.get('/im/customer/offline/drain', { params: { sessionId } }),
  offlineSize: sessionId => request.get('/im/customer/offline/size', { params: { sessionId } }),
  /** 视频回溯 */
  replayByCustomer: sessionId => request.get(`/im/customer/replay/${sessionId}`)
}

// ============= Robot =============
export const robot = {
  ask: (text, customerId) => request.post('/robot/chat', { text, customerId }),
  greeting: () => request.get('/robot/greeting')
}

// ============= Agent =============
export const agent = {
  queue: () => request.get('/im/agent/queue'),
  accept: sessionId => request.post('/im/agent/accept', { sessionId }),
  hangup: sessionId => request.post('/im/agent/hangup', { sessionId }),
  mySessions: () => request.get('/im/agent/sessions'),
  stats: () => request.get('/im/agent/stats')
}

// ============= Product / Financial =============
export const product = {
  list: (type) => request.get('/product/list', { params: { type } }),
  detail: (code) => request.get(`/product/${code}`)
}

export const risk = {
  assess: (data) => request.post('/risk/assess', data),
  latest: (customerId) => request.get('/risk/latest', { params: { customerId } })
}

export const order = {
  create: data => request.post('/order/create', data),
  assess: orderNo => request.post(`/order/${orderNo}/assess`),
  compliance: orderNo => request.post(`/order/${orderNo}/compliance`),
  pay: (orderNo, method) => request.post(`/order/${orderNo}/pay`, null, { params: { method } }),
  oneClickBuy: data => request.post('/order/one-click-buy', data),
  redeem: orderNo => request.post(`/order/${orderNo}/redeem`),
  get: orderNo => request.get(`/order/${orderNo}`),
  list: customerId => request.get('/order/list', { params: { customerId } }),
  holdings: customerId => request.get('/order/holdings', { params: { customerId } })
}

// ============= Ticket 工单（v1.9.0 新增） =============
export const ticket = {
  create: data => request.post('/im/ticket/create', data),
  detail: ticketNo => request.get(`/im/ticket/${ticketNo}`),
  list: customerId => request.get('/im/ticket/list', { params: { customerId } }),
  queue: () => request.get('/im/ticket/queue'),
  mine: () => request.get('/im/ticket/mine'),
  assign: ticketNo => request.post(`/im/ticket/${ticketNo}/assign`),
  start: ticketNo => request.post(`/im/ticket/${ticketNo}/start`),
  resolve: ticketNo => request.post(`/im/ticket/${ticketNo}/resolve`),
  close: ticketNo => request.post(`/im/ticket/${ticketNo}/close`),
  cancel: (ticketNo, reason) => request.post(`/im/ticket/${ticketNo}/cancel`, { reason }),
  reply: (ticketNo, data) => request.post(`/im/ticket/${ticketNo}/reply`, data)
}

// ============= FAQ 知识库（v1.9.0 新增） =============
export const faq = {
  search: keyword => request.get('/im/faq/search', { params: { keyword } }),
  top: () => request.get('/im/faq/top'),
  byCategory: categoryId => request.get(`/im/faq/category/${categoryId}`),
  topCategories: () => request.get('/im/faq/category/top'),
  subCategories: parentId => request.get(`/im/faq/category/${parentId}/children`),
  view: id => request.post(`/im/faq/${id}/view`),
  create: data => request.post('/im/faq/create', data)
}

// ============= Video Replay 视频回溯（v1.9.0 新增） =============
export const replay = {
  get: sessionId => request.get(`/im/replay/${sessionId}`),
  setVideoUrl: (sessionId, videoUrl) => request.post(`/im/replay/${sessionId}/video-url`, { videoUrl }),
  setFrameUrl: (messageId, videoUrl) => request.post(`/im/replay/message/${messageId}/frame-url`, { videoUrl })
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