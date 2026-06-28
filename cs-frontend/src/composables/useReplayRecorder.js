import { ref, onUnmounted } from 'vue'
import { replay } from '@/api'

/**
 * 视频回溯 Recorder (v2.2.78)
 *
 * 用法:
 *   const recorder = useReplayRecorder({ sessionId, intervalMs: 5000, autoStart: true })
 *
 * 自动:
 *   - 每 5 秒对 targetDom (默认 document.body) 截一次图
 *   - 上传到 /im/replay/capture
 *   - 收集交互事件 (click/scroll/input/typing) 作为 INTERACTION 帧
 *   - 提供 stop() / finish() 方法, 触发服务端 ffmpeg 合成
 *
 * 截图实现: 用 SVG foreignObject 包装 DOM + Canvas drawImage
 *           (无需 html2canvas, 纯浏览器 API)
 */
export function useReplayRecorder(options = {}) {
  const {
    sessionId = null,
    targetDom = null,
    intervalMs = 5000,
    autoStart = false,
    uploadedBy = 'customer',
    width = 1280,
    height = 720,
    quality = 0.7,
    onFrame = null,
    onError = null
  } = options

  const isRecording = ref(false)
  const frameCount = ref(0)
  const lastFrameAt = ref(null)
  const sessionInfo = ref({ status: 'IDLE', frameCount: 0 })
  const jobInfo = ref(null)

  let timer = null
  let interactionEvents = []
  let eventListeners = []

  /**
   * 截取 targetDom 的快照 (SVG foreignObject + canvas)
   * 返回 base64 PNG
   */
  async function captureScreenshot() {
    const dom = targetDom || document.body
    const rect = dom.getBoundingClientRect()

    // 克隆 DOM + 注入 SVG foreignObject
    const clone = dom.cloneNode(true)
    clone.style.position = 'absolute'
    clone.style.left = '-99999px'
    clone.style.top = '0'
    clone.style.width = `${rect.width}px`
    clone.style.background = getComputedStyle(dom).background || '#fff'
    document.body.appendChild(clone)

    const xml = new XMLSerializer().serializeToString(clone)
    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
        <foreignObject width="100%" height="100%">
          <div xmlns="http://www.w3.org/1999/xhtml" style="font-family:sans-serif;font-size:14px;color:#333;">
            ${xml}
          </div>
        </foreignObject>
      </svg>
    `

    const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' })
    const url = URL.createObjectURL(blob)

    return new Promise((resolve) => {
      const img = new Image()
      img.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, width, height)
        // 等比缩放
        const scale = Math.min(width / img.width, height / img.height)
        const w = img.width * scale
        const h = img.height * scale
        ctx.drawImage(img, (width - w) / 2, (height - h) / 2, w, h)
        // 顶部时间戳
        ctx.fillStyle = 'rgba(0,0,0,0.7)'
        ctx.fillRect(10, 10, 200, 32)
        ctx.fillStyle = '#fff'
        ctx.font = '14px monospace'
        ctx.fillText(new Date().toLocaleTimeString(), 18, 32)
        // 底部事件数
        if (interactionEvents.length) {
          ctx.fillStyle = 'rgba(0,0,0,0.7)'
          ctx.fillRect(10, height - 32, 280, 22)
          ctx.fillStyle = '#0f0'
          ctx.font = '12px monospace'
          ctx.fillText(`events: ${interactionEvents.length}`, 18, height - 16)
        }
        const dataUrl = canvas.toDataURL('image/png', quality)
        URL.revokeObjectURL(url)
        document.body.removeChild(clone)
        resolve(dataUrl)
      }
      img.onerror = () => {
        URL.revokeObjectURL(url)
        document.body.removeChild(clone)
        resolve(null)
      }
      img.src = url
    })
  }

  /**
   * 上传一帧到服务端
   */
  async function uploadFrame(payload) {
    if (!sessionId) return  // 未设置 sessionId, 跳过
    try {
      const res = await replay.capture({
        sessionId: sessionId,
        ...payload,
        uploadedBy
      })
      if (res.code === 0) {
        frameCount.value = res.data.frameCount || frameCount.value + 1
        sessionInfo.value = res.data
        if (onFrame) onFrame(res.data)
      } else if (onError) {
        onError(new Error(res.message))
      }
    } catch (e) {
      console.warn('[Replay] 上传帧失败:', e)
      if (onError) onError(e)
    }
  }

  /**
   * 一帧截图+上传
   */
  async function tick() {
    if (!isRecording.value) return
    try {
      const img = await captureScreenshot()
      if (img) {
        const meta = {
          scrollY: window.scrollY,
          scrollX: window.scrollX,
          url: location.pathname,
          recentEvents: interactionEvents.slice(-10)
        }
        await uploadFrame({
          frameKind: 'SCREENSHOT',
          imageData: img,
          width,
          height,
          metadata: JSON.stringify(meta)
        })
      }
    } catch (e) {
      console.warn('[Replay] tick 异常:', e)
    } finally {
      lastFrameAt.value = Date.now()
      // 清理事件队列 (已经上传到 metadata)
      interactionEvents = []
    }
  }

  function recordInteraction(type, data) {
    interactionEvents.push({
      type,
      ts: Date.now(),
      ...data
    })
    // 单次 batch 上限
    if (interactionEvents.length > 50) interactionEvents.shift()
  }

  function attachListeners() {
    const onClick = (e) => recordInteraction('click', {
      x: e.clientX,
      y: e.clientY,
      target: e.target.tagName
    })
    const onScroll = (e) => recordInteraction('scroll', {
      y: window.scrollY,
      x: window.scrollX
    })
    const onInput = (e) => recordInteraction('input', {
      target: e.target.tagName,
      value: (e.target.value || '').slice(0, 30)
    })
    document.addEventListener('click', onClick, true)
    document.addEventListener('scroll', onScroll, true)
    document.addEventListener('input', onInput, true)
    eventListeners.push(['click', onClick], ['scroll', onScroll], ['input', onInput])
  }

  function detachListeners() {
    for (const [type, fn] of eventListeners) {
      document.removeEventListener(type, fn, true)
    }
    eventListeners = []
  }

  /**
   * 启动录制 (v2.2.78: 支持 reactive sessionId)
   */
  function start() {
    if (isRecording.value) return
    isRecording.value = true
    attachListeners()
    timer = setInterval(tick, intervalMs)
    console.log('[Replay] 录制启动 sessionId=', sessionId, 'interval=', intervalMs, 'ms')
  }

  /**
   * 停止录制 (但不触发合成)
   */
  function stop() {
    if (!isRecording.value) return
    isRecording.value = false
    if (timer) clearInterval(timer)
    timer = null
    detachListeners()
    console.log('[Replay] 录制停止, 帧数:', frameCount.value)
  }

  /**
   * 停止 + 触发服务端合成 MP4
   */
  async function finish() {
    stop()
    if (!sessionId) return
    try {
      const res = await replay.finish(sessionId)
      if (res.code === 0) {
        jobInfo.value = res.data
        console.log('[Replay] 合成任务已提交:', res.data)
      }
      return res.data
    } catch (e) {
      console.warn('[Replay] 触发合成失败:', e)
      if (onError) onError(e)
    }
  }

  /**
   * 轮询合成状态
   */
  async function pollJob(intervalMs = 2000) {
    if (!sessionId) return
    return setInterval(async () => {
      try {
        const res = await replay.job(sessionId)
        if (res.code === 0 && res.data) {
          jobInfo.value = res.data
          if (res.data.status === 'SUCCESS' || res.data.status === 'FAILED') {
            return res.data
          }
        }
      } catch (e) {
        console.warn('[Replay] pollJob 失败:', e)
      }
    }, intervalMs)
  }

  onUnmounted(() => {
    stop()
  })

  if (autoStart) {
    // 等到 mounted 后再 start (避免 onUnmounted 在 setup 阶段就触发)
    setTimeout(start, 100)
  }

  return {
    isRecording,
    frameCount,
    lastFrameAt,
    sessionInfo,
    jobInfo,
    start,
    stop,
    finish,
    tick,
    pollJob,
    recordInteraction
  }
}