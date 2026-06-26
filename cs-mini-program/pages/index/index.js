// pages/index/index.js
const app = getApp()

Page({
  data: {
    userInfo: {},
    customerId: '',
    kycStatus: 'NOT_STARTED',
    kycStatusLabel: '',
    kycCompleted: false
  },

  onShow() {
    const that = this
    that.setData({
      customerId: app.globalData.customerId || '',
      userInfo: app.globalData.userInfo || {}
    })
    // 查询 KYC 状态
    that.loadKycStatus()
  },

  async loadKycStatus() {
    try {
      const data = await app.request({ url: '/im/kyc/status' })
      this.setData({
        kycStatus: data.status,
        kycCompleted: data.status === 'COMPLETED',
        kycStatusLabel: that.statusLabelOf(data.status)
      })
    } catch (e) {}
  },

  statusLabelOf(s) {
    const map = {
      'NOT_STARTED': '未开始',
      'OCR_UPLOADED': '已上传身份证',
      'LIVENESS_PASSED': '活体已通过',
      'FACE_MATCHED': '人脸已比对',
      'VIDEO_RECORDED': '视频已录制',
      'AUDITING': '审核中',
      'APPROVED': '审核通过',
      'COMPLETED': '已认证',
      'REJECTED': '已拒绝'
    }
    return map[s] || s
  },

  onChat() { wx.switchTab({ url: '/pages/chat/chat' }) },
  onKyc() { wx.switchTab({ url: '/pages/kyc/kyc' }) },
  onProducts() {
    wx.showToast({ title: '理财产品开发中', icon: 'none' })
  },
  onTickets() {
    wx.showToast({ title: '工单功能开发中', icon: 'none' })
  }
})