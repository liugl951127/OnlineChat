<template>
  <el-card class="offline-panel" shadow="hover">
    <template #header>
      <div class="header">
        <el-icon><Bell /></el-icon>
        <span>离线消息</span>
        <el-badge :value="messages.length" :max="99" type="warning" />
      </div>
    </template>

    <div v-if="loading" class="loading">
      <el-icon class="rotating"><Loading /></el-icon>
      加载中...
    </div>

    <el-empty v-else-if="messages.length === 0" description="无离线消息" :image-size="80" />

    <el-scrollbar v-else height="400px">
      <div
        v-for="msg in messages"
        :key="msg.msgId || msg.id"
        class="msg-item"
      >
        <div class="msg-header">
          <el-tag size="small" :type="typeColor(msg.msgType)">
            {{ msg.msgType || 'TEXT' }}
          </el-tag>
          <span class="msg-from">{{ msg.senderId || msg.fromId || '系统' }}</span>
          <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
        </div>
        <div class="msg-content">{{ msg.preview || msg.payload || msg.content }}</div>
      </div>
    </el-scrollbar>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Bell, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const props = defineProps({
  userId: { type: String, required: true }
})

const loading = ref(false)
const messages = ref([])

async function load() {
  loading.value = true
  try {
    // 假设 cs-message 提供 GET /api/offline/list?userId=xxx
    const { data } = await request.get('/offline/list', { params: { userId: props.userId } })
    messages.value = data || []
  } catch (e) {
    // 后端可能未实现接口，静默
    messages.value = []
  } finally {
    loading.value = false
  }
}

function typeColor(t) {
  return { TEXT: 'primary', IMAGE: 'success', FILE: 'warning', SYSTEM: 'info' }[t] || ''
}

function formatTime(t) {
  if (!t) return ''
  try {
    return new Date(t).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return t
  }
}

defineExpose({ load })
onMounted(load)
</script>

<style scoped>
.offline-panel { margin-bottom: 16px; }
.header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.msg-item {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
}
.msg-item:last-child { border-bottom: none; }
.msg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.msg-from { font-size: 13px; font-weight: 500; }
.msg-time { margin-left: auto; font-size: 12px; color: #909399; }
.msg-content {
  font-size: 14px;
  color: #303133;
  padding-left: 8px;
  white-space: pre-wrap;
  word-break: break-all;
}
.loading, .empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #909399;
}
.rotating { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>