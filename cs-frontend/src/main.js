import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage, ElMessageBox, ElNotification, ElLoading } from 'element-plus'
import App from './App.vue'
import router from './router'
import './styles/index.scss'
import * as ElIcons from '@element-plus/icons-vue'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 全局注册命令式 API（按需）
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$messageBox = ElMessageBox
app.config.globalProperties.$notify = ElNotification
app.config.globalProperties.$loading = ElLoading

// 按需注册图标（实际使用到的）
const usedIcons = ['User','ChatDotRound','Service','Setting','ArrowRight','PictureRounded','Upload','ChatLineSquare','Delete','Document','Loading','CircleCheck','CircleClose','Phone','UserFilled','Connection','Star','Odometer','WarningFilled']
for (const name of usedIcons) {
  if (ElIcons[name]) app.component(name, ElIcons[name])
}

app.mount('#app')