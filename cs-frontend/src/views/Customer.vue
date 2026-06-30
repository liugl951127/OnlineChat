<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { im, robot, ticket, faq as faqApi, replay, kyc as kycApi } from '@/api'
import { useUserStore } from '@/store/user'
import { formatTime, safeText, debounce } from '@/utils'
import { useReplayRecorder } from '@/composables/useReplayRecorder'  // v2.2.78
import KnowledgeBase from '@/components/KnowledgeBase.vue'
import TicketPanel from '@/components/TicketPanel.vue'
import ReplayPanel from '@/components/ReplayPanel.vue'
import KycFlow from '@/components/KycFlow.vue'
import ScreenShare from '@/components/ScreenShare.vue'
import VoiceRecorder from '@/components/VoiceRecorder.vue'

const user = useUserStore()
const errorStore = useErrorStore()

// ============ 消息流（WebSocket 实时 + 历史） ============
const messages = ref([])
const inputText = ref('')
const messagesRef = ref(null)
const sending = ref(false)
const sessionId = ref('')

function onVoiceUploaded(voice) {
  messages.value.push({
    id: 'voice-' + voice.id,
    sessionId: sessionId.value,
    from: user.profile?.id,
    fromName: user.profile?.name || '我',
    text: `[语音 ${voice.durationSec}s]`,
    type: 'voice',
    mine: true,
    time: Date.now()
  })
}
const stompClient = ref(null)
const connected = ref(false)
const showEmoji = ref(false)
const recallTimers = ref({}) // msgId -> countdown

// ============ 会话状态 ============
const sessionInfo = ref(null)          // ChatSession 对象
const hasAgent = ref(false)             // 是否有坐席在线
const agentName = ref('')               // 当前坐席
const statusTip = ref('正在加载...')

// ============ 离线消息（首屏拉一次） ============
const offlineCount = ref(0)
const offlineMsgs = ref([])

// ============ 面板开关（KB / 工单 / 回放 / KYC） ============
const showKB = ref(false)
const showTickets = ref(false)
const showReplay = ref(false)
const showKyc = ref(false)
const replaySessionId = ref(null)
const kycStatus = ref('NOT_STARTED')

// ============ 快捷回复 ============
const quickReplies = [
  '我想咨询一下产品',
  '账单有疑问',
  '需要人工客服',
  '如何充值',
  '订单问题',
  '投诉建议'
]

// ============ 生命周期 ============
onMounted(async () => {
  // v2.3.1: 每个 API 独立 try-catch, 一个失败不影响其他, 用 banner 提示
  await safeCall('加载会话', () => loadSession())
  await safeCall('建立聊天连接', () => connectWS(), 'warning')
  await safeCall('加载历史消息', () => loadHistory())
  await safeCall('拉取离线消息', () => loadOffline())
  // v2.2.78: 启动会话回溯 Recorder (定时截图)
  if (sessionId.value) {
    try { recorder.start() } catch (e) { console.warn('[Recorder]', e) }
  }
})

onUnmounted(() => {
  disconnectWS()
  // v2.2.78: 退出页面时停止 + 触发合成
  if (recorder.isRecording.value) {
    recorder.finish()
  }
  // v2.3.0: 不清 localStorage (留作刷新恢复)
})

// v2.2.78: 会话回溯 Recorder
const recorder = useReplayRecorder({
  sessionId: sessionId.value,
  intervalMs: 5000,
  uploadedBy: 'customer',
  targetDom: null,
  onError: (e) => console.warn('[Replay Recorder]', e)
})

// v2.3.0: messages 变化时持久化 (F2)
watch(messages, (val) => {
  if (val && val.length > 0) saveMessages(val)
}, { deep: true })

// 同步 sessionId 到 recorder (当 loadSession 更新时)
watch(sessionId, (newId) => {
  if (newId && !recorder.isRecording.value) {
    recorder.start()
  }
})

// ============ WebSocket（实时聊天） ============
let stompOverWS = null  // v2.3.0: STOMP-over-ReconnectWS 包装

async function connectWS() {
  try {
    const SockJS = (await import('sockjs-client')).default
    const Stomp = (await import('stompjs')).default
    // v2.3.0: 直接用 SockJS (自带重连), 不再叠加 ReconnectWS
    const sock = new SockJS('/ws/im?customerId=' + encodeURIComponent(user.profile?.id || ''))
    stompClient.value = Stomp.over(sock)
    stompClient.value.debug = null
    stompClient.value.heartbeat.outgoing = 20000  // 20s 客户端心跳
    stompClient.value.heartbeat.incoming = 20000
    window.__stompClient = stompClient.value
    stompClient.value.connect({ Authorization: 'Bearer ' + user.token }, frame => {
      connected.value = true
      // 连上 → 立即 drain 离线消息
      loadOffline()
      saveMessages(messages.value)
      // 订阅客户频道（接收坐席回复 + 系统消息）
      stompClient.value.subscribe('/user/queue/messages', msg => {
        try {
          const data = JSON.parse(msg.body)
          data.text = safeText(data.text)
          // 状态消息：更新会话状态
          if (data.type === 'AGENT_JOINED') {
            hasAgent.value = true
            agentName.value = data.agentUsername
            statusTip.value = `坐席 ${agentName.value} 正在为你服务`
            ElMessage.success(`坐席 ${agentName.value} 已接入`)
          } else if (data.type === 'AGENT_LEFT') {
            hasAgent.value = false
            agentName.value = ''
            statusTip.value = '坐席已离开，请稍候或转接其他人'
          } else if (data.type === 'ENDED') {
            hasAgent.value = false
            statusTip.value = '会话已结束'
          } else if (data.type === 'MESSAGE_NEW' && data.payload?.type === 'LINK') {
            // v2.3.0: 坐席推链接 - 渲染卡片
            const token = (data.payload.text || '').replace('[LINK]', '').split('|')[0]
            const url = (data.payload.text || '').split('|')[1] || ''
            data.payload.linkCard = { token, url, shortUrl: '/api/im/link/' + token }
            messages.value.push(data)
            nextTick(scrollToBottom)
            return
          }
          // 正常消息
          if (data.text || data.content) {
            messages.value.push(data)
            nextTick(scrollToBottom)
          }
        } catch (e) { console.error('Parse error', e) }
      })
      // 订阅离线消息（重连后批量推送）
      stompClient.value.subscribe('/user/queue/offline', msg => {
        try {
          const data = JSON.parse(msg.body)
          if (Array.isArray(data.messages) && data.messages.length > 0) {
            data.messages.forEach(m => {
              m.text = safeText(m.content)
              m.fromName = m.fromRole === 'AGENT' ? (agentName.value || '坐席') : '系统'
              messages.value.push(m)
            })
            nextTick(scrollToBottom)
            ElMessage.info(`收到 ${data.messages.length} 条离线消息`)
          }
        } catch (e) {}
      })
    }, err => {
      connected.value = false
      console.warn('[WS] disconnected, retry 3s')
      // 重连后会自动触发 loadOffline (在 connect 成功回调里)
      setTimeout(connectWS, 3000)
    })
  } catch (e) {
    console.error('WS load failed', e)
  }
}

function disconnectWS() {
  try { stompClient.value?.disconnect() } catch {}
}

// ============ 历史/会话/离线消息 ============
// v2.3.1: 安全调用 — 失败时推 banner, 不中断后续调用
async function safeCall(title, fn, type = 'error') {
  try {
    await fn()
  } catch (e) {
    if (e?.message === '未登录' || e?.response?.status === 401) return  // handle401 已处理
    errorStore.push({
      type,
      title,
      message: e?.response?.data?.msg || e?.message || '操作失败',
      source: 'customer-mount'
    })
  }
}

async function loadSession() {
  // v2.3.0: F2 先恢复 localStorage (防刷新断线)
  const cached = loadLocalSession()
  if (cached.sessionId) {
    sessionId.value = cached.sessionId
    sessionInfo.value = cached.info
    if (cached.info) updateStatusFromSession(cached.info)
  }
  // 恢复 messages 缓存
  const cachedMsgs = loadMessages()
  if (cachedMsgs.length > 0) {
    messages.value = cachedMsgs
  }

  try {
    const { data } = await im.activeSession()
    sessionInfo.value = data
    sessionId.value = data.id
    saveSession(data.id, data)
    updateStatusFromSession(data)
  } catch (e) {
    if (!cached.sessionId) ElMessage.error('加载会话失败')
  }
  // 同时加载 KYC 状态
  try {
    const { data: kycData } = await kycApi.status()
    kycStatus.value = kycData.status
  } catch {}
}

function updateStatusFromSession(s) {
  if (!s) return
  if (s.status === 'IN_SESSION' && s.agentUsername) {
    hasAgent.value = true
    agentName.value = s.agentUsername
    statusTip.value = `坐席 ${s.agentUsername} 正在为你服务`
  } else if (s.status === 'QUEUED') {
    hasAgent.value = false
    statusTip.value = '正在为你排队等待坐席...'
  } else if (s.status === 'ENDED') {
    hasAgent.value = false
    statusTip.value = '会话已结束'
  } else {
    hasAgent.value = false
    statusTip.value = '当前无坐席在线（智能客服模式）'
  }
}

async function loadHistory() {
  if (!sessionId.value) return
  try {
    const { data } = await im.history(sessionId.value)
    if (Array.isArray(data)) {
      data.forEach(m => m.text = safeText(m.text || m.content))
      messages.value = data
      nextTick(scrollToBottom)
    }
  } catch {}
}

async function loadOffline() {
  if (!sessionId.value) return
  try {
    const { data } = await im.drainOffline(sessionId.value)
    offlineMsgs.value = data || []
    offlineCount.value = offlineMsgs.value.length
    if (offlineCount.value > 0) {
      // 把离线消息追加到消息流
      offlineMsgs.value.forEach(m => {
        m.text = safeText(m.content)
        m.fromName = m.fromRole === 'AGENT' ? (agentName.value || '坐席') : '系统'
      })
      messages.value.push(...offlineMsgs.value)
      nextTick(scrollToBottom)
      ElMessage.info(`已加载 ${offlineCount.value} 条离线消息`)
    }
  } catch {}
}

// ============ v2.3.0: 打开链接卡片 ============
async function openLinkCard(card) {
  try {
    const { data } = await linkApi.open(card.token)
    if (data.ok && data.url) {
      window.open(data.url, '_blank', 'noopener,noreferrer')
      ElMessage.success('已打开链接')
    } else {
      ElMessage.error('链接无效')
    }
  } catch (e) {
    ElMessage.error('打开链接失败: ' + (e?.response?.data?.msg || e.message))
  }
}

// ============ 发送消息 ============
async function send(textOverride) {
  const text = textOverride ?? inputText.value.trim()
  if (!text || text.length > 2000) {
    if (text.length > 2000) ElMessage.warning('消息过长')
    return
  }
  const cleanText = safeText(text)
  sending.value = true

  // 乐观添加到 UI
  const msg = {
    id: 'm-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    sessionId: sessionId.value,
    from: user.profile?.id || 'me',
    fromName: user.profile?.name || '我',
    text: cleanText,
    type: 'text',
    time: Date.now(),
    mine: true
  }
  messages.value.push(msg)
  inputText.value = ''
  showEmoji.value = false
  nextTick(scrollToBottom)

  // 通过 WebSocket 发送（v1.9.0：WS 实时）
  try {
    if (stompClient.value && connected.value) {
      stompClient.value.send('/app/customer/chat', {}, JSON.stringify({
        sessionId: Number(sessionId.value),
        text: cleanText,
        type: 'TEXT'
      }))
    } else {
      // WS 未连接 → 降级 REST
      await im.send({ sessionId: Number(sessionId.value), content: cleanText, type: 'TEXT' })
      ElMessage.warning('连接断开，消息通过离线通道发送')
    }
  } catch (e) {
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}

// ============ 智能客服（机器人） ============
async function askRobot() {
  if (!inputText.value.trim()) return
  sending.value = true
  const q = inputText.value.trim()
  const cleanQ = safeText(q)

  // 客户消息
  messages.value.push({
    id: 'r-' + Date.now(), from: 'me', fromName: '我',
    text: cleanQ, type: 'text', time: Date.now(), mine: true
  })
  inputText.value = ''
  nextTick(scrollToBottom)

  try {
    const { data } = await robot.ask(cleanQ, sessionId.value)
    messages.value.push({
      id: 'rb-' + Date.now(),
      from: 'robot', fromName: '智能客服',
      text: safeText(data?.text || '抱歉，我没理解您的问题'),
      type: 'robot', time: Date.now()
    })
    nextTick(scrollToBottom)
  } catch (e) {
    ElMessage.error('智能客服暂时不可用')
  } finally {
    sending.value = false
  }
}

// ============ 转人工 ============
async function transferToAgent() {
  try {
    const { data } = await im.transferToAgent()
    sessionInfo.value = data
    updateStatusFromSession(data)
    ElMessage.success('已为你排队，正在呼叫坐席...')
  } catch (e) {
    ElMessage.error('转人工失败')
  }
}

// ============ 滚动到底部 ============
function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// ============ 表情 + 上传 + 撤回 + 反应 ============
const emojis = ['😀','😃','😄','😁','😆','😅','😂','🤣','😊','😇','🙂','🙃','😉','😌','😍','🥰','😘','😗','😙','😚','😋','😛','😝','😜','🤪','🤨','🧐','🤓','😎','🤩','🥳','😏','😒','😞','😔','😟','😕','🙁','☹️','😣','😖','😫','😩','🥺','😢','😭','😤','😠','😡','🤬','🤯','😳','🥵','🥶','😱','😨','😰','😥','😓','🤗','🤔','🤭','🤫','🤥','😶','😐','😑','😬','🙄','😯','😦','😧','😮','😲','🥱','😴','🤤','😪','😵','🤐','🥴','🤢','🤮','🤧','😷','🤒','🤕','🤑','🤠']

function insertEmoji(e) {
  inputText.value += e
}

async function handleUpload(file) {
  const allowTypes = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'application/pdf']
  const maxSize = 5 * 1024 * 1024
  if (file.size > maxSize) { ElMessage.error('文件超过 5MB'); return false }
  if (!allowTypes.includes(file.type)) { ElMessage.error('不支持的文件类型'); return false }
  const ext = file.name.split('.').pop().toLowerCase()
  if (!['png','jpg','jpeg','gif','webp','pdf'].includes(ext)) { ElMessage.error('不允许的扩展名'); return false }
  try {
    const fd = new FormData()
    fd.append('file', file)
    const { data } = await im.upload(fd)
    messages.value.push({
      id: 'up-' + Date.now(),
      from: 'me', fromName: '我',
      type: data?.mime?.startsWith('image/') ? 'image' : 'file',
      url: data?.url, name: file.name, size: file.size,
      time: Date.now(), mine: true
    })
    nextTick(scrollToBottom)
  } catch (e) {}
  return false
}

async function recall(msg) {
  if (!msg.mine) return
  if (Date.now() - msg.time > 2 * 60 * 1000) return ElMessage.warning('超过 2 分钟无法撤回')
  await ElMessageBox.confirm('确定撤回这条消息？', '提示')
  try {
    await im.recall(msg.id)
    msg.recalled = true
    ElMessage.success('已撤回')
  } catch {}
}

async function react(msg, emoji) {
  try {
    await im.react(msg.id, emoji)
    msg.reaction = emoji
  } catch {}
}

// ============ 面板开关 ============
function openKB() { showKB.value = true }
function closeKB() { showKB.value = false }

function openTickets() { showTickets.value = true }
function closeTickets() { showTickets.value = false }

async function openReplay() {
  if (!sessionId.value) return ElMessage.warning('请先建立会话')
  replaySessionId.value = Number(sessionId.value)
  showReplay.value = true
}
function closeReplay() { showReplay.value = false }

function onKycCompleted() {
  kycStatus.value = 'COMPLETED'
}
</script>

<template>
  <div class="customer">
    <header class="topbar">
      <div class="brand" @click="$router.push('/')">
        <div class="logo">💬</div>
        <span>在线客服</span>
      </div>

      <!-- 工具按钮（KB / 工单 / 回放） -->
      <div class="toolbar-btns">
        <el-button size="small" text @click="openKB">📚 知识库</el-button>
        <el-button size="small" text @click="openTickets">🎫 工单</el-button>
        <el-button size="small" text @click="openReplay">🎬 回放</el-button>
        <el-button size="small" text @click="openKyc">
          {{ kycCompleted ? '✓ 已认证' : '🔐 实名认证' }}
        </el-button>
      </div>

      <div class="status">
        <el-tag :type="connected ? 'success' : 'danger'" size="small" effect="dark">
          <el-icon style="vertical-align: -2px"><CircleCheck v-if="connected" /><CircleClose v-else /></el-icon>
          {{ connected ? '已连接' : '连接中' }}
        </el-tag>
        <span class="user">{{ user.profile?.name || '访客' }}</span>
      </div>
    </header>

    <!-- 状态条幅：无坐席 / 排队中 / 服务中 -->
    <div class="status-banner" :class="{
      'no-agent': !hasAgent && statusTip.includes('无坐席'),
      'queued': statusTip.includes('排队'),
      'in-service': hasAgent,
      'ended': statusTip.includes('结束')
    }">
      <span class="banner-icon">{{ hasAgent ? '🎧' : (statusTip.includes('排队') ? '⏳' : (statusTip.includes('结束') ? '🛑' : '🤖')) }}</span>
      <span class="banner-text">{{ statusTip }}</span>
      <el-button
        v-if="!hasAgent && !statusTip.includes('结束') && !statusTip.includes('排队')"
        type="warning" size="small" round @click="transferToAgent"
      >
        转人工坐席
      </el-button>
      <el-button
        v-if="statusTip.includes('排队')"
        type="info" size="small" round disabled
      >
        排队中...
      </el-button>
    </div>

    <!-- KYC 状态提示 -->
    <div v-if="kycStatus !== 'NOT_STARTED' && kycStatus !== 'COMPLETED'" class="kyc-bar">
      <span class="kyc-icon">🔐</span>
      <span class="kyc-text">实名认证状态：<b>{{ kycStatusLabel(kycStatus) }}</b></span>
      <el-button v-if="kycStatus !== 'AUDITING'" type="primary" size="small" round @click="openKyc">继续认证</el-button>
      <el-button v-else type="info" size="small" round disabled>审核中</el-button>
    </div>
    <div v-if="kycCompleted" class="kyc-bar completed">
      <span class="kyc-icon">✓</span>
      <span class="kyc-text">已完成实名认证（可购买金融产品）</span>
      <el-button size="small" round @click="openKyc">查看详情</el-button>
    </div>
    <div v-if="kycStatus === 'NOT_STARTED'" class="kyc-bar warning">
      <span class="kyc-icon">⚠</span>
      <span class="kyc-text">购买金融产品需先完成实名认证</span>
      <el-button type="warning" size="small" round @click="openKyc">开始认证</el-button>
    </div>

    <div class="quick-bar">
      <el-button v-for="q in quickReplies" :key="q" size="small" plain @click="send(q)">{{ q }}</el-button>
    </div>

    <div class="messages" ref="messagesRef">
      <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.mine }">
        <div v-if="!m.mine" class="avatar">
          <el-avatar :size="32" :src="m.from === 'robot' ? '' : undefined">
            {{ m.from === 'robot' ? '🤖' : (m.fromName || '?').slice(0, 1) }}
          </el-avatar>
        </div>
        <div class="msg-content">
          <div v-if="!m.mine" class="name">{{ m.fromName }}</div>
          <div v-if="m.recalled" class="msg-bubble msg-recalled">消息已撤回</div>
          <template v-else>
            <div v-if="m.type === 'text' || m.type === 'robot'" class="msg-bubble" :class="m.mine ? 'msg-mine' : 'msg-other'">
              {{ m.text || m.content }}
            </div>
            <div v-else-if="m.type === 'image'" class="msg-bubble msg-image" :class="m.mine ? 'msg-mine' : 'msg-other'">
              <el-image :src="m.url" :preview-src-list="[m.url]" fit="cover" style="max-width: 200px; border-radius: 8px;" />
            </div>
            <div v-else-if="m.type === 'file'" class="msg-bubble" :class="m.mine ? 'msg-mine' : 'msg-other'">
              <el-link :href="m.url" :underline="'never'" target="_blank" rel="noopener noreferrer">
                <el-icon><Document /></el-icon> {{ m.name || '文件' }}
              </el-link>
            </div>
            <div v-if="m.reaction" class="reaction">👍 {{ m.reaction }}</div>
            <div class="actions" v-if="m.mine && !m.recalled && Date.now() - m.time < 2 * 60 * 1000">
              <el-link type="primary" :underline="'never'" size="small" @click="recall(m)">撤回</el-link>
            </div>
          </template>
          <div class="time">{{ formatTime(m.time) }}</div>
        </div>
        <div v-if="m.mine" class="avatar">
          <el-avatar :size="32">{{ (user.profile?.name || '我').slice(0, 1) }}</el-avatar>
        </div>
      </div>
    </div>

    <div class="input-bar">
      <div class="toolbar">
        <el-popover :width="320" trigger="click" v-model:visible="showEmoji">
          <template #reference>
            <el-button text :icon="'PictureRounded'" />
          </template>
          <div class="emoji-grid">
            <span v-for="e in emojis" :key="e" class="emoji" @click="insertEmoji(e)">{{ e }}</span>
          </div>
        </el-popover>
        <el-upload :show-file-list="false" :before-upload="handleUpload" :http-request="handleUpload">
          <el-button text :icon="'Upload'" />
        </el-upload>
        <ScreenShare role="customer"
          :session-id="sessionId"
          :agent-username="agentName"
          :customer-id="user.profile?.id" />
        <VoiceRecorder :session-id="sessionId"
          :from-id="user.profile?.id"
          from-role="CUSTOMER" @uploaded="onVoiceUploaded" />
      </div>
      <el-input v-model="inputText" type="textarea" :rows="2" placeholder="输入消息..." resize="none" maxlength="2000" show-word-limit @keydown.enter.exact.prevent="send()" />
      <div class="actions">
        <el-button @click="askRobot" :disabled="sending">问智能客服</el-button>
        <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
      </div>
    </div>

    <!-- KB 知识库弹窗 -->
    <el-dialog v-model="showKB" title="📚 知识库" width="700px" :show-close="true" @close="closeKB">
      <KnowledgeBase :customer-id="user.profile?.id" @close="closeKB" />
    </el-dialog>

    <!-- 工单面板弹窗 -->
    <el-dialog v-model="showTickets" title="🎫 工单系统" width="800px" :show-close="true" @close="closeTickets">
      <TicketPanel
        :customer-id="user.profile?.id"
        role="CUSTOMER"
        @close="closeTickets"
      />
    </el-dialog>

    <!-- 视频回溯弹窗 -->
    <el-dialog v-model="showReplay" title="🎬 视频回溯" width="800px" :show-close="true" @close="closeReplay">
      <ReplayPanel v-if="replaySessionId" :session-id="replaySessionId" @close="closeReplay" />
    </el-dialog>

    <!-- KYC 实名认证弹窗 -->
    <el-dialog v-model="showKyc" title="🔐 实名认证 (KYC)" width="900px" :show-close="true" @close="closeKyc">
      <KycFlow :customer-id="user.profile?.id" @close="closeKyc" @completed="onKycCompleted" />
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.customer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  .brand { display: flex; align-items: center; gap: 8px; cursor: pointer; font-weight: 600; }
  .logo {
    width: 32px; height: 32px;
    background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
    border-radius: 8px;
    display: flex; align-items: center; justify-content: center;
    font-size: 16px; color: #fff;
  }
  .toolbar-btns { display: flex; gap: 4px; }
  .status { display: flex; align-items: center; gap: 12px; }
  .user { font-size: 13px; color: #1f2329; }
}

// ============ 状态条幅 ============
.status-banner {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 20px;
  font-size: 13px;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s;
  .banner-icon { font-size: 18px; }
  .banner-text { flex: 1; }

  // 默认（智能客服模式）
  background: #f0f5ff;
  color: #1677ff;

  // 无坐席
  &.no-agent {
    background: #fff7e6;
    color: #d46b08;
    border-bottom-color: #ffd591;
  }

  // 排队中
  &.queued {
    background: #e6f4ff;
    color: #1677ff;
    border-bottom-color: #91caff;
  }

  // 坐席服务中
  &.in-service {
    background: #f6ffed;
    color: #389e0d;
    border-bottom-color: #b7eb8f;
  }

  // 结束
  &.ended {
    background: #f5f5f5;
    color: #86909c;
    border-bottom-color: #d9d9d9;
  }
}

.kyc-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 20px;
  background: #fff7e6;
  color: #d46b08;
  font-size: 13px;
  border-bottom: 1px solid #ffd591;
  .kyc-icon { font-size: 18px; }
  .kyc-text { flex: 1; }
  &.completed { background: #f6ffed; color: #389e0d; border-bottom-color: #b7eb8f; }
  &.warning { background: #fffbe6; color: #d46b08; }
}

.quick-bar {
  display: flex; gap: 6px;
  padding: 8px 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  overflow-x: auto;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: #f5f7fa;
}

.msg-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  &.mine { flex-direction: row-reverse; }
}
.msg-content {
  max-width: 70%;
  .name { font-size: 11px; color: #86909c; margin-bottom: 2px; padding: 0 4px; }
  .msg-bubble {
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.5;
    word-break: break-word;
    &.msg-mine { background: #1677ff; color: #fff; }
    &.msg-other { background: #fff; color: #1f2329; }
    &.msg-recalled { background: #f5f5f5; color: #86909c; font-style: italic; }
  }
  .reaction { font-size: 11px; color: #86909c; margin-top: 2px; padding: 0 4px; }
  .actions { font-size: 11px; margin-top: 2px; padding: 0 4px; }
  .time { font-size: 10px; color: #c9cdd4; margin-top: 2px; padding: 0 4px; }
}

.input-bar {
  background: #fff;
  border-top: 1px solid #e5e6eb;
  padding: 8px 20px 12px;
  .toolbar { display: flex; gap: 4px; margin-bottom: 4px; }
  .actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 240px;
  overflow-y: auto;
  .emoji { cursor: pointer; font-size: 22px; padding: 4px; text-align: center; border-radius: 4px; &:hover { background: #f5f5f5; } }
}
</style>