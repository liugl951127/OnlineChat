<script setup>
import { ref, onMounted, computed } from 'vue'
import { replay } from '@/api'
import { ElMessage } from 'element-plus'

/**
 * 视频回溯播放器（v1.9.0）
 *
 * 功能：
 * - 拉取会话的所有消息（按时间戳）
 * - 模拟视频播放器：每条消息按 offsetMs 间隔播放
 * - 播放/暂停/进度条/跳转
 */

const props = defineProps({
  sessionId: { type: Number, required: true }
})
const emit = defineEmits(['close'])

const replayData = ref(null)
const frames = ref([])
const playing = ref(false)
const currentIndex = ref(0)
const currentTimeMs = ref(0)
let timer = null

onMounted(async () => {
  try {
    const { data } = await replay.get(props.sessionId)
    replayData.value = data
    frames.value = data.frames || []
    if (frames.value.length === 0) {
      ElMessage.info('该会话没有可回放的消息')
    }
  } catch (e) {
    ElMessage.error('加载回放数据失败')
  }
})

const totalMs = computed(() => {
  if (!frames.value.length) return 0
  return Math.max(...frames.value.map(f => f.offsetMs || 0))
})

const progressPercent = computed(() => {
  if (!totalMs.value) return 0
  return (currentTimeMs.value / totalMs.value) * 100
})

const currentFrame = computed(() => frames.value[currentIndex.value])

function play() {
  if (!frames.value.length) return
  playing.value = true
  timer = setInterval(() => {
    currentTimeMs.value += 100
    // 推进 frame
    while (currentIndex.value < frames.value.length - 1
           && (frames.value[currentIndex.value + 1]?.offsetMs || 0) <= currentTimeMs.value) {
      currentIndex.value++
    }
    // 结束
    if (currentTimeMs.value >= totalMs.value) {
      pause()
    }
  }, 100)
}

function pause() {
  playing.value = false
  if (timer) clearInterval(timer)
  timer = null
}

function togglePlay() {
  if (playing.value) pause()
  else play()
}

function seek(percent) {
  pause()
  currentTimeMs.value = totalMs.value * percent
  // 重置 frame index
  for (let i = 0; i < frames.value.length; i++) {
    if ((frames.value[i]?.offsetMs || 0) >= currentTimeMs.value) {
      currentIndex.value = Math.max(0, i - 1)
      break
    }
  }
}

function formatMs(ms) {
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  return `${m.toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`
}

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN')
}

function getRoleColor(role) {
  return { CUSTOMER: '#1677ff', AGENT: '#52c41a', ROBOT: '#722ed1', SYSTEM: '#86909c' }[role] || '#1677ff'
}
</script>

<template>
  <div class="replay-panel">
    <div class="rp-header">
      <h3>🎬 视频回溯</h3>
      <el-button text @click="emit('close')">关闭 ✕</el-button>
    </div>

    <div v-if="replayData" class="rp-info">
      <div class="info-row">
        <span>会话：{{ replayData.customerId }} ↔ {{ replayData.agentUsername || '机器人' }}</span>
        <span>消息数：{{ replayData.frameCount }}</span>
      </div>
      <div class="info-row">
        <span>开始：{{ formatTime(replayData.startTime) }}</span>
        <span>结束：{{ formatTime(replayData.endTime) }}</span>
      </div>
    </div>

    <!-- 播放器主区 -->
    <div class="player">
      <div v-if="!frames.length" class="empty">
        暂无消息记录
      </div>
      <div v-else class="frame-display">
        <div v-if="currentFrame" class="frame-card" :style="{ borderColor: getRoleColor(currentFrame.fromRole) }">
          <div class="frame-header">
            <span class="frame-role" :style="{ color: getRoleColor(currentFrame.fromRole) }">
              {{ currentFrame.fromRole }}
            </span>
            <span class="frame-user">{{ currentFrame.fromUser }}</span>
            <span class="frame-time">{{ formatMs(currentFrame.offsetMs || 0) }}</span>
          </div>
          <div class="frame-content">
            {{ currentFrame.content }}
          </div>
        </div>
      </div>

      <!-- 进度条 -->
      <div class="progress-bar" v-if="frames.length">
        <el-slider
          :model-value="progressPercent"
          @input="seek"
          :show-tooltip="false"
          :max="100"
        />
        <div class="time-info">
          <span>{{ formatMs(currentTimeMs) }}</span>
          <span>{{ formatMs(totalMs) }}</span>
        </div>
      </div>

      <!-- 控制按钮 -->
      <div class="controls" v-if="frames.length">
        <el-button type="primary" circle size="large" @click="togglePlay">
          {{ playing ? '⏸' : '▶' }}
        </el-button>
        <el-button @click="seek(0)">⏮ 重置</el-button>
        <el-button @click="seek(1)">⏭ 跳到结尾</el-button>
      </div>
    </div>

    <!-- 消息列表（参考） -->
    <div v-if="frames.length" class="frame-list">
      <h4>📋 消息时间线</h4>
      <div
        v-for="(f, idx) in frames"
        :key="idx"
        class="frame-row"
        :class="{ active: idx === currentIndex }"
        @click="seek(idx / frames.length)"
      >
        <span class="idx">{{ idx + 1 }}</span>
        <span class="role-tag" :style="{ background: getRoleColor(f.fromRole) }">{{ f.fromRole }}</span>
        <span class="user">{{ f.fromUser }}</span>
        <span class="content">{{ f.content.substring(0, 30) }}{{ f.content.length > 30 ? '...' : '' }}</span>
        <span class="ts">{{ formatMs(f.offsetMs || 0) }}</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.replay-panel {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  max-height: 700px;
  overflow-y: auto;
}
.rp-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  h3 { margin: 0; }
}
.rp-info {
  background: #f0f7ff;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 12px;
  .info-row {
    display: flex; justify-content: space-between;
    color: #595959;
    margin-bottom: 4px;
  }
}
.player {
  background: #000;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
  min-height: 180px;
  .empty { color: #fff; text-align: center; padding: 50px 0; }
  .frame-display {
    min-height: 100px;
    display: flex; align-items: center; justify-content: center;
  }
  .frame-card {
    background: #1f1f1f;
    border-left: 4px solid;
    border-radius: 4px;
    padding: 16px;
    color: #fff;
    max-width: 80%;
    .frame-header {
      display: flex; gap: 8px; align-items: center;
      margin-bottom: 8px;
      font-size: 12px;
      .frame-role { font-weight: 600; }
      .frame-user { color: #999; }
      .frame-time { margin-left: auto; color: #666; font-family: monospace; }
    }
    .frame-content {
      font-size: 14px;
      line-height: 1.6;
      white-space: pre-wrap;
    }
  }
}
.progress-bar {
  margin-top: 16px;
  padding: 0 8px;
  .time-info {
    display: flex; justify-content: space-between;
    color: #999; font-size: 11px; font-family: monospace;
  }
}
.controls {
  display: flex; gap: 8px; justify-content: center;
  margin-top: 12px;
}
.frame-list {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
  h4 { font-size: 13px; color: #86909c; margin-bottom: 8px; }
}
.frame-row {
  display: flex; gap: 8px; align-items: center;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  &.active { background: #e6f4ff; }
  &:hover { background: #f5f5f5; }
  .idx { color: #86909c; font-family: monospace; min-width: 24px; }
  .role-tag {
    color: #fff;
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 3px;
  }
  .user { color: #595959; min-width: 80px; }
  .content { flex: 1; color: #262626; }
  .ts { color: #86909c; font-family: monospace; }
}
</style>