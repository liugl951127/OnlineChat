<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const user = useUserStore()

const roles = [
  { key: 'customer', label: '客户咨询', desc: '智能客服 + 人工坐席', icon: 'ChatDotRound', color: '#1677ff', bg: 'linear-gradient(135deg, #1677ff 0%, #4096ff 100%)' },
  { key: 'agent', label: '坐席工作台', desc: '排队 / 富文本聊天 / 快捷回复', icon: 'Service', color: '#52c41a', bg: 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)' },
  { key: 'admin', label: '后管系统', desc: '会话审核 / 强制挂断 / 审计', icon: 'Setting', color: '#722ed1', bg: 'linear-gradient(135deg, #722ed1 0%, #a855f7 100%)' }
]

function go(role) {
  if (user.token && user.role) {
    // 已登录则按角色跳转
    const map = { CUSTOMER: 'customer', AGENT: 'agent', ADMIN: 'admin' }
    const target = map[user.role] || role
    router.push({ name: target })
  } else {
    router.push({ name: 'login', query: { next: role } })
  }
}
</script>

<template>
  <div class="home">
    <div class="hero">
      <div class="logo">💬</div>
      <h1>在线客服系统</h1>
      <p class="subtitle">Spring Cloud · Vue 3 · Element Plus</p>
      <div class="badges">
        <el-tag v-for="t in ['v1.7.0','Spring Cloud 2023','Java 17','Element Plus 2.7']" :key="t" round effect="plain" size="small" class="badge">{{ t }}</el-tag>
      </div>
    </div>
    <div class="grid">
      <div v-for="r in roles" :key="r.key" class="card" @click="go(r.key)">
        <div class="icon-box" :style="{ background: r.bg }">
          <el-icon :size="32" color="#fff"><component :is="r.icon" /></el-icon>
        </div>
        <div class="title">{{ r.label }}</div>
        <div class="desc">{{ r.desc }}</div>
        <div class="enter">
          <span>进入</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
    </div>
    <footer>
      © 2026 OnlineChat ·
      <a href="https://github.com/liugl951127/OnlineChat" target="_blank" rel="noopener noreferrer">GitHub</a>
    </footer>
  </div>
</template>

<style lang="scss" scoped>
.home {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: radial-gradient(circle at 20% 30%, rgba(22,119,255,0.10) 0%, transparent 50%),
              radial-gradient(circle at 80% 70%, rgba(114,46,209,0.10) 0%, transparent 50%),
              #f5f7fa;
}

.hero {
  text-align: center;
  margin-bottom: 48px;

  .logo {
    width: 80px; height: 80px;
    background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
    border-radius: 20px;
    display: flex; align-items: center; justify-content: center;
    font-size: 40px; margin: 0 auto 16px;
    box-shadow: 0 12px 32px rgba(22,119,255,0.25);
  }

  h1 {
    margin: 0 0 8px;
    font-size: 32px;
    font-weight: 600;
  }

  .subtitle {
    margin: 0 0 16px;
    color: #86909c;
    font-size: 14px;
  }

  .badges {
    display: flex;
    gap: 8px;
    justify-content: center;
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
  max-width: 920px;
  width: 100%;
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px;
  border: 1px solid #e5e6eb;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
    border-color: #1677ff;
  }

  .icon-box {
    width: 56px; height: 56px;
    border-radius: 12px;
    display: flex; align-items: center; justify-content: center;
    margin-bottom: 16px;
  }

  .title {
    font-size: 18px;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .desc {
    font-size: 13px;
    color: #86909c;
    margin-bottom: 20px;
  }

  .enter {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #1677ff;
    font-size: 13px;
    font-weight: 500;
  }
}

footer {
  margin-top: 48px;
  font-size: 12px;
  color: #86909c;
  a { color: #1677ff; text-decoration: none; }
}
</style>