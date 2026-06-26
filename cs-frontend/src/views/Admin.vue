<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { admin } from '@/api'
import { useUserStore } from '@/store/user'
import { maskMobile, formatDate, formatTime } from '@/utils'

const user = useUserStore()
const activeTab = ref('dashboard')

// ============ Dashboard ============
const dashboard = ref({})

// ============ 趋势图 ============
const chartRange = ref('7')
const trendData = ref([])
const yAxisLabels = computed(() => {
  if (!trendData.value.length) return ['0', '0', '0', '0']
  const max = Math.max(...trendData.value.flatMap(d => [d.sessions || 0, d.messages || 0]), 1)
  return [String(max), String(Math.floor(max * 0.66)), String(Math.floor(max * 0.33)), '0']
})
const sessionPoints = computed(() => {
  if (!trendData.value.length) return []
  const max = Math.max(...trendData.value.flatMap(d => [d.sessions || 0, d.messages || 0]), 1)
  const w = 650, h = 160
  const x0 = 40, y0 = 40
  return trendData.value.map((d, i) => ({
    x: x0 + (w * i) / (trendData.value.length - 1 || 1),
    y: y0 + h - (h * (d.sessions || 0)) / max
  }))
})
const messagePoints = computed(() => {
  if (!trendData.value.length) return []
  const max = Math.max(...trendData.value.flatMap(d => [d.sessions || 0, d.messages || 0]), 1)
  const w = 650, h = 160
  const x0 = 40, y0 = 40
  return trendData.value.map((d, i) => ({
    x: x0 + (w * i) / (trendData.value.length - 1 || 1),
    y: y0 + h - (h * (d.messages || 0)) / max
  }))
})
const sessionLinePoints = computed(() => sessionPoints.value.map(p => `${p.x},${p.y}`).join(' '))
const messageLinePoints = computed(() => messagePoints.value.map(p => `${p.x},${p.y}`).join(' '))

async function loadTrend() {
  try {
    const { data } = await request.get(`/admin/trend?days=${chartRange.value}`)
    trendData.value = data || []
  } catch {
    // 降级用 mock 数据
    const days = parseInt(chartRange.value)
    trendData.value = Array.from({ length: days }, (_, i) => {
      const d = new Date(); d.setDate(d.getDate() - (days - 1 - i))
      return {
        date: `${d.getMonth() + 1}/${d.getDate()}`,
        sessions: Math.floor(20 + Math.random() * 50),
        messages: Math.floor(100 + Math.random() * 200)
      }
    })
  }
}

async function loadDashboard() {
  const { data } = await admin.dashboard()
  dashboard.value = data || {}
}

// ============ Sessions ============
const sessions = ref([])
const sessionLoading = ref(false)
const sessionQuery = reactive({ status: '', keyword: '', page: 1, size: 20 })

async function loadSessions() {
  sessionLoading.value = true
  try {
    const { data } = await admin.sessions(sessionQuery)
    sessions.value = data?.list || []
  } finally {
    sessionLoading.value = false
  }
}

async function forceHangup(row) {
  const { value: reason } = await ElMessageBox.prompt('强制挂断原因（必填）：', '二次确认', {
    confirmButtonText: '确认挂断',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputValidator: v => v && v.length >= 5 || '请填写至少 5 个字的原因（审计要求）'
  })
  await admin.forceHangup(row.id, reason)
  ElMessage.success('已强制挂断')
  loadSessions()
}

// ============ Users ============
const users = ref([])
const userLoading = ref(false)
const userQuery = reactive({ keyword: '', page: 1, size: 20 })

async function loadUsers() {
  userLoading.value = true
  try {
    const { data } = await admin.users(userQuery)
    users.value = data?.list || []
  } finally {
    userLoading.value = false
  }
}

// ============ Audit ============
const audits = ref([])
const auditLoading = ref(false)
const auditQuery = reactive({ keyword: '', page: 1, size: 20 })

async function loadAudit() {
  auditLoading.value = true
  try {
    const { data } = await admin.audit(auditQuery)
    audits.value = data?.list || []
  } finally {
    auditLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  loadSessions()
  loadTrend()
})
</script>

<template>
  <el-container class="admin-app">
    <el-aside width="220px" class="sider">
      <div class="brand">
        <div class="logo">🛡️</div>
        <div>
          <div class="title">后管系统</div>
          <div class="sub">{{ user.profile?.name || 'admin' }}</div>
        </div>
      </div>
      <el-menu :default-active="activeTab" @select="k => activeTab = k" class="menu">
        <el-menu-item index="dashboard"><el-icon><Odometer /></el-icon>数据看板</el-menu-item>
        <el-menu-item index="sessions"><el-icon><ChatDotRound /></el-icon>会话监控</el-menu-item>
        <el-menu-item index="users"><el-icon><User /></el-icon>用户管理</el-menu-item>
        <el-menu-item index="audit"><el-icon><Document /></el-icon>审计日志</el-menu-item>
        <el-menu-item index="settings"><el-icon><Setting /></el-icon>系统设置</el-menu-item>
      </el-menu>
      <div class="footer">
        <el-button text @click="$router.push('/')">返回首页</el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <span class="page-title">{{ {
          dashboard: '数据看板', sessions: '会话监控',
          users: '用户管理', audit: '审计日志', settings: '系统设置'
        }[activeTab] }}</span>
      </el-header>

      <el-main>
        <!-- Dashboard -->
        <div v-if="activeTab === 'dashboard'" class="dashboard">
          <el-row :gutter="16">
            <el-col :span="6" v-for="(item, idx) in [
              { label: '今日会话', value: dashboard.todaySessions || 0, color: '#1677ff', icon: 'ChatDotRound' },
              { label: '在线客服', value: dashboard.onlineAgents || 0, color: '#52c41a', icon: 'Service' },
              { label: '排队客户', value: dashboard.queueSize || 0, color: '#faad14', icon: 'Loading' },
              { label: '异常事件', value: dashboard.alerts || 0, color: '#ff4d4f', icon: 'WarningFilled' }
            ]" :key="idx">
              <el-card class="metric" shadow="hover">
                <div class="metric-icon" :style="{ background: item.color }">
                  <el-icon :size="24" color="#fff"><component :is="item.icon" /></el-icon>
                </div>
                <div class="metric-body">
                  <div class="label">{{ item.label }}</div>
                  <div class="value">{{ item.value }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          <el-card class="chart" shadow="never" style="margin-top: 16px;">
            <template #header>
              <div class="card-header">
                <span>近 7 日趋势</span>
                <el-radio-group v-model="chartRange" size="small" @change="loadTrend">
                  <el-radio-button label="7">7 天</el-radio-button>
                  <el-radio-button label="30">30 天</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <svg ref="trendChart" :viewBox="`0 0 700 240`" class="trend-chart" preserveAspectRatio="none">
              <!-- 网格 -->
              <line v-for="(y, i) in [40, 90, 140, 190]" :key="`g${i}`" :x1="40" :y1="y" :x2="690" :y2="y" stroke="#e5e6eb" stroke-dasharray="3 3" />
              <!-- Y轴标签 -->
              <text v-for="(y, i) in [40, 90, 140, 190]" :key="`l${i}`" x="0" :y="y + 4" font-size="11" fill="#86909c">{{ yAxisLabels[i] }}</text>
              <!-- X轴 -->
              <line x1="40" y1="200" x2="690" y2="200" stroke="#86909c" />
              <!-- 折线：会话量 -->
              <polyline :points="sessionLinePoints" fill="none" stroke="#1677ff" stroke-width="2" />
              <!-- 折线：消息量 -->
              <polyline :points="messageLinePoints" fill="none" stroke="#52c41a" stroke-width="2" />
              <!-- 点 -->
              <circle v-for="(p, i) in sessionPoints" :key="`s${i}`" :cx="p.x" :cy="p.y" r="3" fill="#1677ff" />
              <circle v-for="(p, i) in messagePoints" :key="`m${i}`" :cx="p.x" :cy="p.y" r="3" fill="#52c41a" />
              <!-- X轴标签 -->
              <text v-for="(p, i) in sessionPoints" :key="`x${i}`" :x="p.x" :y="220" font-size="10" fill="#86909c" text-anchor="middle">{{ trendData[i]?.date || '' }}</text>
            </svg>
            <div class="chart-legend">
              <span><i style="background:#1677ff"></i>会话量</span>
              <span><i style="background:#52c41a"></i>消息量</span>
            </div>
          </el-card>
        </div>

        <!-- Sessions -->
        <el-card v-if="activeTab === 'sessions'" shadow="never">
          <template #header>
            <div class="card-header">
              <span>会话列表</span>
              <div class="filters">
                <el-input v-model="sessionQuery.keyword" placeholder="搜索客户/ID" style="width: 200px;" clearable @clear="loadSessions" @keydown.enter="loadSessions" />
                <el-select v-model="sessionQuery.status" placeholder="状态" clearable style="width: 120px;" @change="loadSessions">
                  <el-option label="排队" value="waiting" />
                  <el-option label="进行中" value="active" />
                  <el-option label="已结束" value="ended" />
                </el-select>
                <el-button type="primary" @click="loadSessions">查询</el-button>
              </div>
            </div>
          </template>
          <el-table :data="sessions" v-loading="sessionLoading" stripe>
            <el-table-column prop="id" label="会话ID" width="200" />
            <el-table-column prop="customerName" label="客户" width="120" />
            <el-table-column prop="agentName" label="客服" width="120">
              <template #default="{ row }">
                {{ row.agentName || '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : (row.status === 'waiting' ? 'warning' : 'info')" size="small">
                  {{ { waiting: '排队', active: '进行中', ended: '已结束' }[row.status] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="160">
              <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
            </el-table-column>
            <el-table-column prop="msgCount" label="消息数" width="100" />
            <el-table-column label="操作" fixed="right" width="180">
              <template #default="{ row }">
                <el-button v-if="row.status === 'active'" size="small" type="danger" plain @click="forceHangup(row)">
                  <el-icon><CircleClose /></el-icon>强制挂断
                </el-button>
                <el-button v-else size="small" plain>查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- Users -->
        <el-card v-if="activeTab === 'users'" shadow="never">
          <template #header>
            <div class="card-header">
              <span>用户列表</span>
              <div class="filters">
                <el-input v-model="userQuery.keyword" placeholder="搜索用户名/手机" style="width: 240px;" clearable @clear="loadUsers" @keydown.enter="loadUsers" />
                <el-button type="primary" @click="loadUsers">查询</el-button>
              </div>
            </div>
          </template>
          <el-table :data="users" v-loading="userLoading" stripe>
            <el-table-column prop="customerId" label="客户ID" width="180" />
            <el-table-column prop="username" label="账号" width="160" />
            <el-table-column prop="nickname" label="昵称" width="120" />
            <el-table-column prop="phoneMasked" label="手机号" width="140" />
            <el-table-column prop="provider" label="登录方式" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.provider || 'LOCAL' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="注册时间" width="180">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default>
                <el-tag type="success" size="small">正常</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- Audit -->
        <el-card v-if="activeTab === 'audit'" shadow="never">
          <template #header>
            <div class="card-header">
              <span>审计日志（操作员行为追溯）</span>
              <div class="filters">
                <el-input v-model="auditQuery.keyword" placeholder="搜索操作员/动作" style="width: 240px;" clearable @clear="loadAudit" @keydown.enter="loadAudit" />
                <el-button type="primary" @click="loadAudit">查询</el-button>
              </div>
            </div>
          </template>
          <el-table :data="audits" v-loading="auditLoading" stripe>
            <el-table-column prop="time" label="时间" width="180">
              <template #default="{ row }">{{ formatDate(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作员" width="120" />
            <el-table-column prop="action" label="动作" width="160">
              <template #default="{ row }">
                <el-tag size="small">{{ row.action }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="target" label="对象" width="160" />
            <el-table-column prop="reason" label="原因" />
            <el-table-column prop="ip" label="IP" width="140" />
          </el-table>
        </el-card>

        <!-- Settings -->
        <el-card v-if="activeTab === 'settings'" shadow="never">
          <template #header><span>系统设置</span></template>
          <el-form label-width="160px" style="max-width: 600px;">
            <el-form-item label="会话超时（分钟）">
              <el-input-number v-model="dashboard.sessionTimeout" :min="5" :max="120" />
            </el-form-item>
            <el-form-item label="每日最大会话数">
              <el-input-number v-model="dashboard.dailyMax" :min="1" :max="10000" />
            </el-form-item>
            <el-form-item label="客服接听上限">
              <el-input-number v-model="dashboard.agentMax" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="敏感词过滤">
              <el-switch v-model="dashboard.sensitiveFilter" />
            </el-form-item>
            <el-form-item label="强制双因素认证">
              <el-switch v-model="dashboard.force2fa" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.admin-app {
  height: 100vh;
}

.sider {
  background: #001529;
  color: #fff;
  display: flex;
  flex-direction: column;

  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);

    .logo {
      width: 40px; height: 40px;
      background: linear-gradient(135deg, #722ed1 0%, #a855f7 100%);
      border-radius: 10px;
      display: flex; align-items: center; justify-content: center;
      font-size: 20px;
    }

    .title { font-size: 14px; font-weight: 500; }
    .sub { font-size: 11px; color: rgba(255, 255, 255, 0.6); }
  }

  .menu {
    background: transparent;
    border-right: none;
    flex: 1;

    :deep(.el-menu-item) {
      color: rgba(255, 255, 255, 0.75);

      &:hover, &.is-active {
        background: rgba(255, 255, 255, 0.08) !important;
        color: #fff;
      }
    }
  }

  .footer {
    padding: 12px;
    border-top: 1px solid rgba(255, 255, 255, 0.06);

    .el-button { color: rgba(255, 255, 255, 0.65); }
  }
}

.topbar {
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  display: flex;
  align-items: center;

  .page-title {
    font-size: 16px;
    font-weight: 600;
  }
}

.dashboard {
  .metric {
    .el-card__body {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
    }
    .metric-icon {
      width: 56px; height: 56px;
      border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
    }
    .label {
      font-size: 12px;
      color: #86909c;
      margin-bottom: 4px;
    }
    .value {
      font-size: 24px;
      font-weight: 600;
    }
  }

  .chart-placeholder {
    height: 240px;
    display: flex; align-items: center; justify-content: center;
    background: #fafafa;
    border-radius: 8px;
    color: #86909c;
  }
  .trend-chart {
    width: 100%;
    height: 240px;
  }
  .chart-legend {
    display: flex;
    gap: 16px;
    justify-content: center;
    margin-top: 8px;
    font-size: 12px;
    color: #86909c;
    span {
      display: flex;
      align-items: center;
      gap: 4px;
      i {
        display: inline-block;
        width: 12px; height: 2px;
      }
    }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .filters {
    display: flex; gap: 8px;
  }
}
</style>