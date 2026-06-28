<script setup>
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { replay } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 视频回溯播放器（v2.2.78）
 *
 * 两种播放模式:
 *   1. message-timeline: 消息按 offsetMs 时间轴播放 (旧模式, 兼容 v1.9.0)
 *   2. mp4-video: 服务端 ffmpeg 合成后的 MP4 视频播放 (新)
 *
 * 功能:
 *   - 拉取 replay 数据, 检测 videoUrl
 *   - 优先用 MP4 模式播放, 缺失则用消息时间轴模式
 *   - 提供 "触发合成" 按钮, 异步轮询 job 状态
 *   - 时间轴同步显示 (v2.2.78 新增)
 *   - 帧缩略图预览
 */

const props = defineProps({
  sessionId: { type: Number, required: true }
})
const emit = defineEmits(['close'])

const replayData = ref(null)
const frames = ref([])
const timeline = ref([])
const playing = ref(false)
const currentIndex = ref(0)
const currentTimeMs = ref(0)
let timer = null

// v2.2.78
const playMode = ref('auto')  // auto / mp4 / timeline
const jobInfo = ref(null)
const synthesizing = ref(false)
const videoRef = ref(null)

onMounted(async () => {
  try {
    const { data } = await replay.get(props.sessionId)
    replayData.value = data
    frames.value = data.frames || []
    timeline.value = data.timeline || []
    if (frames.value.length === 0 && timeline.value.length === 0) {
      ElMessage.info('该会话没有可回放的记录')
    } else if (timeline.value.length > 0) {
      ElMessage.success(`已加载 ${timeline.value.length} 帧快照`)
    }
  } catch (e) {
    ElMessage.error('加载回放数据失败')
  }
})

onUnmounted(() => pause())

const totalMs = computed(() => {
  if (timeline.value.length) {
    return Math.max(...timeline.value.map(f => f.offsetMs || 0))
  }
  if (frames.value.length) {
    return Math.max(...frames.value.map(f => f.offsetMs || 0))
  }
  return 0
})

const videoUrl = computed(() => replayData.value?.videoUrl)
const hasMp4 = computed(() => !!videoUrl.value)

const effectiveMode = computed(() => {
  if (playMode.value === 'mp4') return 'mp4'
  if (playMode.value === 'timeline') return 'timeline'
  // auto
  return hasMp4.value ? 'mp4' : 'timeline'
})

const progressPercent = computed(() => {
  if (!totalMs.value) return 0
  return (currentTimeMs.value / totalMs.value) * 100
})

const currentFrame = computed(() => frames.value[currentIndex.value])
const currentTimeline = computed(() => {
  // 找到最接近 currentTimeMs 的 timeline 项
  if (!timeline.value.length) return null
  let closest = timeline.value[0]
  for (const t of timeline.value) {
    if ((t.offsetMs || 0) <= currentTimeMs.value) closest = t
  }
  return closest
})

function play() {
  if (!frames.value.length && !timeline.value.length) return
  playing.value = true
  timer = setInterval(() => {
    currentTimeMs.value += 100
    while (currentIndex.value < frames.value.length - 1
           && (frames.value[currentIndex.value + 1]?.offsetMs || 0) <= currentTimeMs.value) {
      currentIndex.value++
    }
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
  for (let i = 0; i < frames.value.length; i++) {
    if ((frames.value[i]?.offsetMs || 0) >= currentTimeMs.value) {
      currentIndex.value = Math.max(0, i - 1)
      break
    }
  }
}

function seekToFrame(seq) {
  const t = timeline.value.find(t => t.seq === seq)
  if (t && totalMs.value > 0) {
    seek(t.offsetMs / totalMs.value)
  }
}

async function triggerSynthesis() {
  if (!props.sessionId) return
  try {
    await ElMessageBox.confirm(
      `确定要触发起 sessionId=${props.sessionId} 的视频合成吗？\n\n这会调用服务端 ffmpeg 合并所有帧, 可能需要几秒到几分钟。`,
      '触发视频合成',
      { confirmButtonText: '开始合成', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }
  synthesizing.value = true
  try {
    const { data } = await replay.finish(props.sessionId)
    jobInfo.value = { status: 'PENDING', frameCount: data.frameCount }
    ElMessage.success(`合成任务已提交, 帧数 ${data.frameCount}`)
    // 轮询
    const poll = setInterval(async () => {
      try {
        const res = await replay.job(props.sessionId)
        if (res.code === 0 && res.data) {
          jobInfo.value = res.data
          if (res.data.status === 'SUCCESS') {
            ElMessage.success('合成成功!')
            clearInterval(poll)
            // 重新拉数据
            const { data: fresh } = await replay.get(props.sessionId)
            replayData.value = fresh
            playMode.value = 'mp4'
          } else if (res.data.status === 'FAILED') {
            ElMessage.error('合成失败: ' + (res.data.errorMessage || '未知错误'))
            clearInterval(poll)
          }
        }
      } catch (e) {
        console.warn('[Replay] poll job error:', e)
      }
    }, 2000)
    // 最多轮询 5 分钟
    setTimeout(() => clearInterval(poll), 5 * 60 * 1000)
  } catch (e) {
    ElMessage.error('触发合成失败: ' + (e.message || ''))
  } finally {
    synthesizing.value = false
  }
}

function switchMode(mode) {
  playMode.value = mode
  pause()
  if (mode === 'mp4' && videoRef.value) {
    videoRef.value.currentTime = 0
    videoRef.value.play().catch(() => {})
  } else if (mode === 'timeline' || (mode === 'auto' && !hasMp4.value)) {
    play()
  }
}

function formatMs(ms) {
  if (ms == null) return '00:00'
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

function getKindIcon(kind) {
  return { SCREENSHOT: '📷', INTERACTION: '🖱', MESSAGE: '💬', PAGE: '📄' }[kind] || '📌'
}

function getKindColor(kind) {
  return { SCREENSHOT: '#1677ff', INTERACTION: '#52c41a', MESSAGE: '#722ed1', PAGE: '#fa8c16' }[kind] || '#86909c'
}

function getJobStatusColor(status) {
  return { PENDING: '#fa8c16', RUNNING: '#1677ff', SUCCESS: '#52c41a', FAILED: '#f5222d' }[status] || '#86909c'
}
</script>

<template>
  <div class="replay-panel">
    <div class="rp-header">
      <h3>🎬 视频回溯 <small v-if="hasMp4" class="mp4-tag">MP4 就绪</small></h3>
      <el-button text @click="emit('close')">关闭 ✕</el-button>
    </div>

    <div v-if="replayData" class="rp-info">
      <div class="info-row">
        <span>会话：{{ replayData.customerId }} ↔ {{ replayData.agentUsername || '机器人' }}</span>
        <span>消息：{{ replayData.frameCount }} | 帧：{{ timeline.length }}</span>
      </div>
      <div class="info-row">
        <span>开始：{{ formatTime(replayData.startTime) }}</span>
        <span>结束：{{ formatTime(replayData.endTime) }}</span>
      </div>
    </div>

    <!-- 模式切换 -->
    <div class="mode-bar">
      <el-radio-group v-model="playMode" size="small">
        <el-radio-button value="auto">自动</el-radio-button>
        <el-radio-button value="mp4" :disabled="!hasMp4">MP4 视频</el-radio-button>
        <el-radio-button value="timeline">消息时间线</el-radio-button>
      </el-radio-group>
      <div class="mode-hint">
        当前: <strong>{{ effectiveMode === 'mp4' ? 'MP4 视频' : '消息时间线' }}</strong>
      </div>
      <el-button v-if="timeline.length > 0 && !hasMp4" size="small" type="primary"
                 @click="triggerSynthesis" :loading="synthesizing">
        🎞 合成 MP4 视频
      </el-button>
    </div>

    <!-- 合成状态 -->
    <div v-if="jobInfo" class="job-status" :style="{ borderColor: getJobStatusColor(jobInfo.status) }">
      <span>合成任务: </span>
      <el-tag :color="getJobStatusColor(jobInfo.status)" effect="dark" size="small">
        {{ jobInfo.status }}
      </el-tag>
      <span v-if="jobInfo.status === 'SUCCESS'">
        <a :href="jobInfo.videoUrl" target="_blank">下载视频</a>
        | 时长: {{ formatMs(jobInfo.durationMs) }}
      </span>
      <span v-else-if="jobInfo.status === 'FAILED'" style="color: #f5222d">
        {{ jobInfo.errorMessage }}
      </span>
      <span v-else-if="jobInfo.frameCount">
        ({{ jobInfo.frameCount }} 帧待合成)
      </span>
    </div>

    <!-- MP4 播放模式 -->
    <div v-if="effectiveMode === 'mp4' && hasMp4" class="video-player">
      <video
        ref="videoRef"
        :src="videoUrl"
        controls
        autoplay
        class="video-tag"
      >
        您的浏览器不支持 video 标签。
      </video>
      <div class="video-meta">
        🎞 <a :href="videoUrl" target="_blank">{{ videoUrl }}</a>
        | <el-button text size="small" @click="replay.get(props.sessionId)">刷新数据</el-button>
      </div>
    </div>

    <!-- 消息时间线模式 -->
    <div v-else class="player">
      <div v-if="!frames.length && !timeline.length" class="empty">
        暂无消息记录
      </div>
      <div v-else>
        <!-- 当前帧 (高亮) -->
        <div v-if="currentFrame" class="frame-card"
             :style="{ borderColor: getRoleColor(currentFrame.fromRole) }">
          <div class="frame-header">
            <span class="frame-role" :style="{ color: getRoleColor(currentFrame.fromRole) }">
              {{ currentFrame.fromRole }}
            </span>
            <span class="frame-user">{{ currentFrame.fromUser }}</span>
            <span class="frame-time">{{ formatMs(currentFrame.offsetMs || 0) }}</span>
          </div>
          <div class="frame-content">{{ currentFrame.content }}</div>
        </div>
        <div v-else-if="currentTimeline" class="frame-card timeline-card"
             :style="{ borderColor: getKindColor(currentTimeline.frameKind) }">
          <div class="frame-header">
            <span class="frame-kind">
              {{ getKindIcon(currentTimeline.frameKind) }} {{ currentTimeline.frameKind }}
            </span>
            <span class="frame-time">{{ formatMs(currentTimeline.offsetMs) }}</span>
          </div>
          <div v-if="currentTimeline.metadata" class="frame-content">
            {{ currentTimeline.metadata }}
          </div>
        </div>

        <!-- 进度条 -->
        <div class="progress-bar" v-if="totalMs">
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

        <!-- 控制 -->
        <div class="controls">
          <el-button type="primary" circle size="large" @click="togglePlay">
            {{ playing ? '⏸' : '▶' }}
          </el-button>
          <el-button @click="seek(0)">⏮ 重置</el-button>
          <el-button @click="seek(1)">⏭ 跳到结尾</el-button>
        </div>
      </div>
    </div>

    <!-- 时间轴 (新) -->
    <div v-if="timeline.length" class="timeline-bar">
      <h4>⏱ 帧时间轴 ({{ timeline.length }} 帧)</h4>
      <div class="timeline-scroll">
        <div
          v-for="t in timeline"
          :key="t.seq"
          class="tl-item"
          :class="{ active: currentTimeline?.seq === t.seq }"
          :style="{ background: getKindColor(t.frameKind) }"
          @click="seekToFrame(t.seq)"
          :title="`seq=${t.seq} ${t.frameKind} @ ${formatMs(t.offsetMs)}`"
        >
          <span class="tl-icon">{{ getKindIcon(t.frameKind) }}</span>
          <span class="tl-seq">{{ t.seq }}</span>
          <span class="tl-time">{{ formatMs(t.offsetMs) }}</span>
        </div>
      </div>
    </div>

    <!-- 消息列表 (保留) -->
    <div v-if="frames.length" class="frame-list">
      <h4>📋 消息时间线</h4>
      <div
        v-for="(f, idx) in frames"
        :key="idx"
        class="frame-row"
        :class="{ active: idx === currentIndex }"
        @click="seek(idx / Math.max(frames.length, 1))"
      >
        <span class="idx">{{ idx + 1 }}</span>
        <span class="role-tag" :style="{ background: getRoleColor(f.fromRole) }">
          {{ f.fromRole }}
        </span>
        <span class="user">{{ f.fromUser }}</span>
        <span class="content">{{ (f.content || '').substring(0, 30) }}{{ (f.content || '').length > 30 ? '...' : '' }}</span>
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
  max-height: 800px;
  overflow-y: auto;
}
.rp-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  h3 { margin: 0; }
  .mp4-tag {
    background: #52c41a; color: #fff;
    padding: 2px 8px; border-radius: 4px;
    font-size: 11px; margin-left: 8px;
  }
}
.rp-info {
  background: #f0f7ff;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 12px;
  .info-row {
    display: flex; justify-content: space-between;
    color: #595959; margin-bottom: 4px;
  }
}
.mode-bar {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  .mode-hint { font-size: 12px; color: #595959; }
}
.job-status {
  border: 2px solid;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 13px;
  display: flex; gap: 8px; align-items: center;
  a { color: #1677ff; text-decoration: none; margin: 0 4px; }
}
.video-player {
  background: #000;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
  .video-tag {
    width: 100%;
    max-height: 400px;
    border-radius: 4px;
    background: #000;
  }
  .video-meta {
    color: #999; font-size: 11px;
    margin-top: 8px;
    a { color: #69b1ff; }
  }
}
.player {
  background: #000;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
  min-height: 180px;
  .empty { color: #fff; text-align: center; padding: 50px 0; }
  .frame-card {
    background: #1f1f1f;
    border-left: 4px solid;
    border-radius: 4px;
    padding: 16px;
    color: #fff;
    max-width: 80%;
    .frame-header {
      display: flex; gap: 8px; align-items: center;
      margin-bottom: 8px; font-size: 12px;
      .frame-role, .frame-kind { font-weight: 600; }
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
.timeline-bar {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
  margin-bottom: 12px;
  h4 { font-size: 13px; color: #86909c; margin-bottom: 8px; }
  .timeline-scroll {
    display: flex; gap: 4px;
    overflow-x: auto;
    padding-bottom: 6px;
    .tl-item {
      flex: 0 0 60px;
      padding: 6px 8px;
      border-radius: 4px;
      color: #fff;
      font-size: 11px;
      cursor: pointer;
      display: flex; flex-direction: column;
      align-items: center;
      gap: 2px;
      opacity: 0.85;
      transition: all 0.2s;
      &.active {
        opacity: 1;
        transform: scale(1.1);
        box-shadow: 0 2px 8px rgba(0,0,0,0.3);
      }
      &:hover { opacity: 1; }
      .tl-icon { font-size: 16px; }
      .tl-seq { font-family: monospace; }
      .tl-time { font-family: monospace; opacity: 0.8; }
    }
  }
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
    color: #fff; font-size: 10px;
    padding: 1px 6px; border-radius: 3px;
  }
  .user { color: #595959; min-width: 80px; }
  .content { flex: 1; color: #262626; }
  .ts { color: #86909c; font-family: monospace; }
}
</style>