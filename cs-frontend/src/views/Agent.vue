<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { im, agent as agentApi, robot } from '@/api'
import { useUserStore } from '@/store/user'
import ProductPanel from '@/components/ProductPanel.vue'
import AiAssistantPanel from '@/components/AiAssistantPanel.vue'
import ScreenShare from '@/components/ScreenShare.vue'
import VoiceRecorder from '@/components/VoiceRecorder.vue'
import { formatTime, safeText, debounce } from '@/utils'

const user = useUserStore()

// ============= 会话列表 =============
const sessions = ref([])
const activeId = ref('')
const loading = ref(false)
const filter = ref('all') // all | waiting | mine
const stompClient = ref(null)
const connected = ref(false)

// ============= 当前会话消息 =============
const messages = ref([])
const messagesRef = ref(null)
const inputText = ref('')
const sending = ref(false)
const showEmoji = ref(false)
const showTemplates = ref(false)
const showProductPanel = ref(false)

// ============= 模板库 =============
const templates = ref(JSON.parse(localStorage.getItem('agent_templates') || '[]'))
const newTemplate = ref('')

const addTemplate = () => {
  const t = newTemplate.value.trim()
  if (!t) return
  if (templates.value.includes(t)) return ElMessage.warning('已存在')
  templates.value.push(t)
  localStorage.setItem('agent_templates', JSON.stringify(templates.value))
  newTemplate.value = ''
}
const removeTemplate = (t) => {
  templates.value = templates.value.filter(x => x !== t)
  localStorage.setItem('agent_templates', JSON.stringify(templates.value))
}

// ============= Emoji =============
const emojis = ['😀','😃','😄','😁','😆','😅','😂','🤣','😊','😇','🙂','🙃','😉','😌','😍','🥰','😘','😗','😙','😚','😋','😛','😝','😜','🤪','🤨','🧐','🤓','😎','🤩','🥳','😏','😒','😞','😔','😟','😕','🙁','☹️','😣','😖','😫','😩','🥺','😢','😭','😤','😠','😡','🤬','🤯','😳','🥵','🥶','😱','😨','😰','😥','😓','🤗','🤔','🤭','🤫','🤥','😶','😐','😑','😬','🙄','😯','😦','😧','😮','😲','🥱','😴','🤤','😪','😵','🤐','🥴','🤢','🤮','🤧','😷','🤒','🤕','🤑','🤠','👍','👎','👏','🙏','💪','🤝','✌️','🤞','🤟','🤘','👌','🤌','🤏','👈','👉','👆','👇','☝️','💯','💢','💥','💫','💦','💨','🕳️','💬','🗨️','🗯️','💭','💤','👀','👁️','👅','👄','👶','🧒','👦','👧','👨','👩','🧓','👴','👵']

// ============= 生命周期 =============
onMounted(async () => {
  await loadSessions()
  await connectWS()
  if (sessions.value.length > 0) selectSession(sessions.value[0])
})
onUnmounted(() => disconnectWS())

async function loadSessions() {
  loading.value = true
  try {
    const { data } = await agentApi.sessions()
    sessions.value = data || []
  } finally {
    loading.value = false
  }
}

async function connectWS() {
  try {
    const SockJS = (await import('sockjs-client')).default
    const Stomp = (await import('stompjs')).default
    const sock = new SockJS('/ws/im')
    stompClient.value = Stomp.over(sock)
    stompClient.value.debug = null
    window.__stompClient = stompClient.value  // 暴露给全局组件
    stompClient.value.connect({ Authorization: 'Bearer ' + user.token }, () => {
      connected.value = true
      stompClient.value.subscribe('/user/queue/agent-messages', m => {
        try {
          const data = JSON.parse(m.body)
          data.text = safeText(data.text)
          // 推入对应会话
          const sess = sessions.value.find(s => s.id === data.sessionId)
          if (sess) {
            sess.lastMsg = data.text || '[文件]'
            sess.lastTime = data.time
            sess.unread = (sess.unread || 0) + 1
          }
          if (data.sessionId === activeId.value) {
            messages.value.push(data)
            nextTick(scrollToBottom)
          }
        } catch {}
      })
    }, () => {
      connected.value = false
      setTimeout(connectWS, 3000)
    })
  } catch {}
}

function disconnectWS() {
  try { stompClient.value?.disconnect() } catch {}
}

async function selectSession(s) {
  activeId.value = s.id
  s.unread = 0
  loading.value = true
  try {
    const { data } = await im.history(s.id)
    if (Array.isArray(data)) {
      data.forEach(m => m.text = safeText(m.text))
      messages.value = data
    } else {
      messages.value = []
    }
    nextTick(scrollToBottom)
  } finally {
    loading.value = false
  }
}

async function takeSession(s) {
  await agentApi.take(s.id)
  s.status = 'mine'
  ElMessage.success('已接入会话')
  selectSession(s)
}

const filteredSessions = computed(() => {
  if (filter.value === 'waiting') return sessions.value.filter(s => s.status === 'waiting')
  if (filter.value === 'mine') return sessions.value.filter(s => s.status === 'mine')
  return sessions.value
})

// ============= 消息操作 =============
async function send(textOverride) {
  const text = textOverride ?? inputText.value.trim()
  if (!text || text.length > 2000) {
    if (text.length > 2000) ElMessage.warning('消息过长')
    return
  }
  const cleanText = safeText(text)
  sending.value = true
  const msg = {
    id: 'm-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    sessionId: activeId.value,
    from: user.profile?.id || 'agent',
    fromName: user.profile?.name || '客服',
    text: cleanText,
    type: 'text',
    time: Date.now(),
    mine: true
  }
  messages.value.push(msg)
  inputText.value = ''
  showEmoji.value = false
  showTemplates.value = false
  nextTick(scrollToBottom)
  try {
    await im.send({ sessionId: activeId.value, text: cleanText, type: 'text' })
  } finally {
    sending.value = false
  }
}

async function recall(msg) {
  if (Date.now() - msg.time > 2 * 60 * 1000) return ElMessage.warning('超过 2 分钟无法撤回')
  await ElMessageBox.confirm('撤回这条消息？', '提示')
  try {
    await im.recall(msg.id)
    msg.recalled = true
  } catch {}
}

async function react(msg, emoji) {
  try {
    await im.react(msg.id, emoji)
    msg.reaction = emoji
  } catch {}
}

function useTemplate(t) {
  inputText.value = t
  showTemplates.value = false
}

function scrollToBottom() {
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}

async function handleUpload(file) {
  const allowTypes = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'application/pdf']
  if (file.size > 5 * 1024 * 1024) { ElMessage.error('文件超过 5MB'); return false }
  if (!allowTypes.includes(file.type)) { ElMessage.error('不支持的文件类型'); return false }
  try {
    const fd = new FormData()
    fd.append('file', file)
    const { data } = await im.upload(fd)
    messages.value.push({
      id: 'up-' + Date.now(),
      sessionId: activeId.value,
      from: user.profile?.id,
      fromName: user.profile?.name,
      type: data?.mime?.startsWith('image/') ? 'image' : 'file',
      url: data?.url,
      name: file.name,
      size: file.size,
      time: Date.now(),
      mine: true
    })
    nextTick(scrollToBottom)
  } catch {}
  return false
}

function insertEmoji(e) {
  inputText.value += e
}

function endSession() {
  ElMessageBox.confirm('结束当前会话？', '提示', { type: 'warning' })
    .then(async () => {
      await im.send({ sessionId: activeId.value, type: 'system', text: '会话已结束' })
      activeId.value = ''
      messages.value = []
      loadSessions()
    }).catch(() => {})
}

const currentCustomer = computed(() => {
  const s = sessions.value.find(s => s.id === activeId.value)
  return s ? { id: s.customerId || s.customerName, name: s.customerName } : null
})

function onUseAiSuggestion(suggestion) {
  inputText.value = suggestion.content
  ElMessage.success('已采纳 AI 推荐到输入框，点击发送即可')
}

function onUseAiModified(content) {
  inputText.value = content
  ElMessage.success('已使用修改版本')
}

function onVoiceUploaded(voice) {
  messages.value.push({
    id: 'voice-' + voice.id,
    sessionId: activeId.value,
    from: user.profile?.name,
    fromName: user.profile?.name || '我',
    text: `[语音 ${voice.durationSec}s]`,
    type: 'voice',
    mine: true,
    time: Date.now()
  })
}

function onPurchased(data) {
  // 购买成功后在聊天中发一条系统消息
  if (data.orderNo && activeId.value) {
    messages.value.push({
      id: 'sys-' + Date.now(),
      sessionId: activeId.value,
      from: 'system',
      fromName: '系统',
      text: `🛍️ 订单 ${data.orderNo} 已成交，金额 ¥${data.amount || ''}。可以在「我的持仓」查看。`,
      type: 'system',
      time: Date.now()
    })
  }
}
</script>

<template>
  <div class="agent-app">
    <aside class="sider">
      <div class="me">
        <el-avatar :size="40">{{ (user.profile?.name || '客').slice(0, 1) }}</el-avatar>
        <div class="info">
          <div class="name">{{ user.profile?.name || '客服' }}</div>
          <el-tag :type="connected ? 'success' : 'danger'" size="small" effect="dark">
            {{ connected ? '在线' : '离线' }}
          </el-tag>
        </div>
      </div>
      <el-radio-group v-model="filter" size="small" class="filter">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="waiting">排队</el-radio-button>
        <el-radio-button label="mine">我的</el-radio-button>
      </el-radio-group>
      <div class="session-list">
        <div v-if="loading && sessions.length === 0" class="empty">
          <el-icon class="is-loading"><Loading /></el-icon>
          加载中...
        </div>
        <div v-else-if="filteredSessions.length === 0" class="empty">暂无会话</div>
        <div v-for="s in filteredSessions" :key="s.id"
             class="session-item" :class="{ active: activeId === s.id }"
             @click="selectSession(s)">
          <el-badge :value="s.unread" :max="99" :hidden="!s.unread">
            <el-avatar :size="36">{{ (s.customerName || '客').slice(0, 1) }}</el-avatar>
          </el-badge>
          <div class="meta">
            <div class="name">{{ s.customerName || '客户' }}</div>
            <div class="last">{{ s.lastMsg || '...' }}</div>
          </div>
          <div class="right">
            <el-tag size="small" :type="s.status === 'waiting' ? 'warning' : (s.status === 'mine' ? 'success' : 'info')">
              {{ s.status === 'waiting' ? '排队' : (s.status === 'mine' ? '我的' : '已结束') }}
            </el-tag>
            <el-button v-if="s.status === 'waiting'" size="small" type="primary" @click.stop="takeSession(s)">接听</el-button>
          </div>
        </div>
      </div>
      <div class="logout">
        <el-button text @click="$router.push('/')">返回首页</el-button>
      </div>
    </aside>

    <main class="main">
      <div v-if="!activeId" class="placeholder">
        <el-empty description="选择左侧会话开始服务" />
      </div>
      <template v-else>
        <header class="main-header">
          <div>
            <span class="title">会话 #{{ activeId.slice(-6) }}</span>
          </div>
          <div class="ops">
            <el-button @click="endSession" type="danger" plain>结束会话</el-button>
          </div>
        </header>

        <div v-if="showProductPanel && currentCustomer" class="product-wrapper">
          <ProductPanel :customer-id="currentCustomer.id" :agent-username="user.profile?.name" @close="showProductPanel = false" @purchased="onPurchased" />
        </div>
        <div class="messages" ref="messagesRef">
          <AiAssistantPanel
            :agent-username="user.profile?.name"
            :customer-id="currentCustomer?.id"
            :session-id="activeId"
            @use="onUseAiSuggestion"
            @use-modified="onUseAiModified" />

          <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.mine }">
            <el-avatar :size="36">{{ (m.fromName || '?').slice(0, 1) }}</el-avatar>
            <div class="msg-content">
              <div class="name">{{ m.fromName }}</div>
              <div v-if="m.recalled" class="msg-bubble msg-recalled">消息已撤回</div>
              <template v-else>
                <div v-if="m.type === 'text' || m.type === 'robot'" class="msg-bubble" :class="m.mine ? 'msg-mine' : 'msg-other'">
                  {{ m.text }}
                </div>
                <div v-else-if="m.type === 'image'" class="msg-bubble msg-image" :class="m.mine ? 'msg-mine' : 'msg-other'">
                  <el-image :src="m.url" :preview-src-list="[m.url]" fit="cover" style="max-width: 220px; border-radius: 8px;" />
                </div>
                <div v-else-if="m.type === 'file'" class="msg-bubble" :class="m.mine ? 'msg-mine' : 'msg-other'">
                  <el-link :href="m.url" :underline="'never'" target="_blank" rel="noopener noreferrer">
                    <el-icon><Document /></el-icon> {{ m.name || '文件' }}
                  </el-link>
                </div>
                <div v-else-if="m.type === 'system'" class="msg-system">{{ m.text }}</div>
                <div v-if="m.reaction" class="reaction">{{ m.reaction }}</div>
              </template>
              <div class="time">{{ formatTime(m.time) }}</div>
              <div v-if="m.mine && !m.recalled && Date.now() - m.time < 2 * 60 * 1000" class="actions">
                <el-link type="primary" :underline="'never'" size="small" @click="recall(m)">撤回</el-link>
              </div>
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
            <el-upload :show-file-list="false" :before-upload="handleUpload">
              <el-button text :icon="'Upload'" />
            </el-upload>
            <el-button text :icon="'ShoppingCart'" @click="showProductPanel = !showProductPanel" title="金融产品购买" />
            <ScreenShare role="agent"
              :session-id="activeId"
              :agent-username="user.profile?.name"
              :customer-id="currentCustomer?.id" />
            <VoiceRecorder :session-id="activeId"
              :from-id="user.profile?.name"
              from-role="AGENT" @uploaded="onVoiceUploaded" />
            <el-popover :width="320" trigger="click" v-model:visible="showTemplates" title="快捷回复模板">
              <template #reference>
                <el-button text :icon="'ChatLineSquare'" />
              </template>
              <div class="templates">
                <div v-for="t in templates" :key="t" class="tpl-item">
                  <span @click="useTemplate(t)">{{ t }}</span>
                  <el-button text :icon="'Delete'" size="small" @click="removeTemplate(t)" />
                </div>
                <el-input v-model="newTemplate" size="small" placeholder="新增模板" @keydown.enter="addTemplate">
                  <template #append><el-button @click="addTemplate">添加</el-button></template>
                </el-input>
              </div>
            </el-popover>
          </div>
          <el-input v-model="inputText" type="textarea" :rows="3" placeholder="回复客户..." resize="none" maxlength="2000" show-word-limit @keydown.enter.exact.prevent="send()" />
          <div class="actions">
            <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<style lang="scss" scoped>
.agent-app {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

.sider {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e5e6eb;
  display: flex;
  flex-direction: column;

  .me {
    display: flex; align-items: center; gap: 12px;
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;

    .info { flex: 1; }
    .name { font-size: 14px; font-weight: 500; margin-bottom: 2px; }
  }

  .filter {
    margin: 12px;
    width: calc(100% - 24px);
    display: flex;
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
  }

  .empty {
    text-align: center;
    padding: 40px 0;
    color: #86909c;
    font-size: 13px;

    .el-icon { font-size: 24px; margin-bottom: 8px; }
  }

  .session-item {
    display: flex;
    gap: 8px;
    padding: 12px 16px;
    cursor: pointer;
    transition: background 0.15s;
    border-bottom: 1px solid #f5f5f5;

    &:hover, &.active {
      background: #e6f4ff;
    }

    .meta {
      flex: 1;
      min-width: 0;

      .name {
        font-size: 13px;
        font-weight: 500;
        margin-bottom: 2px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .last {
        font-size: 12px;
        color: #86909c;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .right {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
    }
  }

  .logout {
    padding: 12px;
    border-top: 1px solid #f0f0f0;
    text-align: center;
  }
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;

  .title {
    font-size: 14px;
    font-weight: 500;
  }
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}

.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  animation: fadeInUp 0.2s ease;

  &.mine { flex-direction: row-reverse; }

  .msg-content {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-width: 60%;
  }

  .name {
    font-size: 11px;
    color: #86909c;
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

  .msg-system {
    text-align: center;
    color: #86909c;
    font-size: 12px;
    background: #f5f5f5;
    padding: 4px 12px;
    border-radius: 4px;
    align-self: center;
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
  }

  .time {
    font-size: 11px;
    color: #c9cdd4;
  }

  .actions {
    font-size: 12px;
  }
}

.input-bar {
  background: #fff;
  border-top: 1px solid #e5e6eb;
  padding: 8px 24px 12px;

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

.product-wrapper {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fafbfc;
  max-height: 600px;
  overflow-y: auto;
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

    &:hover { background: #f5f5f5; }
  }
}

.templates {
  .tpl-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 8px;
    border-radius: 4px;

    &:hover { background: #f5f5f5; }

    span {
      cursor: pointer;
      flex: 1;
      font-size: 13px;
    }
  }
}
</style>