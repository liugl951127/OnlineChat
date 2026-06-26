<script setup>
import { onMounted, onErrorCaptured, ref } from 'vue'
import { ElNotification } from 'element-plus'
import { useUserStore } from '@/store/user'

const user = useUserStore()
const hasError = ref(false)
const errorMsg = ref('')

onMounted(() => user.hydrate())

// 全局错误捕获
onErrorCaptured(err => {
  hasError.value = true
  errorMsg.value = err.message || '未知错误'
  ElNotification.error({
    title: '页面渲染错误',
    message: err.message || '请刷新页面或返回首页',
    duration: 5000
  })
  console.error('[App] captured error:', err)
  return false  // 阻止错误继续冒泡
})

function reload() {
  location.reload()
}
</script>

<template>
  <el-config-provider>
    <div v-if="hasError" class="app-error">
      <div class="content">
        <h2>⚠️ 页面出错了</h2>
        <pre>{{ errorMsg }}</pre>
        <el-button type="primary" @click="reload">刷新页面</el-button>
        <el-button @click="$router.push('/')">返回首页</el-button>
      </div>
    </div>
    <router-view v-else />
  </el-config-provider>
</template>

<style lang="scss">
html, body, #app { height: 100%; margin: 0; }

.app-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f5f7fa;
  .content {
    text-align: center;
    h2 { color: #ff4d4f; margin-bottom: 16px; }
    pre {
      background: #fff;
      padding: 16px;
      border-radius: 8px;
      text-align: left;
      max-width: 600px;
      overflow: auto;
      margin: 16px auto;
      color: #86909c;
    }
  }
}
</style>