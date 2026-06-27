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
        // 优先从 cs_user_info 拿完整用户信息（OAuthCallback.vue 保存）
        const stored = JSON.parse(localStorage.getItem('cs_user_info') || 'null')
        profile.value = {
          id: payload.userId,
          name: payload.displayName,
          channel: payload.channel,
          role: payload.role || stored?.role || 'CUSTOMER',
          avatar: stored?.avatar,
          customerId: stored?.customerId || payload.userId,
          nickname: stored?.nickname || payload.displayName,
          openid: stored?.openid,
          unionid: stored?.unionid,
          phoneMasked: stored?.phoneMasked,
          provider: stored?.provider || payload.channel
        }
        role.value = payload.role || stored?.role || ''
      } catch { /* token 无效 */ }
    }
  }

  function setToken(t, profileData) {
    token.value = t
    localStorage.setItem(TOKEN_KEY, t)
    if (profileData) {
      profile.value = profileData
      // 同步保存完整信息到 localStorage（OAuthCallback.vue 也会保存）
      localStorage.setItem('cs_user_info', JSON.stringify({
        customerId: profileData.customerId || profileData.id,
        nickname: profileData.name || profileData.nickname,
        avatar: profileData.avatar,
        openid: profileData.openid,
        unionid: profileData.unionid,
        phoneMasked: profileData.phoneMasked,
        provider: profileData.provider,
        role: profileData.role,
        channel: profileData.channel
      }))
    }
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