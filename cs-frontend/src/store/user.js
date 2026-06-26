import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'cs_token'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const profile = ref(null)
  const role = ref('')

  // 从 localStorage + URL 还原登录态
  function hydrate() {
    const url = new URL(location.href)
    const t = url.searchParams.get('token')
    if (t) {
      token.value = t
      localStorage.setItem(TOKEN_KEY, t)
      url.searchParams.delete('token')
      history.replaceState(null, '', url.pathname + url.search)
    } else {
      token.value = localStorage.getItem(TOKEN_KEY) || ''
    }
    // 解析 JWT payload（不校验签名，仅供前端读取）
    if (token.value) {
      try {
        const payload = JSON.parse(atob(token.value.split('.')[1] || ''))
        profile.value = { id: payload.userId, name: payload.displayName, channel: payload.channel }
        role.value = payload.role || ''
      } catch { /* token 无效 */ }
    }
  }

  function setToken(t, profileData) {
    token.value = t
    localStorage.setItem(TOKEN_KEY, t)
    if (profileData) profile.value = profileData
    if (profileData?.role) role.value = profileData.role
  }

  function logout() {
    token.value = ''
    profile.value = null
    role.value = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  const isLogin = computed(() => !!token.value)

  return { token, profile, role, isLogin, hydrate, setToken, logout }
})