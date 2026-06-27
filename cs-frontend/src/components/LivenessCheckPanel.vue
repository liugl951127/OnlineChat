<template>
  <div class="liveness-panel">
    <el-steps :active="activeStep" finish-status="success" align-center>
      <el-step v-for="(a, i) in actions" :key="i" :title="a.title" :description="a.desc" />
    </el-steps>

    <div class="action-area">
      <transition name="fade" mode="out-in">
        <div v-if="activeStep < actions.length" :key="activeStep" class="action-prompt">
          <div class="emoji">{{ actions[activeStep].emoji }}</div>
          <div class="title">{{ actions[activeStep].title }}</div>
          <div class="desc">{{ actions[activeStep].desc }}</div>
          <el-button type="primary" size="large" @click="nextAction" round>
            完成动作并继续
          </el-button>
        </div>
        <div v-else key="done" class="action-prompt success">
          <div class="emoji">✅</div>
          <div class="title">所有动作完成</div>
          <el-button type="success" size="large" @click="submit" :loading="submitting" round>
            提交活体检测
          </el-button>
        </div>
      </transition>
    </div>

    <video ref="videoEl" autoplay muted playsinline class="camera-preview" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { kyc } from '@/api'

const emit = defineEmits(['passed'])

const actions = [
  { title: '请眨眼', desc: '缓慢眨动双眼', emoji: '👁️' },
  { title: '请张嘴', desc: '张大嘴巴', emoji: '👄' },
  { title: '请缓慢左转头', desc: '左转 30 度', emoji: '⬅️' },
  { title: '请缓慢右转头', desc: '右转 30 度', emoji: '➡️' }
]

const activeStep = ref(0)
const submitting = ref(false)
const videoEl = ref(null)
const actions_done = ref([])
let stream = null

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { width: 320, height: 240 } })
    if (videoEl.value) videoEl.value.srcObject = stream
  } catch (e) {
    ElMessage.warning('摄像头权限被拒，请手动上传活体照片')
  }
}

function nextAction() {
  actions_done.value.push(actions[activeStep.value].title)
  activeStep.value++
}

async function submit() {
  submitting.value = true
  try {
    // 拍照：从 video 取一帧
    let faceImgUrl = ''
    if (videoEl.value) {
      const canvas = document.createElement('canvas')
      canvas.width = videoEl.value.videoWidth || 320
      canvas.height = videoEl.value.videoHeight || 240
      canvas.getContext('2d').drawImage(videoEl.value, 0, 0)
      faceImgUrl = canvas.toDataURL('image/jpeg', 0.7)
    }

    const { data } = await kyc.liveness({
      faceImgUrl,
      actions: actions_done.value
    })

    ElMessage.success(`活体检测通过（分数 ${data.score}）`)
    emit('passed', data)
  } catch (e) {
    ElMessage.error('活体检测失败：' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

onMounted(startCamera)
onBeforeUnmount(() => {
  if (stream) stream.getTracks().forEach(t => t.stop())
})
</script>

<style scoped>
.liveness-panel {
  position: relative;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  min-height: 320px;
}
.camera-preview {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 200px;
  height: 150px;
  background: #000;
  border-radius: 4px;
  object-fit: cover;
  z-index: 10;
}
.action-area {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
}
.action-prompt {
  text-align: center;
}
.emoji { font-size: 64px; }
.title {
  font-size: 20px;
  font-weight: 600;
  margin: 12px 0 8px;
}
.desc {
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>