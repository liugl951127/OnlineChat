import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/Home.vue'), meta: { title: '在线客服' } },
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { title: '登录', public: true } },
  { path: '/customer', name: 'customer', component: () => import('@/views/Customer.vue'), meta: { title: '客户咨询', role: 'CUSTOMER' } },
  { path: '/agent', name: 'agent', component: () => import('@/views/Agent.vue'), meta: { title: '坐席工作台', role: 'AGENT' } },
  { path: '/admin', name: 'admin', component: () => import('@/views/Admin.vue'), meta: { title: '后管系统', role: 'ADMIN' } },
  { path: '/404', name: 'notfound', component: () => import('@/views/NotFound.vue'), meta: { title: '页面未找到', public: true } },
  // 微信/GitHub/Google OAuth 回调页（从后端 302 跳转到这里，带 code 或 token）
  { path: '/auth/:provider/callback', name: 'oauth-callback', component: () => import('@/views/OAuthCallback.vue'), meta: { title: '授权回调', public: true } },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  // 使用 Hash 模式：URL 带 #/login，避免后端 SPA fallback 配置
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} · OnlineChat` : 'OnlineChat'
  if (to.meta.public) return next()
  const user = useUserStore()
  if (!user.token) return next({ name: 'login', query: { next: to.name } })
  if (to.meta.role && user.role && user.role !== to.meta.role && user.role !== 'ADMIN') {
    return next({ name: 'home' })
  }
  next()
})

export default router