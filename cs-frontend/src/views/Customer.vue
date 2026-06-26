<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { im, robot } from '@/api'
import { useUserStore } from '@/store/user'
import { formatTime, safeText, debounce } from '@/utils'

const user = useUserStore()
const messages = ref([])
const inputText = ref('')
const messagesRef = ref(null)
const sending = ref(false)
const sessionId = ref('')
const stompClient = ref(null)
const connected = ref(false)
const showEmoji = ref(false)
const uploadRef = ref(null)
const recallTimers = ref({}) // msgId -> countdown

const quickReplies = [
  '我想咨询一下产品',
  '账单有疑问',
  '需要人工客服',
  '如何充值',
  '订单问题',
  '投诉建议'
]

onMounted(async () => {
  sessionId.value = 'c-' + Math.random().toString(36).slice(2, 12)
  await connectWS()
  await loadHistory()
})

onUnmounted(() => disconnectWS())

async function connectWS() {
  // WebSocket 防注入：仅允许 wss/ws + 同源
  const wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') +
                location.host + '/ws/im?token=' + encodeURIComponent(user.token)
  try {
    const SockJS = (await import('sockjs-client')).default
    const Stomp = (await import('stompjs')).default
    const sock = new SockJS('/ws/im')
    stompClient.value = Stomp.over(sock)
    stompClient.value.debug = null
    stompClient.value.connect({ Authorization: 'Bearer ' + user.token }, frame => {
      connected.value = true
      stompClient.value.subscribe('/user/queue/messages', msg => {
        try {
          const data = JSON.parse(msg.body)
          // 防 XSS：清理消息内容
          data.text = safeText(data.text)
          messages.value.push(data)
          nextTick(scrollToBottom)
        } catch (e) { console.error('Parse error', e) }
      })
    }, err => {
      connected.value = false
      setTimeout(connectWS, 3000) // 自动重连
    })
  } catch (e) {
    console.error('WS load failed', e)
  }
}

function disconnectWS() {
  try { stompClient.value?.disconnect() } catch {}
}

async function loadHistory() {
  try {
    const { data } = await im.history(sessionId.value)
    if (Array.isArray(data)) {
      data.forEach(m => m.text = safeText(m.text))
      messages.value = data
      nextTick(scrollToBottom)
    }
  } catch {}
}

async function send(textOverride) {
  const text = textOverride ?? inputText.value.trim()
  if (!text || text.length > 2000) {
    if (text.length > 2000) ElMessage.warning('消息过长')
    return
  }
  // 防 XSS：清理后再发送
  const cleanText = safeText(text)
  sending.value = true
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
  try {
    await im.send({ sessionId: sessionId.value, text: cleanText, type: 'text' })
  } catch {} finally {
    sending.value = false
  }
}

async function askRobot() {
  if (!inputText.value.trim()) return
  sending.value = true
  const q = inputText.value.trim()
  const msg = { id: 'r-' + Date.now(), from: 'me', fromName: '我', text: q, type: 'text', time: Date.now(), mine: true }
  messages.value.push(msg)
  inputText.value = ''
  nextTick(scrollToBottom)
  try {
    const { data } = await robot.ask(q, sessionId.value)
    messages.value.push({
      id: 'rb-' + Date.now(),
      from: 'robot',
      fromName: '智能客服',
      text: safeText(data?.answer || '抱歉，我没理解您的问题'),
      type: 'robot',
      time: Date.now()
    })
    nextTick(scrollToBottom)
  } catch {} finally {
    sending.value = false
  }
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const emojis = ['😀','😃','😄','😁','😆','😅','😂','🤣','😊','😇','🙂','🙃','😉','😌','😍','🥰','😘','😗','😙','😚','😋','😛','😝','😜','🤪','🤨','🧐','🤓','😎','🤩','🥳','😏','😒','😞','😔','😟','😕','🙁','☹️','😣','😖','😫','😩','🥺','😢','😭','😤','😠','😡','🤬','🤯','😳','🥵','🥶','😱','😨','😰','😥','😓','🤗','🤔','🤭','🤫','🤥','😶','😐','😑','😬','🙄','😯','😦','😧','😮','😲','🥱','😴','🤤','😪','😵','🤐','🥴','🤢','🤮','🤧','😷','🤒','🤕','🤑','🤠']

function insertEmoji(e) {
  inputText.value += e
}

async function handleUpload(file) {
  // 前端校验：大小 + 类型 + 扩展名
  const allowTypes = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'application/pdf']
  const maxSize = 5 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件超过 5MB')
    return false
  }
  if (!allowTypes.includes(file.type)) {
    ElMessage.error('不支持的文件类型')
    return false
  }
  const ext = file.name.split('.').pop().toLowerCase()
  const allowExts = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf']
  if (!allowExts.includes(ext)) {
    ElMessage.error('不允许的扩展名')
    return false
  }
  try {
    const fd = new FormData()
    fd.append('file', file)
    const { data } = await im.upload(fd)
    messages.value.push({
      id: 'up-' + Date.now(),
      from: 'me', fromName: '我',
      type: data?.mime?.startsWith('image/') ? 'image' : 'file',
      url: data?.url,
      name: file.name,
      size: file.size,
      time: Date.now(), mine: true
    })
    nextTick(scrollToBottom)
  } catch (e) { /* 上报已在拦截器 */ }
  return false // 阻止 ElUpload 默认上传
}

async function recall(msg) {
  if (!msg.mine) return
  if (Date.now() - msg.time > 2 * 60 * 1000) {
    return ElMessage.warning('超过 2 分钟无法撤回')
  }
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

function fileType(mime) {
  return mime?.startsWith('image/') ? 'image' : 'file'
}
</script>

<template>
  <div class="customer">
    <header class="topbar">
      <div class="brand" @click="$router.push('/')">
        <div class="logo">💬</div>
        <span>在线客服</span>
      </div>
      <div class="status">
        <el-tag :type="connected ? 'success' : 'danger'" size="small" effect="dark">
          <el-icon style="vertical-align: -2px"><CircleCheck v-if="connected" /><CircleClose v-else /></el-icon>
          {{ connected ? '已连接' : '连接中' }}
        </el-tag>
        <span class="user">{{ user.profile?.name || '访客' }}</span>
      </div>
    </header>

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
              {{ m.text }}
            </div>
            <div v-else-if="m.type === 'image'" class="msg-bubble msg-image" :class="m.mine ? 'msg-mine' : 'msg-other'">
              <el-image :src="m.url" :preview-src-list="[m.url]" fit="cover" style="max-width: 200px; border-radius: 8px;" />
            </div>
            <div v-else-if="m.type === 'file'" class="msg-bubble" :class="m.mine ? 'msg-mine' : 'msg-other'">
              <el-link :href="m.url" :underline="false" target="_blank" rel="noopener noreferrer">
                <el-icon><Document /></el-icon> {{ m.name || '文件' }}
              </el-link>
            </div>
            <div v-if="m.reaction" class="reaction">👍 {{ m.reaction }}</div>
            <div class="actions" v-if="m.mine && !m.recalled && Date.now() - m.time < 2 * 60 * 1000">
              <el-link type="primary" :underline="false" size="small" @click="recall(m)">撤回</el-link>
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
      </div>
      <el-input v-model="inputText" type="textarea" :rows="2" placeholder="输入消息..." resize="none" maxlength="2000" show-word-limit @keydown.enter.exact.prevent="send()" />
      <div class="actions">
        <el-button @click="askRobot" :disabled="sending">问智能客服</el-button>
        <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
      </div>
    </div>
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

  .brand {
    display: flex; align-items: center; gap: 8px;
    cursor: pointer;
    font-weight: 600;
  }
  .logo {
    width: 32px; height: 32px;
    background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
    border-radius: 8px;
    display: flex; align-items: center; justify-content: center;
    font-size: 16px; color: #fff;
  }
  .status {
    display: flex; align-items: center; gap: 12px;
  }
  .user {
    font-size: 13px; color: #1f2329;
  }
}

.quick-bar {
  display: flex; gap: 6px;
  padding: 8px 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  overflow-x: auto;

  .el-button {
    flex-shrink: 0;
    font-size: 12px;
  }
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.msg-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  animation: fadeInUp 0.2s ease;

  &.mine {
    flex-direction: row-reverse;
  }

  .msg-content {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-width: 60%;
  }

  .name {
    font-size: 11px;
    color: #86909c;
    margin-bottom: 2px;
  }

  .msg-bubble {
    padding: 10px 14px;
    border-radius: 12px;
    line-height: 1.5;
    word-break: break-word;
    font-size: 14px;
  }

  .msg-mine {
    background: #1677ff;
    color: #fff;
    border-bottom-right-radius: 2px;
  }

  .msg-other {
    background: #fff;
    color: #1f2329;
    border-bottom-left-radius: 2px;
    border: 1px solid #e5e6eb;
  }

  .msg-image {
    padding: 4px;
    background: transparent !important;
    border: none !important;
  }

  .msg-recalled {
    background: #f5f5f5 !important;
    color: #86909c !important;
    font-size: 12px;
    font-style: italic;
  }

  .reaction {
    font-size: 12px;
    color: #86909c;
    margin-top: 2px;
  }

  .actions {
    font-size: 12px;
  }

  .time {
    font-size: 11px;
    color: #c9cdd4;
  }
}

.input-bar {
  background: #fff;
  border-top: 1px solid #e5e6eb;
  padding: 8px 20px 12px;

  .toolbar {
    display: flex;
    gap: 4px;
    margin-bottom: 4px;
  }

  .actions {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
    margin-top: 8px;
  }
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 240px;
  overflow-y: auto;

  .emoji {
    cursor: pointer;
    font-size: 22px;
    padding: 4px;
    text-align: center;
    border-radius: 4px;

    &:hover {
      background: #f5f5f5;
    }
  }
}
</style>