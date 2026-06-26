// pages/chat/chat.js
const app = getApp()

Page({
  data: {
    messages: [],
    inputText: '',
    sending: false,
    sessionId: '',
    agentName: '',
    statusTip: '',
    lastMsgId: ''
  },

  onShow() {
    this.pollMessages()
    // 每 3 秒轮询
    this.pollTimer = setInterval(() => this.pollMessages(), 3000)
  },

  onUnload() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value })
  },

  async send() {
    const text = this.data.inputText.trim()
    if (!text) return
    this.setData({ sending: true, inputText: '' })
    try {
      // 乐观显示
      const msg = {
        id: 'm-' + Date.now(),
        text: text,
        fromRole: 'CUSTOMER',
        timeText: this.formatTime(Date.now()),
        mine: true
      }
      this.data.messages.push(msg)
      this.setData({ messages: this.data.messages, lastMsgId: 'msg-' + msg.id })
      // 调后端
      const result = await app.request({
        url: '/im/customer/chat',
        method: 'POST',
        data: { text: text }
      })
      // 更新 session 信息
      if (result.session) {
        this.setData({ sessionId: result.session.id })
        if (result.session.agentUsername) {
          this.setData({ agentName: result.session.agentUsername, statusTip: '' })
        }
      }
      // 机器人回复
      if (result.robotMessage) {
        this.data.messages.push({
          id: 'rb-' + Date.now(),
          text: result.robotMessage.text,
          fromRole: 'ROBOT',
          timeText: this.formatTime(Date.now()),
          mine: false
        })
        this.setData({ messages: this.data.messages })
      }
    } catch (e) {
      wx.showToast({ title: e.message || '发送失败', icon: 'none' })
    } finally {
      this.setData({ sending: false })
    }
  },

  async pollMessages() {
    if (!this.data.sessionId) {
      try {
        const session = await app.request({ url: '/im/customer/session/active' })
        this.setData({ sessionId: session.id })
      } catch (e) { return }
    }
    try {
      const result = await app.request({
        url: '/im/message/poll',
        data: { sessionId: this.data.sessionId, lastId: 0 }
      })
      if (result.status === 'NO_AGENT_AVAILABLE') {
        this.setData({ agentName: '', statusTip: '当前无坐席在线' })
      } else if (result.messages && result.messages.length) {
        this.setData({ messages: result.messages, lastMsgId: 'msg-' + result.messages[result.messages.length - 1].id })
      }
    } catch (e) {}
  },

  formatTime(ts) {
    const d = new Date(ts)
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  }
})