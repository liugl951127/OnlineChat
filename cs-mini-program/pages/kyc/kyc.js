// pages/kyc/kyc.js
const app = getApp()

Page({
  data: {
    currentStep: 0,
    steps: [
      { title: '创建申请' },
      { title: '身份证 OCR' },
      { title: '活体检测' },
      { title: '人脸比对' },
      { title: '视频双录' },
      { title: '坐席审核' },
      { title: '绑卡鉴权' }
    ],
    application: null,
    statements: [],
    idCardForm: { frontImgUrl: '', backImgUrl: '' },
    livenessForm: { faceImgUrl: '', actions: [] },
    bankForm: { cardNo: '', cardName: '', idCardNo: '', mobile: '' },
    recorded: false,
    videoDuration: 0,
    recording: false
  },

  onShow() {
    this.loadMy()
    this.loadStatements()
  },

  async loadMy() {
    try {
      const data = await app.request({ url: '/im/kyc/my' })
      this.setData({ application: data })
      if (data && data.status && data.status !== 'REJECTED') {
        const map = {
          'INIT': 0, 'OCR_UPLOADED': 1, 'LIVENESS_PASSED': 2, 'FACE_MATCHED': 3,
          'VIDEO_RECORDED': 4, 'AUDITING': 5, 'APPROVED': 6, 'BANK_BINDING': 6
        }
        this.setData({ currentStep: map[data.status] || 0 })
      }
    } catch (e) {}
  },

  async loadStatements() {
    try {
      const data = await app.request({ url: '/im/kyc/statements' })
      this.setData({ statements: data || [] })
    } catch (e) {}
  },

  async stepCreate() {
    try {
      const data = await app.request({ url: '/im/kyc/create', method: 'POST' })
      this.setData({ application: data, currentStep: 1 })
      wx.showToast({ title: '申请已创建', icon: 'success' })
    } catch (e) {}
  },

  uploadFront() {
    // Mock：直接生成 URL
    this.setData({ 'idCardForm.frontImgUrl': 'https://oss.example.com/idcard/front-' + Date.now() + '.jpg' })
    wx.showToast({ title: '正面已上传', icon: 'success' })
  },
  uploadBack() {
    this.setData({ 'idCardForm.backImgUrl': 'https://oss.example.com/idcard/back-' + Date.now() + '.jpg' })
    wx.showToast({ title: '反面已上传', icon: 'success' })
  },
  uploadFace() {
    this.setData({ 'livenessForm.faceImgUrl': 'https://oss.example.com/face-' + Date.now() + '.jpg' })
  },

  async stepUploadIdcard() {
    const form = this.data.idCardForm
    if (!form.frontImgUrl || !form.backImgUrl) return wx.showToast({ title: '请上传正反面', icon: 'none' })
    try {
      const data = await app.request({ url: '/im/kyc/upload-idcard', method: 'POST', data: form })
      this.setData({ application: data, currentStep: data.status === 'REJECTED' ? 0 : 2 })
    } catch (e) {}
  },

  onCardInput(e) {
    const k = e.currentTarget.dataset.k
    this.setData({ [`bankForm.${k}`]: e.detail.value })
  },

  toggleAction(e) {
    const val = e.currentTarget.dataset.action
    const actions = this.data.livenessForm.actions
    const idx = actions.indexOf(val)
    if (idx > -1) actions.splice(idx, 1)
    else actions.push(val)
    this.setData({ 'livenessForm.actions': actions })
  },

  async stepLiveness() {
    const form = this.data.livenessForm
    if (!form.faceImgUrl) return wx.showToast({ title: '请先拍照', icon: 'none' })
    if (form.actions.length < 3) return wx.showToast({ title: '至少 3 个动作', icon: 'none' })
    try {
      const data = await app.request({ url: '/im/kyc/liveness', method: 'POST', data: form })
      this.setData({ application: data, currentStep: data.status === 'REJECTED' ? 1 : 3 })
    } catch (e) {}
  },

  async stepFaceMatch() {
    try {
      const data = await app.request({ url: '/im/kyc/face-match', method: 'POST' })
      this.setData({ application: data, currentStep: data.status === 'REJECTED' ? 2 : 4 })
      if (data.faceMatchScore) {
        wx.showToast({ title: `通过 (${data.faceMatchScore})`, icon: 'success' })
      }
    } catch (e) {}
  },

  startRecording() {
    this.setData({ recording: true, videoDuration: 0, recorded: false })
    this.recTimer = setInterval(() => {
      const d = this.data.videoDuration + 1
      this.setData({ videoDuration: d })
      if (d >= 15) {
        clearInterval(this.recTimer)
        this.setData({ recording: false, recorded: true })
      }
    }, 1000)
  },
  stopRecording() {
    clearInterval(this.recTimer)
    this.setData({ recording: false, recorded: this.data.videoDuration >= 15 })
  },

  async stepSubmitVideo(e) {
    const idx = e.currentTarget.dataset.idx
    const stmt = this.data.statements[idx]
    if (!stmt) return
    if (!this.data.recorded) return wx.showToast({ title: '请先完成录制', icon: 'none' })
    try {
      const data = await app.request({
        url: '/im/kyc/submit-video',
        method: 'POST',
        data: { statementCode: stmt.code, videoBase64: 'mock', durationSec: this.data.videoDuration }
      })
      this.setData({ application: data, currentStep: 5 })
      wx.showToast({ title: '视频已提交', icon: 'success' })
    } catch (e) {}
  },

  async stepSubmitAudit() {
    try {
      const data = await app.request({ url: '/im/kyc/submit-audit', method: 'POST' })
      this.setData({ application: data })
      wx.showToast({ title: '已提交审核', icon: 'success' })
    } catch (e) {}
  },

  async stepBindCard() {
    const f = this.data.bankForm
    if (!f.cardNo || !f.cardName || !f.idCardNo || !f.mobile) {
      return wx.showToast({ title: '请填写完整四要素', icon: 'none' })
    }
    try {
      const data = await app.request({ url: '/im/kyc/bind-card', method: 'POST', data: f })
      this.setData({ application: data })
      wx.showToast({ title: '🎉 KYC 完成', icon: 'success' })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 1500)
    } catch (e) {}
  },

  nextStep() { this.setData({ currentStep: this.data.currentStep + 1 }) }
})