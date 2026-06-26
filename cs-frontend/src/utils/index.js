// 防抖
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

// 节流
export function throttle(fn, gap = 1000) {
  let last = 0
  return function (...args) {
    const now = Date.now()
    if (now - last >= gap) {
      last = now
      fn.apply(this, args)
    }
  }
}

// 时间格式化
export function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(typeof ts === 'number' && ts < 1e12 ? ts : ts)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

export function formatDate(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return formatTime(ts)
  return `${d.getMonth() + 1}-${d.getDate()} ${formatTime(ts)}`
}

// 文件大小
export function formatSize(b) {
  if (!b) return ''
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

// 手机号脱敏
export function maskMobile(phone) {
  if (!phone || phone.length < 7) return phone || ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

// 头像兜底（首字母）
export function avatarText(name) {
  if (!name) return '?'
  return name.slice(0, 1).toUpperCase()
}

// UUID
export function uuid() {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

// 复制到剪贴板
export async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    return true
  }
}

// 存储
export const storage = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)) } catch { return null } },
  set(k, v) { localStorage.setItem(k, JSON.stringify(v)) },
  remove(k) { localStorage.removeItem(k) }
}