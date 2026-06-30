import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * v2.3.1 全局错误状态
 *
 * <p>任何页面调 API 失败 / 业务错误 / 渲染异常 → errorStore.push()
 * App.vue 顶部 banner 自动显示, 不再覆盖整个页面
 *
 * <p>用法:
 * <pre>
 * import { useErrorStore } from '@/store/error'
 * const errorStore = useErrorStore()
 *
 * try {
 *   await api.something()
 * } catch (e) {
 *   errorStore.push({
 *     type: 'error',
 *     title: '加载失败',
 *     message: e.message || '请重试'
 *   })
 * }
 *
 * // 一次性 push 多个 (登录页常见)
 * errorStore.pushList([
 *   { type: 'warning', title: '...', message: '...' },
 *   { type: 'info', title: '...', message: '...' }
 * ])
 * </pre>
 */
export const useErrorStore = defineStore('error', () => {
  const list = ref([])
  let seq = 0

  /**
   * push 一条错误
   */
  function push(err) {
    seq += 1
    const item = {
      id: Date.now() + '-' + seq,
      type: err.type || 'error',
      title: err.title || '提示',
      message: err.message || '',
      source: err.source || 'manual',
      createdAt: Date.now()
    }
    list.value.push(item)

    // 自动消失 (info 默认 4s, warning 6s, error 8s)
    const ttl = err.type === 'info' ? 4000
              : err.type === 'warning' ? 6000
              : 8000
    if (ttl > 0 && !err.sticky) {
      setTimeout(() => dismiss(item.id), ttl)
    }
    return item.id
  }

  /**
   * 批量 push
   */
  function pushList(errs) {
    return errs.map(e => push(e))
  }

  /**
   * 关闭一条
   */
  function dismiss(id) {
    const i = list.value.findIndex(e => e.id === id)
    if (i >= 0) list.value.splice(i, 1)
  }

  /**
   * 清空
   */
  function clear() {
    list.value = []
  }

  /**
   * 按 source 清空 (页面切换时调用, 不显示上一页面的错误)
   */
  function clearBySource(source) {
    list.value = list.value.filter(e => e.source !== source)
  }

  return { list, push, pushList, dismiss, clear, clearBySource }
})