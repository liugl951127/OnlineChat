<template>
  <div class="screen-share">
    <!-- 坐席：发起按钮 -->
    <el-button v-if="role === 'agent' && !active" type="primary" plain
      :icon="Share" @click="onInitiate">发起屏幕共享</el-button>

    <!-- 客户：接听弹窗 -->
    <el-dialog v-model="inviteVisible" title="坐席请求屏幕共享" width="320px" :show-close="false">
      <p>坐席 <b>{{ inviter }}</b> 请求与您共享屏幕（协助解决问题）</p>
      <p style="color:#8c8c8c;font-size:12px">点击"接受"后会启动屏幕录制</p>
      <template #footer>
        <el-button @click="onReject">拒绝</el-button>
        <el-button type="primary" @click="onAccept">接受</el-button>
      </template>
    </el-dialog>

    <!-- 共享中状态 -->
    <div v-if="active" class="sharing-bar">
      <el-tag type="success" effect="dark">🔴 屏幕共享中 {{ durationStr }}</el-tag>
      <el-button size="small" type="danger" @click="onEnd">结束</el-button>
    </div>

    <!-- 远端视频 -->
    <video v-if="role === 'customer'" ref="remoteVideo" autoplay playsinline
      style="width:100%;max-width:600px;border-radius:8px;margin-top:8px" />
    <video v-if="role === 'agent'" ref="localPreview" autoplay muted playsinline
      style="width:100%;max-width:200px;border-radius:8px;margin-top:8px" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Share } from '@element-plus/icons-vue'
import { media } from '../api'

const props = defineProps({
  role: { type: String, default: 'agent' },
  sessionId: [Number, String],
  agentUsername: String,
  customerId: String
})

const active = ref(false)
const inviteVisible = ref(false)
const inviter = ref('')
const shareId = ref(null)
const remoteVideo = ref(null)
const localPreview = ref(null)

let peerConnection = null
let localStream = null
let startTs = 0
let durationTimer = null
const durationSec = ref(0)
const durationStr = computed(() => {
  const m = Math.floor(durationSec.value / 60).toString().padStart(2, '0')
  const s = (durationSec.value % 60).toString().padStart(2, '0')
  return `${m}:${s}`
})

const rtcConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' }
  ]
}

onMounted(() => {
  let attempts = 0
  const timer = setInterval(() => {
    attempts++
    const stomp = window.__stompClient
    if (stomp) {
      clearInterval(timer)
      stomp.subscribe('/user/queue/screen-share', msg => onSignal(JSON.parse(msg.body)))
    } else if (attempts > 30) {
      clearInterval(timer)
    }
  }, 500)
})

onBeforeUnmount(() => {
  cleanup()
})

async function onInitiate() {
  try {
    const resp = await media.initiateShare({
      sessionId: props.sessionId,
      agent: props.agentUsername,
      customer: props.customerId
    })
    shareId.value = resp.data.id
    inviter.value = props.agentUsername
    inviteVisible.value = true
    ElMessage.info('已发送屏幕共享邀请')
  } catch (e) {
    ElMessage.error('发起失败：' + e.message)
  }
}

async function onAccept() {
  inviteVisible.value = false
  try {
    localStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
    if (localPreview.value) {
      localPreview.value.srcObject = localStream
    }
    peerConnection = new RTCPeerConnection(rtcConfig)

    localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream))

    peerConnection.onicecandidate = e => {
      if (e.candidate) {
        media.relayIce(shareId.value, 'customer', JSON.stringify(e.candidate))
      }
    }

    peerConnection.ontrack = e => {
      if (remoteVideo.value) remoteVideo.value.srcObject = e.streams[0]
    }

    const offer = await peerConnection.createOffer()
    await peerConnection.setLocalDescription(offer)

    await media.acceptShare(shareId.value, JSON.stringify(offer))
    active.value = true
    startTimer()
  } catch (err) {
    ElMessage.error('权限被拒绝或不支持：' + err.message)
    cleanup()
  }
}

async function onReject() {
  inviteVisible.value = false
  if (shareId.value) await media.rejectShare(shareId.value)
  shareId.value = null
}

function onEnd() {
  if (shareId.value) media.endShare(shareId.value)
  cleanup()
}

async function onSignal(signal) {
  try {
    if (signal.type === 'SCREEN_SHARE_INVITE' && props.role === 'customer') {
      shareId.value = signal.shareId
      inviter.value = signal.agent
      inviteVisible.value = true
    } else if (signal.type === 'SCREEN_SHARE_ACCEPTED' && props.role === 'agent') {
      const answer = JSON.parse(signal.sdpAnswer)
      await peerConnection.setRemoteDescription(answer)
      active.value = true
      startTimer()
    } else if (signal.type === 'SCREEN_SHARE_REJECTED') {
      ElMessage.warning('客户拒绝了屏幕共享')
      shareId.value = null
    } else if (signal.type === 'SCREEN_SHARE_ENDED') {
      ElMessage.info('屏幕共享已结束')
      cleanup()
    } else if (signal.type === 'ICE_CANDIDATE') {
      const cand = JSON.parse(signal.candidate)
      if (peerConnection) {
        await peerConnection.addIceCandidate(cand)
      }
    }
  } catch (e) {
    console.error('[ScreenShare] signal error', e)
  }
}

function startTimer() {
  startTs = Date.now()
  durationTimer = setInterval(() => {
    durationSec.value = Math.floor((Date.now() - startTs) / 1000)
  }, 1000)
}

function cleanup() {
  active.value = false
  durationSec.value = 0
  if (durationTimer) clearInterval(durationTimer)
  if (localStream) {
    localStream.getTracks().forEach(t => t.stop())
    localStream = null
  }
  if (peerConnection) {
    peerConnection.close()
    peerConnection = null
  }
  shareId.value = null
}
</script>

<style scoped>
.screen-share { padding: 4px 0; }
.sharing-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px;
  background: #fff2f0;
  border-radius: 4px;
}
</style>