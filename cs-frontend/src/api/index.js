import request from './request'

// ============= Auth (v2.3.0 简化) =============
export const auth = {
  /** 统一账号密码登录 (客户/坐席/管理员; 后端按 username 自动判 role) */
  login: (username, password) => request.post('/auth/login', { username, password }),
  /** 简单注册 */
  register: (username, password, nickname) =>
    request.post('/auth/register', { username, password, nickname }),
  /** 修改密码 (登录后) */
  changePassword: (oldPassword, newPassword) =>
    request.post('/auth/password', { oldPassword, newPassword }),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout'),
  refresh: () => request.post('/auth/refresh')
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
  /** 离线消息 (v2.3.0: 不需 sessionId, 用 userId) */
  drainOffline: () => request.get('/im/customer/offline/drain'),
  offlineSize: () => request.get('/im/customer/offline/size'),
  /** WS 在线状态 */
  wsStatus: () => request.get('/im/customer/ws/status'),
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
  setFrameUrl: (messageId, videoUrl) => request.post(`/im/replay/message/${messageId}/frame-url`, { videoUrl }),
  // v2.2.78: 帧上传 + 合成
  capture: data => request.post('/im/replay/capture', data),
  captureBatch: data => request.post('/im/replay/capture-batch', data),
  finish: sessionId => request.post(`/im/replay/${sessionId}/finish`),
  job: sessionId => request.get(`/im/replay/${sessionId}/job`),
  frames: sessionId => request.get(`/im/replay/${sessionId}/frames`),
  meta: sessionId => request.get(`/im/replay/${sessionId}/meta`)
}

// ============= KYC（v2.0.0） =============
export const kyc = {
  status: () => request.get('/im/kyc/status'),
  my: () => request.get('/im/kyc/my'),
  create: () => request.post('/im/kyc/create'),
  uploadIdcard: data => request.post('/im/kyc/upload-idcard', data),
  liveness: data => request.post('/im/kyc/liveness', data),
  faceMatch: () => request.post('/im/kyc/face-match'),
  submitVideo: data => request.post('/im/kyc/submit-video', data),
  submitAudit: () => request.post('/im/kyc/submit-audit'),
  bindCard: data => request.post('/im/kyc/bind-card', data),
  statements: () => request.get('/im/kyc/statements'),
  bankCards: () => request.get('/im/kyc/bank-cards'),
  auditQueue: () => request.get('/im/kyc/audit/queue'),
  auditDetail: no => request.get(`/im/kyc/audit/${no}`),
  approve: (no, data) => request.post(`/im/kyc/audit/${no}/approve`, data),
  reject: (no, reason) => request.post(`/im/kyc/audit/${no}/reject`, { reason })
}

// ============= 微信小程序登录（v2.0.0） =============
export const wxMini = {
  login: (jsCode, encryptedData, iv) =>
    request.post('/auth/wx-mini/login', { jsCode, encryptedData, iv })
}

// ============= AI 助手（v2.1.0 新增） =============
export const ai = {
  /** 即时生成推荐（同步） */
  suggest: data => request.post('/im/ai/suggest', data),
  /** 异步生成 + WS 推送 */
  suggestAsync: data => request.post('/im/ai/suggest/async', data),
  /** 坐席反馈 */
  feedback: data => request.post('/im/ai/feedback', data),
  /** 坐席历史推荐 */
  recent: (agentUsername, limit = 20) =>
    request.get('/im/ai/recent', { params: { agentUsername, limit } }),
  /** 知识库浏览 */
  knowledge: (category, limit = 20) =>
    request.get('/im/ai/knowledge', { params: { category, limit } }),
  /** 知识库检索 */
  knowledgeSearch: (q, topK = 5) =>
    request.get('/im/ai/knowledge/search', { params: { q, topK } }),
  /** 知识库统计 */
  knowledgeStats: () => request.get('/im/ai/knowledge/stats')
}

// ============= 多媒体：WebRTC 屏幕共享 + 语音（v2.1.0） =============
export const media = {
  initiateShare: data => request.post('/im/media/screen-share/initiate', data),
  acceptShare: (shareId, sdpAnswer) =>
    request.post(`/im/media/screen-share/${shareId}/accept`, { sdpAnswer }),
  rejectShare: shareId =>
    request.post(`/im/media/screen-share/${shareId}/reject`),
  endShare: shareId =>
    request.post(`/im/media/screen-share/${shareId}/end`),
  relayIce: (shareId, from, candidate) =>
    request.post(`/im/media/screen-share/${shareId}/ice`, { from, candidate }),
  uploadVoice: data => request.post('/im/media/voice/upload', data),
  listVoice: sessionId =>
    request.get('/im/media/voice/list', { params: { sessionId } })
}

// ============= 微信公众号 H5（v2.0.0） =============
export const wxOaH5 = {
  // 直接跳转到 /auth/wx-oa/h5-entry 即可
}

// ============= Admin =============
export const offline = {
  list: userId => request.get('/message/offline/list', { params: { userId } }),
  drain: userId => request.post('/message/offline/drain', { userId }),
  peek: (userId, limit = 20) => request.get('/message/offline/peek', { params: { userId, limit } }),
  size: userId => request.get('/message/offline/size', { params: { userId } })
}

export const admin = {
  users: params => request.get('/admin/users', { params }),
  sessions: params => request.get('/admin/sessions', { params }),
  forceHangup: (sessionId, reason) => request.post(`/admin/force-hangup/${sessionId}`, { reason }),
  audit: params => request.get('/admin/audit', { params }),
  dashboard: () => request.get('/im/stats/dashboard/all'),
  blacklist: data => request.post('/admin/blacklist', data)
}

export default request

// ============= v2.3.0 推链接 =============
export const link = {
  /** 坐席推链接 */
  push: (sessionId, targetUrl) => request.post('/im/agent/link', { sessionId, targetUrl }),
  /** 客户打开短链 (返回 JSON 含 targetUrl) */
  open: token => request.get(`/im/link/${token}`)
}
