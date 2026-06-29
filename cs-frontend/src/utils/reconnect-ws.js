/**
 * v2.3.0 抗刷新断线 - 5 道防线
 *
 * F1: SPA keep-alive (路由切换不卸载组件 - Vue <keep-alive>)
 * F2: localStorage 缓存 session/消息
 * F3: 指数退避重连 WS
 * F4: HTTP offline drain (进站立即拉)
 * F5: IndexedDB 缓存 (SDK 录制用, 在 chat-monitor-sdk 里)
 */

const STORAGE_KEYS = {
  SESSION_ID: 'cs_session_id',
  SESSION_INFO: 'cs_session_info',
  MESSAGES_CACHE: 'cs_messages_cache',
  MONITOR_STATE: 'cs_monitor_state',
  WS_STATE: 'cs_ws_state'
}

export function saveSession(sessionId, info) {
  try {
    if (sessionId != null) localStorage.setItem(STORAGE_KEYS.SESSION_ID, String(sessionId))
    if (info) localStorage.setItem(STORAGE_KEYS.SESSION_INFO, JSON.stringify(info))
  } catch (e) {}
}

export function loadSession() {
  try {
    const sid = localStorage.getItem(STORAGE_KEYS.SESSION_ID)
    const infoStr = localStorage.getItem(STORAGE_KEYS.SESSION_INFO)
    return {
      sessionId: sid ? Number(sid) : null,
      info: infoStr ? JSON.parse(infoStr) : null
    }
  } catch (e) {
    return { sessionId: null, info: null }
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(STORAGE_KEYS.SESSION_ID)
    localStorage.removeItem(STORAGE_KEYS.SESSION_INFO)
    localStorage.removeItem(STORAGE_KEYS.MESSAGES_CACHE)
    localStorage.removeItem(STORAGE_KEYS.MONITOR_STATE)
    localStorage.removeItem(STORAGE_KEYS.WS_STATE)
  } catch (e) {}
}

export function saveMessages(messages) {
  try {
    // 只保留最近 200 条避免爆
    const slice = messages.slice(-200)
    localStorage.setItem(STORAGE_KEYS.MESSAGES_CACHE, JSON.stringify(slice))
  } catch (e) {}
}

export function loadMessages() {
  try {
    const s = localStorage.getItem(STORAGE_KEYS.MESSAGES_CACHE)
    return s ? JSON.parse(s) : []
  } catch (e) { return [] }
}

/**
 * 指数退避重连 WS (1s → 2s → 4s → 8s → 16s → 32s → 60s 上限)
 */
export class ReconnectWS {
  constructor(url, options = {}) {
    this.url = url
    this.options = options
    this.ws = null
    this.retryCount = 0
    this.maxRetry = 10
    this.maxBackoff = 32000 // 32s 上限
    this.heartbeatTimer = null
    this.heartbeatInterval = 20000 // 20s
    this.lastPong = Date.now()
    this.handlers = {}
    this.shouldReconnect = true
  }

  on(event, handler) {
    (this.handlers[event] = this.handlers[event] || []).push(handler)
  }

  emit(event, ...args) {
    (this.handlers[event] || []).forEach(h => {
      try { h(...args) } catch (e) { console.warn('[ReconnectWS] handler', e) }
    })
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }
    this.shouldReconnect = true
    try {
      this.ws = new WebSocket(this.url)
    } catch (e) {
      console.warn('[ReconnectWS] create failed:', e)
      this._scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      console.log('[ReconnectWS] connected')
      this.retryCount = 0
      this.lastPong = Date.now()
      this._startHeartbeat()
      this.emit('open')
    }

    this.ws.onmessage = (ev) => {
      this.lastPong = Date.now()
      try {
        const data = JSON.parse(ev.data)
        this.emit('message', data)
      } catch (e) {
        this.emit('message', ev.data)
      }
    }

    this.ws.onerror = (err) => {
      console.warn('[ReconnectWS] error:', err)
      this.emit('error', err)
    }

    this.ws.onclose = (ev) => {
      console.log('[ReconnectWS] closed code=', ev.code)
      this._stopHeartbeat()
      this.emit('close', ev)
      if (this.shouldReconnect) {
        this._scheduleReconnect()
      }
    }
  }

  _scheduleReconnect() {
    // 1s, 2s, 4s, 8s, 16s, 32s, 32s, ...
    const backoff = Math.min(this.maxBackoff, 1000 * Math.pow(2, this.retryCount))
    this.retryCount++
    console.log(`[ReconnectWS] reconnect in ${backoff}ms (attempt ${this.retryCount})`)
    setTimeout(() => {
      if (this.shouldReconnect) this.connect()
    }, backoff)
  }

  _startHeartbeat() {
    this._stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      // 心跳超时检测: 60s 没收到 pong 视为断
      if (Date.now() - this.lastPong > 60000) {
        console.warn('[ReconnectWS] heartbeat timeout, force reconnect')
        this._forceReconnect()
        return
      }
      // STOMP 帧心跳
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try { this.ws.send('\n') } catch (e) {}
      }
    }, this.heartbeatInterval)
  }

  _stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  _forceReconnect() {
    if (this.ws) {
      try { this.ws.close() } catch (e) {}
      this.ws = null
    }
    this._scheduleReconnect()
  }

  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(typeof data === 'string' ? data : JSON.stringify(data))
      return true
    }
    return false
  }

  disconnect() {
    this.shouldReconnect = false
    this._stopHeartbeat()
    if (this.ws) {
      try { this.ws.close() } catch (e) {}
      this.ws = null
    }
  }
}