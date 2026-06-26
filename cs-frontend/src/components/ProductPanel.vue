<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { product, risk, order } from '@/api'

const props = defineProps({
  customerId: { type: String, default: '' },
  agentUsername: { type: String, default: null }  // 坐席协助下单
})
const emit = defineEmits(['close', 'purchased'])

// ============ 数据 ============
const products = ref([])
const riskLatest = ref(null)
const showRiskDialog = ref(false)
const riskForm = reactive({ age: 0, income: 0, experience: 0, preference: 0, ratio: 0 })
const riskResult = ref(null)
const selectedProduct = ref(null)
const buyForm = reactive({ amount: 0, paymentMethod: 'MOCK_BANK' })
const orderResult = ref(null)

onMounted(async () => {
  await loadProducts()
  await loadRisk()
})

async function loadProducts() {
  try {
    const { data } = await product.list()
    products.value = data || []
  } catch (e) { /* 拦截器已处理 */ }
}

async function loadRisk() {
  if (!props.customerId) return
  try {
    const { data } = await risk.latest(props.customerId)
    riskLatest.value = data
  } catch {}
}

function selectProduct(p) {
  selectedProduct.value = p
  buyForm.amount = p.minAmount
}

async function startBuy(p) {
  if (!props.customerId) {
    return ElMessage.warning('请先选择客户')
  }
  if (!riskLatest.value) {
    // 弹风险评估问卷
    showRiskDialog.value = true
    selectedProduct.value = p
    return
  }
  selectProduct(p)
}

async function submitRisk() {
  try {
    const { data } = await risk.assess(props.customerId, { ...riskForm })
    riskResult.value = data
    riskLatest.value = { score: data.score, riskLevel: data.riskLevel }
    ElMessage.success(`评估完成：${data.riskLevel}（${data.score}分）`)
    showRiskDialog.value = false
  } catch {}
}

async function confirmBuy() {
  if (!selectedProduct.value) return
  if (buyForm.amount < selectedProduct.value.minAmount) {
    return ElMessage.warning('金额低于起购额')
  }
  try {
    const { data } = await order.oneClickBuy({
      customerId: props.customerId,
      productCode: selectedProduct.value.code,
      amount: buyForm.amount,
      agentUsername: props.agentUsername,
      riskAnswers: null  // 已评估
    })
    orderResult.value = data
    if (data.success) {
      ElMessage.success(data.message)
      emit('purchased', data)
      setTimeout(() => emit('close'), 2000)
    } else {
      ElMessage.error(data.message + ': ' + (data.complianceRemark || ''))
    }
  } catch {}
}

const productTypeLabel = (t) => ({
  INSURANCE: '保险', DEPOSIT: '理财', FUND: '基金', BOND: '债券'
}[t] || t)

const riskLevelLabel = (l) => ({
  CONSERVATIVE: '保守型', MODERATE: '稳健型', AGGRESSIVE: '激进型'
}[l] || l)

const statusLabel = (s) => ({
  DRAFT: '草稿', RISK_ASSESSED: '已评估', COMPLIANCE_PASSED: '合规通过',
  PAYING: '支付中', SETTLED: '已成交', REDEEMED: '已赎回', REJECTED: '已拒绝'
}[s] || s)
</script>

<template>
  <div class="product-panel">
    <!-- 头部 -->
    <div class="panel-header">
      <h3>🛍️ 金融产品超市</h3>
      <el-button text @click="emit('close')">关闭 ✕</el-button>
    </div>

    <!-- 风险评估状态条 -->
    <div class="risk-bar" :class="{ ok: riskLatest, none: !riskLatest }">
      <span v-if="riskLatest">✓ 已完成风险评估：<b>{{ riskLevelLabel(riskLatest.riskLevel) }}</b>（{{ riskLatest.score }}分，有效期 1 年）</span>
      <span v-else>⚠ 您尚未完成风险评估，请先评估</span>
      <el-button size="small" @click="showRiskDialog = true">重新评估</el-button>
    </div>

    <!-- 产品列表 -->
    <div class="product-grid">
      <div v-for="p in products" :key="p.code" class="product-card" :class="selectedProduct?.code === p.code ? 'selected' : ''" @click="selectProduct(p)">
        <div class="type-badge" :class="p.productType.toLowerCase()">{{ productTypeLabel(p.productType) }}</div>
        <div class="name">{{ p.name }}</div>
        <div class="desc">{{ p.description }}</div>
        <div class="meta">
          <div><span class="label">风险：</span><el-tag size="small" :type="p.riskLevel === 'LOW' ? 'success' : p.riskLevel === 'MID' ? 'warning' : 'danger'">{{ p.riskLevel }}</el-tag></div>
          <div><span class="label">收益：</span><b class="yield">{{ p.yieldRate }}%</b> 年化</div>
          <div><span class="label">期限：</span>{{ p.period === 'PERPETUAL' ? '活期' : p.period }}</div>
          <div><span class="label">起购：</span>¥ {{ p.minAmount }}</div>
        </div>
        <el-button type="primary" size="small" class="buy-btn" @click.stop="startBuy(p)">立即购买</el-button>
      </div>
    </div>

    <!-- 风险评估问卷弹窗 -->
    <el-dialog v-model="showRiskDialog" title="📊 风险评估问卷" width="500px">
      <p class="risk-hint">5 道题，用于评估您的风险承受能力，结果有效期 1 年</p>
      <el-form label-width="120px">
        <el-form-item label="年龄">
          <el-radio-group v-model="riskForm.age">
            <el-radio :label="0">18-30</el-radio>
            <el-radio :label="1">31-50</el-radio>
            <el-radio :label="2">51+</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年收入">
          <el-radio-group v-model="riskForm.income">
            <el-radio :label="0">&lt;10万</el-radio>
            <el-radio :label="1">10-50万</el-radio>
            <el-radio :label="2">&gt;50万</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="投资经验">
          <el-radio-group v-model="riskForm.experience">
            <el-radio :label="0">无</el-radio>
            <el-radio :label="1">1-3年</el-radio>
            <el-radio :label="2">&gt;3年</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="风险偏好">
          <el-radio-group v-model="riskForm.preference">
            <el-radio :label="0">保本</el-radio>
            <el-radio :label="1">稳健</el-radio>
            <el-radio :label="2">激进</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资产占比">
          <el-radio-group v-model="riskForm.ratio">
            <el-radio :label="0">&lt;10%</el-radio>
            <el-radio :label="1">10-30%</el-radio>
            <el-radio :label="2">&gt;30%</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRiskDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRisk">提交评估</el-button>
      </template>
    </el-dialog>

    <!-- 购买确认弹窗 -->
    <el-dialog v-model="selectedProduct" :title="`购买：${selectedProduct?.name}`" width="500px" :show-close="true" @close="selectedProduct = null">
      <div v-if="selectedProduct">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="产品">{{ selectedProduct.name }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ selectedProduct.riskLevel }}</el-descriptions-item>
          <el-descriptions-item label="年化收益">{{ selectedProduct.yieldRate }}%</el-descriptions-item>
          <el-descriptions-item label="期限">{{ selectedProduct.period === 'PERPETUAL' ? '活期' : selectedProduct.period }}</el-descriptions-item>
          <el-descriptions-item label="起购金额">¥ {{ selectedProduct.minAmount }}</el-descriptions-item>
          <el-descriptions-item label="限购金额">{{ selectedProduct.maxAmount ? '¥ ' + selectedProduct.maxAmount : '不限' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="100px" style="margin-top: 20px;">
          <el-form-item label="购买金额">
            <el-input-number v-model="buyForm.amount" :min="selectedProduct.minAmount" :max="selectedProduct.maxAmount || 9999999" :step="100" />
          </el-form-item>
          <el-form-item label="支付方式">
            <el-select v-model="buyForm.paymentMethod">
              <el-option label="模拟银行" value="MOCK_BANK" />
              <el-option label="微信支付" value="WECHAT_PAY" />
              <el-option label="支付宝" value="ALIPAY" />
            </el-select>
          </el-form-item>
        </el-form>

        <el-alert v-if="orderResult" :type="orderResult.success ? 'success' : 'error'" :closable="false" show-icon style="margin-top: 12px;">
          <p><b>{{ orderResult.success ? '✓ 购买成功' : '✗ ' + (orderResult.message || '购买失败') }}</b></p>
          <p v-if="orderResult.complianceRemark" style="font-size: 12px; margin-top: 4px;">{{ orderResult.complianceRemark }}</p>
          <p v-if="orderResult.orderNo" style="font-size: 12px; color: #86909c;">订单号：{{ orderResult.orderNo }}</p>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="selectedProduct = null; orderResult = null">取消</el-button>
        <el-button type="primary" :loading="!!orderResult" :disabled="!!orderResult" @click="confirmBuy">确认购买</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.product-panel {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
}
.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  h3 { margin: 0; font-size: 16px; }
}
.risk-bar {
  padding: 10px 14px;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px;
  &.ok { background: #f6ffed; border: 1px solid #b7eb8f; color: #389e0d; }
  &.none { background: #fff7e6; border: 1px solid #ffd591; color: #d46b08; }
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.product-card {
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
  &:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
  &.selected { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,0.2); }
  .type-badge {
    display: inline-block;
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 4px;
    margin-bottom: 8px;
    &.insurance { background: #fff1f0; color: #cf1322; }
    &.deposit { background: #f6ffed; color: #389e0d; }
    &.fund { background: #e6f4ff; color: #1677ff; }
    &.bond { background: #f9f0ff; color: #722ed1; }
  }
  .name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
  .desc { font-size: 12px; color: #86909c; height: 32px; line-height: 16px; overflow: hidden; margin-bottom: 8px; }
  .meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4px 12px;
    font-size: 12px;
    color: #595959;
    margin-bottom: 10px;
    .label { color: #86909c; }
    .yield { color: #cf1322; font-size: 14px; }
  }
  .buy-btn { width: 100%; }
}
.risk-hint { color: #86909c; font-size: 12px; margin-bottom: 16px; }
</style>