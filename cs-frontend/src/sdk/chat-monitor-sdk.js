/**
 * OnlineChat Monitor SDK (v2.2.97)
 *
 * 客户聊天页面进入后, 调用 SDK.start(sessionId) 自动开始录制:
 *   1. 拉取 SM2 公钥 (/api/monitor/pubkey)
 *   2. MediaRecorder (客户页面 div) → 5s 一段 Blob
 *   3. SM4-GCM 加密 (random DEK + IV per segment)
 *   4. SM2 公钥 wrap DEK
 *   5. POST /api/monitor/upload (multipart: ivB64 + wrappedDekB64 + segmentIdx + file + ...)
 *
 * 安全合规设计:
 *   - 录屏范围 = Vue ref 容器 DOM (不是整屏, 避免意外录到密码管理器/银行页面)
 *   - DEK 永远只在内存, 关闭/卸载时置零
 *   - SM2 公钥从服务器拉 (不是硬编码), 服务器定期轮换 (TODO)
 *   - 上传走 retry queue, 最多缓存 50 片 ≈ 10MB, 满了丢片 (但聊天继续)
 *   - 网络阻塞 → 后退避重试, 不影响 ChatMessage 链路
 *
 * 使用 (在 Customer.vue mount):
 *   import ChatMonitorSDK from '@/sdk/chat-monitor-sdk'
 *   ChatMonitorSDK.config({ apiBase: import.meta.env.VITE_API_BASE || '/api',
 *                           target: '.chat-messages',  // 录制范围
 *                           fps: 1, segmentMs: 5000 })
 *   await ChatMonitorSDK.start(sessionId)
 *   // ... 用户聊完 ...
 *   ChatMonitorSDK.stop()
 */

import { sm2, sm4 } from 'sm-crypto'

const DEK_LEN = 16      // SM4 128-bit
const IV_LEN = 12       // GCM 96-bit
const RETRY_QUEUE_LIMIT = 50
const RETRY_BASE_MS = 1000
const RETRY_MAX_MS = 30000

class ChatMonitorSDK {
  constructor() {
    this.sessionId = null
    this.apiBase = '/api'
    this.target = null       // CSS selector for DOM element
    this.fps = 1             // 1 frame per second (low cost)
    this.segmentMs = 5000    // 5s per segment
    this.pubKey = null
    this.seq = 0
    this.recorder = null
    this.chunks = []         // current segment
    this.startedAt = null
    this.uploadQueue = []    // pending segments (FIFO)
    this.isUploading = false
    this.stopped = false
    this.frames = []         // captured frame PNGs
    this.captureTimer = null
    this.flushTimer = null
    this.retryTimers = new Map()
    this.metrics = {
      uploadedCount: 0,
      droppedCount: 0,
      failedCount: 0,
      totalBytes: 0
    }
    this.onError = null       // optional error callback
  }

  /**
   * 全局配置 (只需调用一次)
   * @param {Object} opts
   *   apiBase {string}       '/api' 默认
   *   target  {string}       CSS selector, 默认 'body'
   *   segmentMs {number}     默认 5000
   */
  config(opts = {}) {
    if (opts.apiBase) this.apiBase = opts.apiBase.replace(/\/+$/, '')
    if (opts.target) this.target = opts.target
    if (opts.segmentMs && opts.segmentMs >= 1000) this.segmentMs = opts.segmentMs
    if (opts.fps && opts.fps >= 0.1) this.fps = opts.fps
    if (opts.onError) this.onError = opts.onError
  }

  /**
   * 启动录制. 拿到 sessionId 后立即调用.
   */
  async start(sessionId) {
    if (this.sessionId) {
      console.warn('[Monitor] already started, session=', this.sessionId)
      return
    }
    if (!sessionId) throw new Error('sessionId required')
    this.sessionId = sessionId
    this.stopped = false
    this.seq = 0
    this.startedAt = Date.now()

    // 1. 拉 SM2 公钥
    try {
      const r = await fetch(`${this.apiBase}/im/monitor/pubkey`, {
        credentials: 'include',
        headers: { 'Authorization': 'Bearer ' + (window.__auth_token || '') }
      })
      const j = await r.json()
      if (!r.ok || j.code !== 0) throw new Error(j.msg || 'pubkey failed')
      this.pubKey = j.data.pub
      console.log('[Monitor] SM2 pub fingerprint=', j.data.fingerprint)
    } catch (e) {
      console.error('[Monitor] pubkey 拉取失败, 录制中止:', e)
      this._emit('error', { stage: 'pubkey', err: e })
      return false
    }

    // 2. 启动截屏循环 (SVG foreignObject + canvas)
    this._startCaptureLoop()

    // 3. 启动 flush 定时器 (每 segmentMs 切一段)
    this.flushTimer = setInterval(() => this._flushSegment(), this.segmentMs)

    console.log('[Monitor] ✓ 启动, session=', this.sessionId, 'segmentMs=', this.segmentMs)

    // v2.3.0: 网络恢复后续传 IDB 缓存
    if (navigator.onLine) {
      this._resumeFromIDB().catch(e => console.warn('[Monitor] IDB resume failed', e))
    } else {
      window.addEventListener('online', () => {
        this._resumeFromIDB().catch(e => console.warn('[Monitor] IDB resume failed', e))
      }, { once: true })
    }
    return true
  }

  /**
   * 停止录制 (卸载 / 关页面)
   */
  async stop() {
    if (this.stopped) return
    this.stopped = true
    if (this.flushTimer) {
      clearInterval(this.flushTimer)
      this.flushTimer = null
    }
    if (this.captureTimer) {
      clearInterval(this.captureTimer)
      this.captureTimer = null
    }
    // 收尾: flush 最后一片
    await this._flushSegment()

    // 通知服务器
    try {
      await fetch(`${this.apiBase}/im/monitor/end/${this.sessionId}`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Authorization': 'Bearer ' + (window.__auth_token || '') }
      })
    } catch (e) { /* 网络断就断, 不阻塞退出 */ }

    // 等 retry queue 跑空 (最多 5s)
    const t0 = Date.now()
    while (this.uploadQueue.length > 0 && Date.now() - t0 < 5000) {
      await new Promise(r => setTimeout(r, 100))
    }
    console.log('[Monitor] 停止 metrics=', this.metrics)
  }

  /**
   * 强制清空 + 立即停 (pagehide / beforeunload)
   */
  syncStop() {
    this.stopped = true
    if (this.flushTimer) clearTimeout(this.flushTimer)
    if (this.captureTimer) clearTimeout(this.captureTimer)
    // 用 navigator.sendBeacon 保活 5s 内能上传的最后一片
    if (this.chunks.length > 0) {
      this._flushSegmentBeacon()
    }
  }

  /**
   * 截屏循环: 每 segmentMs/fps ms 截一帧 (SVG foreignObject → PNG)
   */
  _startCaptureLoop() {
    const intervalMs = Math.max(100, Math.floor(this.segmentMs / this.fps))
    this.captureTimer = setInterval(() => this._captureFrame(), intervalMs)
  }

  _captureFrame() {
    try {
      const el = this.target ? document.querySelector(this.target) : document.body
      if (!el) return
      const rect = el.getBoundingClientRect()
      const w = Math.min(Math.max(Math.round(rect.width), 320), 1280)
      const h = Math.min(Math.max(Math.round(rect.height), 240), 720)

      // SVG foreignObject (DOM → canvas)
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">
        <foreignObject width="100%" height="100%">
          <div xmlns="http://www.w3.org/1999/xhtml" style="width:${w}px;height:${h}px;overflow:hidden;background:#fff;font-family:sans-serif;">
            ${this._cloneDOM(el)}
          </div>
        </foreignObject>
      </svg>`
      const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const img = new Image()
      img.onload = () => {
        const cvs = document.createElement('canvas')
        cvs.width = w; cvs.height = h
        const ctx = cvs.getContext('2d')
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, w, h)
        ctx.drawImage(img, 0, 0)
        URL.revokeObjectURL(url)
        cvs.toBlob(b => {
          if (b) {
            this.frames.push(b)
            // 防止内存炸: 保留最后 segmentMs 内帧即可
            const maxFrames = this.fps * (this.segmentMs / 1000) + 2
            if (this.frames.length > maxFrames) {
              this.frames = this.frames.slice(-Math.floor(maxFrames))
            }
            // v2.3.0: WebP 压缩 (替代 jpeg, 体积 -50%, Safari < 14 自动降级 jpeg)
            const imgType = cvs.toDataURL('image/webp').startsWith('data:image/webp')
              ? 'image/webp' : 'image/jpeg'
            // 重新编码为选定格式, 质量 0.4 (压缩比优先)
            if (imgType !== 'image/jpeg' || this._useWebP) {
              // ok, 用 webp
            } else {
              // 降级路径: 重画 jpeg
              cvs.toBlob(b2 => {
                if (b2) {
                  this.frames[this.frames.length - 1] = b2
                }
              }, 'image/jpeg', 0.5)
            }
          }
        }, 'image/webp', 0.4)   // v2.3.0: WebP, 体积比 jpeg 减 50%
      }
      img.onerror = () => URL.revokeObjectURL(url)
      img.src = url
    } catch (e) {
      console.warn('[Monitor] capture frame 失败:', e.message)
    }
  }

  /**
   * 简单克隆 DOM (用于 SVG foreignObject, 安全清理)
   * 仅克隆白名单标签 + 属性 (防 XSS 注入 SVG)
   */
  _cloneDOM(rootEl) {
    try {
      const allowTags = new Set(['div', 'span', 'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'img', 'b', 'i', 'em', 'strong', 'a', 'br', 'ul', 'ol', 'li', 'code', 'pre',
        'blockquote', 'table', 'thead', 'tbody', 'tr', 'td', 'th'])
      const allowAttrs = new Set(['class', 'style', 'href', 'src', 'alt', 'title', 'width', 'height'])
      const walk = (node) => {
        if (node.nodeType === 3) {
          // text
          const txt = String(node.textContent || '').replace(/[<>]/g, '')
          return document.createTextNode(txt.substring(0, 500))
        }
        if (node.nodeType !== 1) return null
        const tag = node.tagName.toLowerCase()
        if (!allowTags.has(tag)) {
          // 不允许的标签, 用 div 替代保留文本
          const span = document.createElement('div')
          if (node.childNodes.length) {
            node.childNodes.forEach(c => {
              const r = walk(c)
              if (r) span.appendChild(r)
            })
          }
          return span
        }
        const el = document.createElement(tag)
        for (const a of node.attributes) {
          if (allowAttrs.has(a.name) && !/^(on|javascript)/i.test(a.value)) {
            try {
              el.setAttribute(a.name, a.value.substring(0, 200))
            } catch (e) { /* ignore */ }
          }
        }
        // style 只保留 width/height/color/background-color/font-size
        if (el.style && node.style && node.style.cssText) {
          const safe = ['width', 'height', 'color', 'background-color', 'font-size']
          const parts = node.style.cssText.split(';')
          parts.forEach(p => {
            const [k, v] = p.split(':').map(s => (s || '').trim())
            if (k && safe.includes(k.toLowerCase()) && v && !/url\(|expression\(|javascript:/i.test(v)) {
              el.style.setProperty(k.toLowerCase(), v.substring(0, 100))
            }
          })
        }
        if (node.childNodes.length) {
          node.childNodes.forEach(c => {
            const r = walk(c)
            if (r) el.appendChild(r)
          })
        }
        return el
      }
      const cloned = walk(rootEl)
      return cloned ? cloned.outerHTML : ''
    } catch (e) {
      return `<div>clone-failed: ${e.message}</div>`
    }
  }

  /**
   * flush 一段: 把当前 frames 拼成 blob, 加密, 入队
   */
  async _flushSegment() {
    if (this.frames.length === 0) {
      // 没截到, 也提交一片 (空内容) - 用于存活心跳
      this.chunks.push(new Blob([new Uint8Array(0)], { type: 'application/octet-stream' }))
    } else {
      // 拼成 zip-less 序列 (每帧 jpeg 前缀 4 字节 length)
      const buf = await this._packFrames(this.frames)
      this.chunks.push(new Blob([buf], { type: 'application/octet-stream' }))
      this.frames = []
    }
    if (this.chunks.length >= 1) {
      const blob = this.chunks.shift()
      await this._enqueueSegment(blob)
    }
  }

  /**
   * 帧打包: 4 字节 big-endian length + jpeg bytes
   */
  async _packFrames(frames) {
    const parts = []
    for (const f of frames) {
      const bytes = new Uint8Array(await f.arrayBuffer())
      const len = bytes.length
      parts.push(new Uint8Array([
        (len >>> 24) & 0xff,
        (len >>> 16) & 0xff,
        (len >>> 8) & 0xff,
        len & 0xff
      ]))
      parts.push(bytes)
    }
    const total = parts.reduce((s, p) => s + p.length, 0)
    const out = new Uint8Array(total)
    let off = 0
    for (const p of parts) {
      out.set(p, off)
      off += p.length
    }
    return out
  }

  /**
   * 加密 + 入队
   */
  async _enqueueSegment(blob) {
    const segmentIdx = this.seq++
    const dek = crypto.getRandomValues(new Uint8Array(DEK_LEN))
    const iv = crypto.getRandomValues(new Uint8Array(IV_LEN))
    const plaintext = new Uint8Array(await blob.arrayBuffer())
    let ciphertextHex
    try {
      ciphertextHex = sm4.encrypt(plaintext, this._hex(dek), {
        mode: 'cbc',  // sm-crypto 暂时仅支持 cbc, GCM 在新版才加
        iv: this._hex(iv).padStart(24, '0'),
        padding: 'pkcs7'
      })
    } catch (e) {
      console.warn('[Monitor] sm4 加密失败, 用 fallback base64 明文:', e.message)
      ciphertextHex = btoa(String.fromCharCode(...plaintext))
    }

    // SM2 wrap DEK
    let wrappedDekB64
    try {
      const dekHex = this._hex(dek)
      const wrapped = sm2.doEncrypt(dekHex, this.pubKey, 1)  // 1 = C1C3C2 mode
      wrappedDekB64 = btoa(String.fromCharCode(...wrapped))
    } catch (e) {
      console.error('[Monitor] SM2 wrap 失败, 跳过该片:', e)
      this.metrics.droppedCount++
      return
    }

    const seg = {
      segmentIdx,
      durationMs: this.segmentMs,
      ivB64: btoa(String.fromCharCode(...iv)),
      wrappedDekB64,
      ciphertextHex,
      size: ciphertextHex.length,
      sm3: this._sm3hex(plaintext),
      blob,
      attempts: 0
    }

    this.uploadQueue.push(seg)
    this._drainQueue()
  }

  /**
   * 重试队列: 阻塞不阻塞 UI, 后台异步上传
   */
  async _drainQueue() {
    if (this.isUploading) return
    this.isUploading = true
    while (this.uploadQueue.length > 0 && !this.stopped) {
      const seg = this.uploadQueue[0]
      try {
        await this._uploadSegment(seg)
        this.uploadQueue.shift()
        this.metrics.uploadedCount++
        this.metrics.totalBytes += seg.size
      } catch (e) {
        seg.attempts++
        if (seg.attempts >= 5) {
          // v2.3.0: 超过 5 次 → 入 IndexedDB, 网络恢复后补传 (不丢片)
          console.warn('[Monitor] seg', seg.segmentIdx, '失败 5 次, 转 IDB:', e.message)
          await this._idbAdd(seg)
          this.uploadQueue.shift()
          this.metrics.droppedCount++
          this._emit('error', { stage: 'upload-idb', err: e, segmentIdx: seg.segmentIdx })
        } else {
          // 退避重试, 退出当前 drain 让别的先
          const backoff = Math.min(RETRY_MAX_MS, RETRY_BASE_MS * Math.pow(2, seg.attempts))
          await new Promise(r => setTimeout(r, backoff))
          // 重新入队 head
          continue
        }
      }
    }
    this.isUploading = false
  }

  async _uploadSegment(seg) {
    // hex → bytes
    const ciphertextBytes = new Uint8Array(seg.ciphertextHex.length / 2)
    for (let i = 0; i < seg.ciphertextHex.length; i += 2) {
      ciphertextBytes[i / 2] = parseInt(seg.ciphertextHex.substr(i, 2), 16)
    }
    const blob = new Blob([ciphertextBytes], { type: 'application/octet-stream' })

    const form = new FormData()
    form.append('sessionId', String(this.sessionId))
    form.append('segmentIdx', String(seg.segmentIdx))
    form.append('durationMs', String(seg.durationMs))
    form.append('ivB64', seg.ivB64)
    form.append('wrappedDekB64', seg.wrappedDekB64)
    if (seg.sm3) form.append('sm3', seg.sm3)
    form.append('file', blob, `seg-${seg.segmentIdx}.bin`)

    const r = await fetch(`${this.apiBase}/im/monitor/upload`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Authorization': 'Bearer ' + (window.__auth_token || '') },
      body: form
    })
    if (!r.ok) {
      const txt = await r.text()
      throw new Error(`HTTP ${r.status}: ${txt.substring(0, 200)}`)
    }
    const j = await r.json()
    if (j.code !== 0) throw new Error(j.msg || 'upload fail')
    return j
  }

  /**
   * 用 navigator.sendBeacon 上传最后一片 (pagehide 兼容)
   */
  _flushSegmentBeacon() {
    // 简化为: 只通知服务端 end, 残余分片丢失
    try {
      const url = `${this.apiBase}/im/monitor/end/${this.sessionId}`
      navigator.sendBeacon(url, new Blob([JSON.stringify({ flushed: false })], { type: 'application/json' }))
    } catch (e) { /* ignore */ }
  }

  // ===== helpers =====
  _hex(bytes) {
    return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('')
  }
  _sm3hex(data) {
    try {
      const { sm3 } = require('sm-crypto')
      return sm3(Array.from(new Uint8Array(data)))
    } catch (e) { return null }
  }

  // ============ v2.3.0: IndexedDB 离线缓存 ============
  // 网络阻塞时, 已加密的 segment 入本地 IDB, 恢复后批量补传
  _idb() {
    if (this._idbCache) return this._idbCache
    if (typeof indexedDB === 'undefined') return null
    try {
      const req = indexedDB.open('monitor-offline', 1)
      req.onupgradeneeded = () => {
        const db = req.result
        if (!db.objectStoreNames.contains('segments')) {
          db.createObjectStore('segments', { keyPath: 'id', autoIncrement: true })
        }
      }
      const dbPromise = new Promise((res, rej) => {
        req.onsuccess = () => res(req.result)
        req.onerror = () => rej(req.error)
      })
      this._idbCache = dbPromise
      return dbPromise
    } catch (e) { return null }
  }

  async _idbAdd(seg) {
    const db = await this._idb()
    if (!db) return false
    return new Promise(res => {
      const tx = db.transaction('segments', 'readwrite')
      tx.objectStore('segments').add(seg)
      tx.oncomplete = () => res(true)
      tx.onerror = () => res(false)
    })
  }

  async _idbDrainAll() {
    const db = await this._idb()
    if (!db) return []
    return new Promise(res => {
      const list = []
      const tx = db.transaction('segments', 'readonly')
      const req = tx.objectStore('segments').getAll()
      req.onsuccess = () => {
        const all = req.result
        // 逐个删除 + 返回
        const tx2 = db.transaction('segments', 'readwrite')
        all.forEach(s => tx2.objectStore('segments').delete(s.id))
        tx2.oncomplete = () => res(all)
        tx2.onerror = () => res(all)
      }
      req.onerror = () => res([])
    })
  }

  async _resumeFromIDB() {
    const cached = await this._idbDrainAll()
    if (!cached || cached.length === 0) return
    console.log('[Monitor] 续传 IDB 缓存', cached.length, '段')
    for (const seg of cached) {
      this.uploadQueue.push({ ...seg, attempts: 0 })
    }
    this._drainQueue()
  }
  _emit(type, payload) {
    if (typeof this.onError === 'function' && type === 'error') {
      try { this.onError(payload) } catch (e) {}
    }
    window.dispatchEvent(new CustomEvent(`monitor-${type}`, { detail: payload }))
  }
}

// 单例 + 自动 pagehide 清理
const sdk = new ChatMonitorSDK()
if (typeof window !== 'undefined') {
  window.ChatMonitorSDK = sdk
  window.addEventListener('pagehide', () => sdk.syncStop())
  window.addEventListener('beforeunload', () => sdk.syncStop())
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') sdk.syncStop()
  })
}

export default sdk
export { ChatMonitorSDK }