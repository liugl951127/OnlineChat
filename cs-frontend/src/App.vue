<script setup>
import { onMounted, onErrorCaptured, ref, computed } from 'vue'
import { ElNotification, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useErrorStore } from '@/store/error'

const user = useUserStore()
const errorStore = useErrorStore()

onMounted(() => user.hydrate())

// 全局错误捕获 (v2.3.1: 不再覆盖整个页面, 而是用顶部 banner)
onErrorCaptured(err => {
  console.error('[App] captured error:', err)
  errorStore.push({
    type: 'error',
    title: '页面渲染错误',
    message: err?.message || '未知错误, 请稍后重试',
    source: 'render'
  })
  return false  // 阻止错误继续冒泡, 让页面继续渲染
})

// 计算属性: banner 显示列表
const errors = computed(() => errorStore.list)

function dismissError(id) {
  errorStore.dismiss(id)
}

function clearAll() {
  errorStore.clear()
}
</script>

<template>
  <el-config-provider>
    <!-- v2.3.1: 顶部全局错误 banner (可关闭 + 一键清空) -->
    <div v-if="errors.length > 0" class="app-error-banner-wrap">
      <transition-group name="error-fade" tag="div">
        <div
          v-for="err in errors" :key="err.id"
          :class="['app-error-banner', `banner-${err.type}`]"
          @click="dismissError(err.id)">
          <span class="banner-icon">
            <template v-if="err.type === 'error'">⚠️</template>
            <template v-else-if="err.type === 'warning'">⚡</template>
            <template v-else>ℹ️</template>
          </span>
          <div class="banner-content">
            <div class="banner-title">{{ err.title }}</div>
            <div class="banner-msg">{{ err.message }}</div>
          </div>
          <span class="banner-close">×</span>
        </div>
      </transition-group>
      <div v-if="errors.length > 1" class="banner-clear-all">
        <el-button text size="small" @click="clearAll">清空全部 ({{ errors.length }})</el-button>
      </div>
    </div>

    <router-view />
  </el-config-provider>
</template>

<style lang="scss">
html, body, #app { height: 100%; margin: 0; }

// ============ v2.3.1: 顶部 banner (不挡路由内容) ============
.app-error-banner-wrap {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  width: min(560px, 90vw);
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;  // 允许点穿透背景

  .app-error-banner {
    pointer-events: auto;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-radius: 8px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
    cursor: pointer;
    user-select: none;
    transition: all 0.2s ease;

    .banner-icon { font-size: 22px; line-height: 1; }
    .banner-content { flex: 1; min-width: 0; }
    .banner-title { font-weight: 600; font-size: 14px; margin-bottom: 2px; }
    .banner-msg { font-size: 13px; opacity: 0.85; word-break: break-word; }
    .banner-close {
      font-size: 22px; line-height: 1; opacity: 0.6; padding: 0 4px;
      transition: opacity 0.2s;
    }
    &:hover .banner-close { opacity: 1; }

    &.banner-error {
      background: #fef0f0;
      border: 1px solid #fbc4c4;
      color: #5f2120;
    }
    &.banner-warning {
      background: #fdf6ec;
      border: 1px solid #faecd8;
      color: #5b3417;
    }
    &.banner-info {
      background: #f4f4f5;
      border: 1px solid #e9e9eb;
      color: #303133;
    }
  }

  .banner-clear-all {
    text-align: center;
    pointer-events: auto;
  }
}

// 渐入渐出动画
.error-fade-enter-active,
.error-fade-leave-active {
  transition: all 0.3s ease;
}
.error-fade-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}
.error-fade-leave-to {
  opacity: 0;
  transform: translateX(40px);
}
</style>