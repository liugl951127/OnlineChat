<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ticket } from '@/api'

/**
 * 工单面板组件（v1.9.0）
 *
 * 客户视角：创建 / 查看我的工单
 * 坐席视角：队列 / 我的工单 / 处理
 */

const props = defineProps({
  customerId: { type: String, default: '' },
  role: { type: String, default: 'CUSTOMER' }  // CUSTOMER / AGENT
})
const emit = defineEmits(['close', 'ticket-updated'])

const activeTab = ref(props.role === 'AGENT' ? 'queue' : 'my')
const tickets = ref([])
const selectedTicket = ref(null)
const ticketReplies = ref([])
const showCreate = ref(false)
const showDetail = ref(false)
const newTicket = reactive({
  title: '',
  description: '',
  category: 'GENERAL',
  priority: 'NORMAL',
  customerId: ''
})
const replyText = ref('')

onMounted(async () => {
  await loadList()
})

async function loadList() {
  try {
    let res
    if (activeTab.value === 'queue') {
      res = await ticket.queue()
    } else if (activeTab.value === 'mine') {
      res = await ticket.mine()
    } else {
      const cid = props.customerId || (newTicket.customerId)
      res = await ticket.list(cid)
    }
    tickets.value = res.data || []
  } catch (e) {}
}

async function switchTab(tab) {
  activeTab.value = tab
  await loadList()
}

async function openDetail(t) {
  try {
    const { data } = await ticket.detail(t.ticketNo)
    selectedTicket.value = data.ticket
    ticketReplies.value = data.replies || []
    showDetail.value = true
  } catch (e) {}
}

async function doCreate() {
  if (!newTicket.title) {
    return ElMessage.warning('请填写标题')
  }
  try {
    const payload = { ...newTicket }
    if (!payload.customerId) payload.customerId = props.customerId
    const { data } = await ticket.create(payload)
    ElMessage.success(`工单已创建：${data.ticketNo}`)
    showCreate.value = false
    newTicket.title = ''
    newTicket.description = ''
    emit('ticket-updated', data)
    await loadList()
  } catch (e) {}
}

async function doAction(action, ticketNo) {
  try {
    let res
    if (action === 'assign') res = await ticket.assign(ticketNo)
    else if (action === 'start') res = await ticket.start(ticketNo)
    else if (action === 'resolve') res = await ticket.resolve(ticketNo)
    else if (action === 'close') res = await ticket.close(ticketNo)
    else if (action === 'cancel') {
      await ElMessageBox.confirm('确认取消该工单？', '提示', { type: 'warning' })
      res = await ticket.cancel(ticketNo)
    }
    ElMessage.success('操作成功')
    if (showDetail.value) await openDetail(selectedTicket.value)
    await loadList()
  } catch (e) {}
}

async function doReply() {
  if (!replyText.value.trim()) return
  try {
    await ticket.reply(selectedTicket.value.ticketNo, {
      content: replyText.value
    })
    replyText.value = ''
    await openDetail(selectedTicket.value)
  } catch (e) {}
}

const statusLabel = (s) => ({
  OPEN: '待分配', ASSIGNED: '已分配', PROCESSING: '处理中',
  RESOLVED: '已解决', CLOSED: '已关闭', CANCELLED: '已取消'
}[s] || s)

const statusColor = (s) => ({
  OPEN: 'warning', ASSIGNED: 'primary', PROCESSING: '',
  RESOLVED: 'success', CLOSED: 'info', CANCELLED: 'danger'
}[s] || '')

const priorityColor = (p) => ({
  URGENT: 'danger', HIGH: 'warning', NORMAL: '', LOW: 'info'
}[p] || '')

const categoryLabel = (c) => ({
  GENERAL: '一般咨询', COMPLAINT: '投诉', CONSULT: '业务咨询', BUG: '问题反馈'
}[c] || c)
</script>

<template>
  <div class="ticket-panel">
    <div class="panel-header">
      <h3>🎫 工单系统</h3>
      <el-button text @click="emit('close')">关闭 ✕</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="switchTab">
      <el-tab-pane v-if="role === 'AGENT'" label="📋 排队" name="queue" />
      <el-tab-pane v-if="role === 'AGENT'" label="👤 我的" name="mine" />
      <el-tab-pane label="📦 我的工单" name="my" />
      <template #append>
        <el-button type="primary" size="small" @click="showCreate = true">+ 新建工单</el-button>
      </template>
    </el-tabs>

    <div class="ticket-list">
      <el-empty v-if="tickets.length === 0" description="暂无工单" />
      <div
        v-for="t in tickets"
        :key="t.id"
        class="ticket-item"
        @click="openDetail(t)"
      >
        <div class="ti-header">
          <span class="ti-no">{{ t.ticketNo }}</span>
          <el-tag :type="statusColor(t.status)" size="small">{{ statusLabel(t.status) }}</el-tag>
          <el-tag :type="priorityColor(t.priority)" size="small" effect="plain">{{ t.priority }}</el-tag>
        </div>
        <div class="ti-title">{{ t.title }}</div>
        <div class="ti-meta">
          <span>{{ categoryLabel(t.category) }}</span>
          <span>·</span>
          <span>{{ t.createdAt }}</span>
          <span v-if="t.agentUsername">· 坐席 {{ t.agentUsername }}</span>
        </div>
      </div>
    </div>

    <!-- 创建工单 -->
    <el-dialog v-model="showCreate" title="新建工单" width="500px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="newTicket.title" placeholder="简要描述你的问题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="newTicket.category">
            <el-option label="一般咨询" value="GENERAL" />
            <el-option label="投诉" value="COMPLAINT" />
            <el-option label="业务咨询" value="CONSULT" />
            <el-option label="问题反馈" value="BUG" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="newTicket.priority">
            <el-radio label="LOW">低</el-radio>
            <el-radio label="NORMAL">中</el-radio>
            <el-radio label="HIGH">高</el-radio>
            <el-radio label="URGENT">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="详情">
          <el-input v-model="newTicket.description" type="textarea" :rows="4" placeholder="详细描述问题..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="doCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 工单详情 -->
    <el-dialog v-model="showDetail" :title="`工单 ${selectedTicket?.ticketNo}`" width="700px">
      <div v-if="selectedTicket">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题">{{ selectedTicket.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusColor(selectedTicket.status)">{{ statusLabel(selectedTicket.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryLabel(selectedTicket.category) }}</el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag :type="priorityColor(selectedTicket.priority)">{{ selectedTicket.priority }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ selectedTicket.customerId }}</el-descriptions-item>
          <el-descriptions-item label="坐席">{{ selectedTicket.agentUsername || '未分配' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedTicket.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="SLA截止">{{ selectedTicket.slaDeadline }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="selectedTicket.description" class="t-desc">
          <strong>描述：</strong>{{ selectedTicket.description }}
        </div>

        <h4 style="margin: 16px 0 8px;">💬 对话</h4>
        <div class="reply-list">
          <div v-for="r in ticketReplies" :key="r.id" class="reply-item" :class="{ me: r.fromUser === (role === 'AGENT' ? selectedTicket.agentUsername : selectedTicket.customerId) }">
            <div class="reply-header">
              <span class="reply-user">{{ r.fromUser }}</span>
              <span class="reply-role">{{ r.fromRole }}</span>
              <span class="reply-time">{{ r.createdAt }}</span>
            </div>
            <div class="reply-content">{{ r.content }}</div>
          </div>
        </div>

        <div v-if="selectedTicket.status !== 'CLOSED' && selectedTicket.status !== 'CANCELLED'" class="reply-input">
          <el-input v-model="replyText" type="textarea" :rows="2" placeholder="回复..." />
          <div style="margin-top: 8px; display: flex; gap: 8px;">
            <el-button type="primary" @click="doReply">发送</el-button>
            <template v-if="role === 'AGENT'">
              <el-button v-if="selectedTicket.status === 'OPEN'" @click="doAction('assign', selectedTicket.ticketNo)">认领</el-button>
              <el-button v-if="selectedTicket.status === 'ASSIGNED'" @click="doAction('start', selectedTicket.ticketNo)">开始处理</el-button>
              <el-button v-if="selectedTicket.status === 'PROCESSING'" type="success" @click="doAction('resolve', selectedTicket.ticketNo)">标记解决</el-button>
              <el-button v-if="selectedTicket.status === 'RESOLVED'" @click="doAction('close', selectedTicket.ticketNo)">关闭</el-button>
            </template>
            <el-button type="danger" plain @click="doAction('cancel', selectedTicket.ticketNo)">取消</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.ticket-panel {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
}
.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
  h3 { margin: 0; font-size: 16px; }
}
.ticket-list {
  max-height: 500px;
  overflow-y: auto;
}
.ticket-item {
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: #f0f7ff; }
  .ti-header {
    display: flex; gap: 6px; align-items: center;
    margin-bottom: 6px;
    .ti-no { font-size: 11px; color: #86909c; font-family: monospace; }
  }
  .ti-title { font-size: 14px; font-weight: 500; margin-bottom: 4px; }
  .ti-meta { font-size: 11px; color: #86909c; display: flex; gap: 6px; }
}
.t-desc {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
}
.reply-list {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 12px;
}
.reply-item {
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 6px;
  background: #fafafa;
  &.me { background: #e6f4ff; }
  .reply-header {
    display: flex; gap: 8px; align-items: center;
    margin-bottom: 4px;
    font-size: 12px;
    .reply-user { font-weight: 500; }
    .reply-role { color: #86909c; font-size: 10px; padding: 1px 6px; background: #fff; border-radius: 3px; }
    .reply-time { color: #86909c; margin-left: auto; }
  }
  .reply-content { font-size: 13px; line-height: 1.6; white-space: pre-wrap; }
}
</style>