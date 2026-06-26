<template>
  <div class="voice-recorder">
    <el-button v-if="!recording" :icon="Microphone" circle
      @mousedown="start" @touchstart.prevent="start" title="长按录音" />
    <el-button v-else type="danger" :icon="VideoPause" circle
      @mouseup="stop" @mouseleave="stop" @touchend="stop" title="松开发送" />

    <span v-if="recording" class="recording-tip">🔴 录音中 {{ seconds }}s</span>
    <span v-if="uploading" class="recording-tip">上传中...</span>

    <!-- 播放列表 -->
    <div class="voice-list" v-if="voices && voices.length">
      <div v-for="v in voices" :key="v.id" class="voice-item">
        <el-button link size="small" @click="play(v)" :icon="VideoPlay">
          {{ v.durationSec }}s
        </el-button>
        <span class="voice-trans" v-if="v.transcription">"{{ v.transcription }}"</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Microphone, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { media } from '../api'

const props = defineProps({
  sessionId: [Number, String],
  fromId: String,
  fromRole: { type: String, default: 'customer' }
})
const emit = defineEmits(['uploaded'])

const recording = ref(false)
const uploading = ref(false)
const seconds = ref(0)
const voices = ref([])
const recorder = ref(null)
const chunks = ref([])
let timer = null

onBeforeUnmount(() => {
  if (recording.value) stop()
  cleanup()
})

async function start() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    chunks.value = []
    recorder.value = new MediaRecorder(stream)
    recorder.value.ondataavailable = e => chunks.value.push(e.data)
    recorder.value.onstop = upload
    recorder.value.start()
    recording.value = true
    seconds.value = 0
    timer = setInterval(() => seconds.value++, 1000)
  } catch (e) {
    ElMessage.error('麦克风权限被拒绝：' + e.message)
  }
}

async function stop() {
  if (!recording.value || !recorder.value) return
  if (recorder.value.state !== 'inactive') {
    recorder.value.stop()
  }
  recording.value = false
  if (timer) clearInterval(timer)
  // 关闭麦克风
  if (recorder.value.stream) {
    recorder.value.stream.getTracks().forEach(t => t.stop())
  }
}

async function upload() {
  if (!chunks.value.length) return
  uploading.value = true
  try {
    const blob = new Blob(chunks.value, { type: 'audio/webm' })
    const base64 = await blobToBase64(blob)
    const sizeKb = Math.round(blob.size / 1024)
    const resp = await media.uploadVoice({
      sessionId: props.sessionId,
      fromId: props.fromId,
      fromRole: props.fromRole,
      durationSec: seconds.value,
      fileSizeKb: sizeKb,
      audioBase64: base64
    })
    ElMessage.success('语音已发送')
    emit('uploaded', resp.data)
    await loadVoices()
  } catch (e) {
    ElMessage.error('上传失败：' + e.message)
  } finally {
    uploading.value = false
    chunks.value = []
  }
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onloadend = () => resolve(r.result.split(',')[1])
    r.onerror = reject
    r.readAsDataURL(blob)
  })
}

async function loadVoices() {
  if (!props.sessionId) return
  const resp = await media.listVoice(props.sessionId)
  voices.value = resp.data || []
}

function play(v) {
  // Mock：实际可播放 OSS URL
  ElMessage.info(`🔊 播放 ${v.durationSec}秒 语音（mock）`)
}

function cleanup() {
  if (timer) clearInterval(timer)
}

defineExpose({ loadVoices })
</script>

<style scoped>
.voice-recorder {
  display: inline-flex; align-items: center; gap: 8px;
}
.recording-tip {
  color: #f5222d; font-size: 12px;
}
.voice-list {
  display: inline-flex; gap: 8px; flex-wrap: wrap;
  margin-left: 8px;
}
.voice-item {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 8px;
  background: #f0f5ff;
  border-radius: 12px;
}
.voice-trans {
  font-size: 12px; color: #595959;
}
</style>