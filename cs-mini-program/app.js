// app.js — 全局入口
App({
  globalData: {
    // 后端网关地址（开发环境）
    apiBase: 'https://your-domain.com',
    // 用户信息
    userInfo: null,
    token: '',
    customerId: ''
  },

  onLaunch() {
    // 启动时检查本地 token
    const token = wx.getStorageSync('token')
    const customerId = wx.getStorageSync('customerId')
    if (token) {
      this.globalData.token = token
      this.globalData.customerId = customerId
      console.log('[App] 已登录 customerId=' + customerId)
    } else {
      // 静默登录
      this.silentLogin()
    }
  },

  /**
   * 微信登录：调 wx.login → 后端 /auth/wx-mini/login → 存 token
   */
  silentLogin() {
    const that = this
    wx.login({
      success(res) {
        if (res.code) {
          wx.request({
            url: that.globalData.apiBase + '/auth/wx-mini/login',
            method: 'POST',
            data: { jsCode: res.code },
            success(loginRes) {
              if (loginRes.data && loginRes.data.code === 0) {
                const data = loginRes.data.data
                that.globalData.token = data.token
                that.globalData.customerId = data.customerId
                wx.setStorageSync('token', data.token)
                wx.setStorageSync('customerId', data.customerId)
                console.log('[App] 登录成功 customerId=' + data.customerId)
              } else {
                console.error('[App] 登录失败', loginRes)
              }
            },
            fail(err) {
              console.error('[App] 登录请求失败', err)
            }
          })
        }
      }
    })
  },

  /**
   * 调后端 API 通用方法
   */
  request(options) {
    const that = this
    return new Promise((resolve, reject) => {
      wx.request({
        url: that.globalData.apiBase + options.url,
        method: options.method || 'GET',
        data: options.data,
        header: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + that.globalData.token
        },
        success(res) {
          if (res.statusCode === 200 && res.data && res.data.code === 0) {
            resolve(res.data.data)
          } else if (res.statusCode === 401) {
            // 重新登录
            that.silentLogin()
            reject(new Error('未登录'))
          } else {
            reject(new Error(res.data?.message || '请求失败'))
          }
        },
        fail(err) {
          reject(err)
        }
      })
    })
  }
})