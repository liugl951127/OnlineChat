<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="notfound">
    <div class="content">
      <div class="code">404</div>
      <h1>页面走丢了</h1>
      <p>您访问的页面不存在或已被移动</p>
      <div class="actions">
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
        <el-button @click="router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.notfound {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.content {
  text-align: center;
}
.code {
  font-size: 120px;
  font-weight: 700;
  background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
  margin-bottom: 16px;
}
h1 {
  font-size: 24px;
  margin: 0 0 8px;
  color: #1f2329;
}
p {
  font-size: 14px;
  color: #86909c;
  margin: 0 0 24px;
}
.actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}
</style>