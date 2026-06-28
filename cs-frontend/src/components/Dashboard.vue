<script setup>
/**
 * 运营 Dashboard 大屏 (v2.2.83)
 *
 * 后端: /im/stats/dashboard/all (18 个聚合指标)
 *
 * 包含:
 *   - 4 大数字卡 (会话/消息/用户/营收)
 *   - 会话趋势图 (最近 7 天折线)
 *   - 会话状态分布 (饼图)
 *   - 坐席会话排行 (条形图)
 *   - 消息角色分布 (饼图)
 *   - 公众号关注 vs 未关注 (饼图)
 *   - KYC/订单/工单 状态概览
 *   - 视频回溯统计 (成功/失败)
 */
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { admin } from '@/api'
import { ElMessage } from 'element-plus'

// ============= 数据加载 =============
const data = ref({})
const loading = ref(false)
let timer = null

async function load() {
  loading.value = true
  try {
    const { data: d } = await admin.dashboard()
    data.value = d || {}
  } catch (e) {
    console.warn('[Dashboard] load error:', e)
  } finally {
    loading.value = false
  }
}

// 30 秒自动刷新
function startAutoRefresh() {
  timer = setInterval(load, 30000)
}
function stopAutoRefresh() {
  if (timer) clearInterval(timer)
}

onMounted(() => {
  load()
  startAutoRefresh()
})
onUnmounted(stopAutoRefresh)

// ============= 数据访问 =============
const sessionTotals = computed(() => data.value.sessionTotals || { total: 0, today: 0, active: 0 })
const messageTotals = computed(() => data.value.messageTotals || { total: 0, today: 0, last_hour: 0 })
const userTotals = computed(() => data.value.userTotals || { total: 0, today: 0, subscribed: 0 })
const replayTotals = computed(() => data.value.replayTotals || { total: 0, success: 0, failed: 0, today_success: 0 })
const kycStats = computed(() => data.value.kycStats || { total: 0, approved: 0, rejected: 0, today: 0 })
const revenueToday = computed(() => data.value.revenueToday || { today_settled: 0, total_settled: 0 })

const sessionTrend = computed(() => data.value.sessionTrend || [])
const sessionByStatus = computed(() => data.value.sessionByStatus || [])
const sessionByAgent = computed(() => data.value.sessionByAgent || [])
const sessionByHourToday = computed(() => data.value.sessionByHourToday || [])
const messageByRole = computed(() => data.value.messageByRole || [])
const userBySubscribe = computed(() => data.value.userBySubscribe || [])
const orderByStatus = computed(() => data.value.orderByStatus || [])
const ticketByStatus = computed(() => data.value.ticketByStatus || [])

// ============= 派生 =============

// 通过率
const kycPassRate = computed(() => {
  const k = kycStats.value
  if (!k.total) return '0%'
  return ((k.approved / k.total) * 100).toFixed(1) + '%'
})

// 视频回溯成功率
const replaySuccessRate = computed(() => {
  const r = replayTotals.value
  if (!r.total) return '0%'
  return ((r.success / r.total) * 100).toFixed(1) + '%'
})

// 会话趋势图 SVG 点
const trendMax = computed(() => {
  const v = sessionTrend.value
  if (!v.length) return 1
  return Math.max(...v.map(d => d.cnt || 0), 1)
})

const trendPolyline = computed(() => {
  const v = sessionTrend.value
  if (!v.length) return ''
  const w = 700, h = 200, pad = 30
  return v.map((d, i) => {
    const x = pad + ((w - 2 * pad) * i) / Math.max(v.length - 1, 1)
    const y = pad + (h - 2 * pad) * (1 - (d.cnt || 0) / trendMax.value)
    return `${x},${y}`
  }).join(' ')
})

const trendPoints = computed(() => {
  const v = sessionTrend.value
  if (!v.length) return []
  const w = 700, h = 200, pad = 30
  return v.map((d, i) => {
    const x = pad + ((w - 2 * pad) * i) / Math.max(v.length - 1, 1)
    const y = pad + (h - 2 * pad) * (1 - (d.cnt || 0) / trendMax.value)
    return { x, y, day: d.day, cnt: d.cnt }
  })
})

// 状态分布颜色
const statusColors = {
  IN_SESSION: '#1677ff',
  ROBOT: '#722ed1',
  QUEUED: '#faad14',
  ENDED: '#86909c'
}

// 饼图计算 (会话状态)
const statusPie = computed(() => {
  const total = sessionByStatus.value.reduce((a, b) => a + (b.v || 0), 0)
  if (!total) return { segments: [], total: 0 }
  let cumulative = 0
  const segments = sessionByStatus.value.map(d => {
    const pct = ((d.v || 0) / total) * 100
    const seg = { label: d.k, value: d.v, pct, color: statusColors[d.k] || '#1677ff' }
    cumulative += pct
    seg.cumulative = cumulative
    return seg
  })
  return { segments, total }
})

function describeArc(start, end, radius, cx, cy) {
  const startRad = (start / 100) * 360 - 90
  const endRad = (end / 100) * 360 - 90
  const x1 = cx + radius * Math.cos(startRad * Math.PI / 180)
  const y1 = cy + radius * Math.sin(startRad * Math.PI / 180)
  const x2 = cx + radius * Math.cos(endRad * Math.PI / 180)
  const y2 = cy + radius * Math.sin(endRad * Math.PI / 180)
  const largeArc = end - start > 50 ? 1 : 0
  return `M ${cx} ${cy} L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`
}

// 坐席会话排行 (top 10)
const topAgents = computed(() => sessionByAgent.value.slice(0, 10))
const agentMax = computed(() => {
  const v = topAgents.value
  if (!v.length) return 1
  return Math.max(...v.map(d => d.sessions || 0), 1)
})

// 公众号关注 vs 未关注
const subscribePie = computed(() => {
  const total = userBySubscribe.value.reduce((a, b) => a + (b.v || 0), 0)
  if (!total) return { segments: [], total: 0 }
  let cumulative = 0
  const colors = { '已关注': '#52c41a', '未关注': '#ff4d4f', '未知': '#86909c' }
  const segments = userBySubscribe.value.map(d => {
    const pct = ((d.v || 0) / total) * 100
    const seg = { label: d.k, value: d.v, pct, color: colors[d.k] || '#1677ff' }
    cumulative += pct
    seg.cumulative = cumulative
    return seg
  })
  return { segments, total }
})

// 消息角色分布
const rolePie = computed(() => {
  const total = messageByRole.value.reduce((a, b) => a + (b.v || 0), 0)
  if (!total) return { segments: [], total: 0 }
  let cumulative = 0
  const colors = { CUSTOMER: '#1677ff', AGENT: '#52c41a', ROBOT: '#722ed1', SYSTEM: '#86909c' }
  const segments = messageByRole.value.map(d => {
    const pct = ((d.v || 0) / total) * 100
    const seg = { label: d.k, value: d.v, pct, color: colors[d.k] || '#1677ff' }
    cumulative += pct
    seg.cumulative = cumulative
    return seg
  })
  return { segments, total }
})

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN')
}

function formatDate(day) {
  if (!day) return ''
  return String(day).slice(5)  // MM-DD
}

function fmt(n) {
  return Number(n || 0).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="dashboard">
    <!-- 顶部 4 大指标 -->
    <el-row :gutter="12" class="metric-row">
      <el-col :xs="12" :sm="12" :md="6" v-for="(m, i) in [
        { label: '总会话', value: fmt(sessionTotals.total), sub: `今日 ${fmt(sessionTotals.today)}`, icon: 'ChatDotRound', color: '#1677ff' },
        { label: '总消息', value: fmt(messageTotals.total), sub: `今日 ${fmt(messageTotals.today)}`, icon: 'Promotion', color: '#52c41a' },
        { label: '总用户', value: fmt(userTotals.total), sub: `已关注 ${fmt(userTotals.subscribed)}`, icon: 'UserFilled', color: '#722ed1' },
        { label: '回放视频', value: fmt(replayTotals.success), sub: `成功率 ${replaySuccessRate}`, icon: 'VideoPlay', color: '#fa8c16' }
      ]" :key="i">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-icon" :style="{ background: m.color }">
            <el-icon :size="28" color="#fff"><component :is="m.icon" /></el-icon>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value">{{ m.value }}</div>
            <div class="metric-sub">{{ m.sub }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + 状态分布 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><DataLine /></el-icon> 会话趋势 (最近 7 天)</span>
              <el-tag size="small">{{ sessionTrend.length }} 天</el-tag>
            </div>
          </template>
          <div v-if="!sessionTrend.length" class="empty-tip">暂无数据</div>
          <svg v-else viewBox="0 0 700 220" class="trend-svg" preserveAspectRatio="xMidYMid meet">
            <!-- 网格 -->
            <line v-for="(y, i) in [40, 90, 140, 190]" :key="`g${i}`" x1="30" :y1="y" x2="690" :y2="y" stroke="#f0f0f0" stroke-dasharray="3 3" />
            <!-- Y 轴 -->
            <text v-for="(y, i) in [40, 90, 140, 190]" :key="`l${i}`" x="0" :y="y + 4" font-size="10" fill="#86909c">
              {{ Math.round(trendMax * (1 - i * 0.33)) }}
            </text>
            <!-- 折线 -->
            <polyline :points="trendPolyline" fill="none" stroke="#1677ff" stroke-width="2" />
            <!-- 区域填充 -->
            <polygon :points="`30,190 ${trendPolyline} 690,190`" fill="#1677ff" opacity="0.1" />
            <!-- 点 + tooltip -->
            <g v-for="(p, i) in trendPoints" :key="`pt${i}`">
              <circle :cx="p.x" :cy="p.y" r="4" fill="#fff" stroke="#1677ff" stroke-width="2" />
              <text :x="p.x" :y="p.y - 10" font-size="10" fill="#1677ff" text-anchor="middle">{{ p.cnt }}</text>
              <text :x="p.x" y="210" font-size="10" fill="#86909c" text-anchor="middle">{{ formatDate(p.day) }}</text>
            </g>
          </svg>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><PieChart /></el-icon> 会话状态分布</span>
            </div>
          </template>
          <div v-if="!statusPie.total" class="empty-tip">暂无数据</div>
          <div v-else class="pie-wrap">
            <svg viewBox="0 0 200 200" class="pie-svg">
              <circle cx="100" cy="100" r="70" fill="#f5f5f5" />
              <template v-for="(s, i) in statusPie.segments" :key="i">
                <path :d="describeArc(s.cumulative - s.pct, s.cumulative, 70, 100, 100)"
                      :fill="s.color" stroke="#fff" stroke-width="1" />
              </template>
              <circle cx="100" cy="100" r="40" fill="#fff" />
              <text x="100" y="100" text-anchor="middle" dominant-baseline="middle"
                    font-size="22" font-weight="bold" fill="#262626">{{ statusPie.total }}</text>
              <text x="100" y="120" text-anchor="middle" font-size="10" fill="#86909c">总会话</text>
            </svg>
            <ul class="pie-legend">
              <li v-for="(s, i) in statusPie.segments" :key="i">
                <i :style="{ background: s.color }"></i>
                <span>{{ s.label }}</span>
                <strong>{{ s.value }}</strong>
                <small>{{ s.pct.toFixed(1) }}%</small>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 坐席排行 + 消息角色 + 关注分布 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><Trophy /></el-icon> 坐席会话排行</span>
              <el-tag size="small" type="info">{{ topAgents.length }} 人</el-tag>
            </div>
          </template>
          <div v-if="!topAgents.length" class="empty-tip">暂无数据</div>
          <ul v-else class="agent-list">
            <li v-for="(a, i) in topAgents" :key="a.agent">
              <span class="rank" :class="{ gold: i===0, silver: i===1, bronze: i===2 }">{{ i + 1 }}</span>
              <span class="name">{{ a.agent }}</span>
              <div class="bar-wrap">
                <div class="bar" :style="{ width: ((a.sessions || 0) / agentMax * 100) + '%' }"></div>
              </div>
              <strong>{{ a.sessions }}</strong>
              <small v-if="a.active">({{ a.active }} 进行中)</small>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><ChatLineRound /></el-icon> 消息角色分布</span>
            </div>
          </template>
          <div v-if="!rolePie.total" class="empty-tip">暂无消息</div>
          <div v-else class="pie-wrap">
            <svg viewBox="0 0 200 200" class="pie-svg">
              <circle cx="100" cy="100" r="70" fill="#f5f5f5" />
              <template v-for="(s, i) in rolePie.segments" :key="i">
                <path :d="describeArc(s.cumulative - s.pct, s.cumulative, 70, 100, 100)"
                      :fill="s.color" stroke="#fff" stroke-width="1" />
              </template>
              <circle cx="100" cy="100" r="40" fill="#fff" />
              <text x="100" y="100" text-anchor="middle" dominant-baseline="middle"
                    font-size="22" font-weight="bold" fill="#262626">{{ rolePie.total }}</text>
              <text x="100" y="120" text-anchor="middle" font-size="10" fill="#86909c">总消息</text>
            </svg>
            <ul class="pie-legend">
              <li v-for="(s, i) in rolePie.segments" :key="i">
                <i :style="{ background: s.color }"></i>
                <span>{{ s.label }}</span>
                <strong>{{ s.value }}</strong>
                <small>{{ s.pct.toFixed(1) }}%</small>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用户/公众号 + KYC/订单 + 回放统计 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><User /></el-icon> 公众号关注</span>
            </div>
          </template>
          <div v-if="!subscribePie.total" class="empty-tip">暂无用户</div>
          <div v-else class="pie-wrap small">
            <svg viewBox="0 0 200 200" class="pie-svg">
              <circle cx="100" cy="100" r="70" fill="#f5f5f5" />
              <template v-for="(s, i) in subscribePie.segments" :key="i">
                <path :d="describeArc(s.cumulative - s.pct, s.cumulative, 70, 100, 100)"
                      :fill="s.color" stroke="#fff" stroke-width="1" />
              </template>
              <circle cx="100" cy="100" r="40" fill="#fff" />
              <text x="100" y="100" text-anchor="middle" dominant-baseline="middle"
                    font-size="22" font-weight="bold" fill="#262626">{{ subscribePie.total }}</text>
              <text x="100" y="120" text-anchor="middle" font-size="10" fill="#86909c">总用户</text>
            </svg>
            <ul class="pie-legend">
              <li v-for="(s, i) in subscribePie.segments" :key="i">
                <i :style="{ background: s.color }"></i>
                <span>{{ s.label }}</span>
                <strong>{{ s.value }}</strong>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><Document /></el-icon> KYC 审核</span>
            </div>
          </template>
          <div class="kv-list">
            <div class="kv">
              <span class="k">提交总数</span>
              <strong>{{ fmt(kycStats.total) }}</strong>
            </div>
            <div class="kv">
              <span class="k">通过</span>
              <strong style="color: #52c41a">{{ fmt(kycStats.approved) }}</strong>
            </div>
            <div class="kv">
              <span class="k">拒绝</span>
              <strong style="color: #ff4d4f">{{ fmt(kycStats.rejected) }}</strong>
            </div>
            <div class="kv">
              <span class="k">今日</span>
              <strong>{{ fmt(kycStats.today) }}</strong>
            </div>
            <div class="kv-bar">
              <span class="k">通过率</span>
              <strong>{{ kycPassRate }}</strong>
              <div class="bar-mini"><div class="fill" :style="{ width: kycPassRate, background: '#52c41a' }"></div></div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><Money /></el-icon> 营收 + 订单</span>
            </div>
          </template>
          <div class="kv-list">
            <div class="kv">
              <span class="k">今日营收</span>
              <strong style="color: #fa8c16">¥{{ fmt(revenueToday.today_settled) }}</strong>
            </div>
            <div class="kv">
              <span class="k">累计营收</span>
              <strong>¥{{ fmt(revenueToday.total_settled) }}</strong>
            </div>
            <div class="kv-divider">订单状态</div>
            <div v-for="o in orderByStatus" :key="o.k" class="kv">
              <span class="k">{{ o.k }}</span>
              <strong>{{ fmt(o.v) }}</strong>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工单 + 回溯 + 会话小时分布 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><Tickets /></el-icon> 工单状态</span>
            </div>
          </template>
          <div v-if="!ticketByStatus.length" class="empty-tip">暂无工单</div>
          <ul v-else class="status-list">
            <li v-for="t in ticketByStatus" :key="t.k">
              <el-tag size="small" :type="t.k === 'OPEN' ? 'warning' : (t.k === 'CLOSED' ? 'success' : 'info')">{{ t.k }}</el-tag>
              <strong>{{ fmt(t.v) }}</strong>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><VideoCamera /></el-icon> 视频回溯</span>
            </div>
          </template>
          <div class="kv-list">
            <div class="kv">
              <span class="k">合成总数</span>
              <strong>{{ fmt(replayTotals.total) }}</strong>
            </div>
            <div class="kv">
              <span class="k">成功</span>
              <strong style="color: #52c41a">{{ fmt(replayTotals.success) }}</strong>
            </div>
            <div class="kv">
              <span class="k">失败</span>
              <strong style="color: #ff4d4f">{{ fmt(replayTotals.failed) }}</strong>
            </div>
            <div class="kv">
              <span class="k">今日成功</span>
              <strong>{{ fmt(replayTotals.today_success) }}</strong>
            </div>
            <div class="kv-bar">
              <span class="k">成功率</span>
              <strong>{{ replaySuccessRate }}</strong>
              <div class="bar-mini"><div class="fill" :style="{ width: replaySuccessRate, background: '#1677ff' }"></div></div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><Clock /></el-icon> 今日小时分布</span>
              <el-tag size="small" type="warning">{{ sessionByHourToday.length }} 小时</el-tag>
            </div>
          </template>
          <div v-if="!sessionByHourToday.length" class="empty-tip">今日无会话</div>
          <svg v-else viewBox="0 0 240 100" class="hour-svg" preserveAspectRatio="xMidYMid meet">
            <template v-for="(h, i) in sessionByHourToday" :key="i">
              <rect :x="(i * 240) / Math.max(sessionByHourToday.length, 1)"
                    :y="100 - ((h.cnt / Math.max(...sessionByHourToday.map(x => x.cnt), 1)) * 90)"
                    :width="(240 / Math.max(sessionByHourToday.length, 1)) - 2"
                    :height="(h.cnt / Math.max(...sessionByHourToday.map(x => x.cnt), 1)) * 90"
                    fill="#1677ff" opacity="0.7" rx="2" />
              <text :x="(i * 240) / Math.max(sessionByHourToday.length, 1) + (240 / Math.max(sessionByHourToday.length, 1)) / 2"
                    y="98" font-size="8" fill="#86909c" text-anchor="middle">{{ h.hour }}h</text>
            </template>
          </svg>
        </el-card>
      </el-col>
    </el-row>

    <div class="footer-tip">
      <small>🕐 30 秒自动刷新 · 最后更新 {{ new Date().toLocaleTimeString('zh-CN') }}</small>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.dashboard {
  padding: 0;
}
.metric-row, .chart-row {
  margin-bottom: 12px;
}
.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}
.metric-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.metric-body { flex: 1; }
.metric-label {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 4px;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #262626;
  line-height: 1.2;
}
.metric-sub {
  font-size: 11px;
  color: #86909c;
  margin-top: 2px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  span { display: flex; align-items: center; gap: 6px; }
}
.trend-svg {
  width: 100%;
  height: 220px;
}
.empty-tip {
  text-align: center;
  color: #bfbfbf;
  padding: 30px 0;
  font-size: 13px;
}
.pie-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  &.small { gap: 8px; }
}
.pie-svg {
  width: 160px;
  height: 160px;
  flex-shrink: 0;
}
.pie-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  li {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 0;
    font-size: 12px;
    i {
      width: 10px;
      height: 10px;
      border-radius: 2px;
      flex-shrink: 0;
    }
    span { flex: 1; color: #595959; }
    strong { color: #262626; min-width: 36px; text-align: right; }
    small { color: #86909c; min-width: 48px; text-align: right; }
  }
}
.agent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 0;
    font-size: 13px;
  }
  .rank {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #f0f0f0;
    color: #595959;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: bold;
    flex-shrink: 0;
    &.gold { background: #faad14; color: #fff; }
    &.silver { background: #c0c4cc; color: #fff; }
    &.bronze { background: #d4a373; color: #fff; }
  }
  .name {
    min-width: 80px;
    color: #595959;
    font-size: 12px;
  }
  .bar-wrap {
    flex: 1;
    height: 8px;
    background: #f5f5f5;
    border-radius: 4px;
    overflow: hidden;
  }
  .bar {
    height: 100%;
    background: linear-gradient(90deg, #1677ff, #4096ff);
    border-radius: 4px;
    transition: width 0.3s;
  }
  strong { min-width: 36px; text-align: right; color: #262626; }
  small { color: #86909c; font-size: 11px; }
}
.kv-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}
.kv {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  border-bottom: 1px dashed #f0f0f0;
  &:last-child { border-bottom: none; }
}
.kv .k { color: #86909c; }
.kv strong { font-size: 16px; }
.kv-divider {
  font-size: 11px;
  color: #bfbfbf;
  margin: 4px 0 0;
  padding-top: 4px;
  border-top: 1px solid #f0f0f0;
  &:first-child { border-top: none; padding: 0; }
}
.kv-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  .k { color: #86909c; min-width: 56px; }
  strong { font-size: 14px; min-width: 56px; }
  .bar-mini {
    flex: 1;
    height: 6px;
    background: #f5f5f5;
    border-radius: 3px;
    overflow: hidden;
  }
  .fill {
    height: 100%;
    border-radius: 3px;
    transition: width 0.3s;
  }
}
.status-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 4px 0;
    font-size: 13px;
  }
}
.hour-svg {
  width: 100%;
  height: 110px;
}
.footer-tip {
  text-align: right;
  padding: 8px 12px;
  color: #bfbfbf;
  font-size: 11px;
}
</style>