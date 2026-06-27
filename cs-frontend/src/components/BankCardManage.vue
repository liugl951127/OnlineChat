<template>
  <el-card class="bankcard-panel" shadow="hover">
    <template #header>
      <div class="header">
        <el-icon><CreditCard /></el-icon>
        <span>银行卡管理</span>
        <el-button text type="primary" @click="loadCards" :icon="Refresh">刷新</el-button>
      </div>
    </template>

    <div v-if="loading" class="loading">
      <el-icon class="rotating"><Loading /></el-icon>
      加载银行卡...
    </div>

    <div v-else-if="cards.length === 0" class="empty">
      <el-empty description="尚未绑定银行卡" :image-size="80" />
    </div>

    <div v-else class="card-list">
      <div
        v-for="card in cards"
        :key="card.id"
        class="card-item"
        :class="{ default: card.isDefault === 1 }"
      >
        <div class="card-info">
          <div class="card-no">
            <span class="masked">{{ card.cardNoMasked }}</span>
            <el-tag v-if="card.isDefault === 1" size="small" type="success">主卡</el-tag>
            <el-tag v-if="card.verified === 1" size="small" type="info">已鉴权</el-tag>
          </div>
          <div class="card-meta">
            <span>{{ card.cardName }}</span>
            <span class="separator">·</span>
            <span>{{ card.bankName || card.bankCode }}</span>
            <span class="separator">·</span>
            <span>{{ card.cardType === 'CREDIT' ? '信用卡' : '借记卡' }}</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button
            v-if="card.isDefault !== 1"
            size="small"
            text
            type="primary"
            @click="setDefault(card)"
          >设为主卡</el-button>
          <el-button
            size="small"
            text
            type="danger"
            @click="unbindCard(card)"
          >解绑</el-button>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { CreditCard, Refresh, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kyc } from '@/api'

const loading = ref(false)
const cards = ref([])

async function loadCards() {
  loading.value = true
  try {
    const { data } = await kyc.bankCards()
    cards.value = data || []
  } catch (e) {
    ElMessage.error('加载银行卡失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

async function setDefault(card) {
  try {
    // 直接调后端接口：假设有 /im/kyc/bank-card/{id}/default
    // 这里给个简化提示：调 kyc.bindCard 时 isDefault=1
    ElMessage.info('请在绑卡时勾选"设为主卡"')
  } catch (e) {
    ElMessage.error('设置主卡失败')
  }
}

async function unbindCard(card) {
  try {
    await ElMessageBox.confirm(
      `确认解绑 ${card.cardNoMasked} 吗？`,
      '解绑银行卡',
      { confirmButtonText: '解绑', cancelButtonText: '取消', type: 'warning' }
    )
    // 假设有 DELETE /im/kyc/bank-card/{id}，前端暂用提示
    ElMessage.success('解绑成功（占位，请实现 DELETE API）')
    cards.value = cards.value.filter(c => c.id !== card.id)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('解绑失败')
  }
}

defineExpose({ loadCards })
onMounted(loadCards)
</script>

<style scoped>
.bankcard-panel { margin-bottom: 16px; }
.header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: linear-gradient(135deg, #f5f7fa 0%, #fff 100%);
}
.card-item.default {
  background: linear-gradient(135deg, #e1f3d8 0%, #f0f9eb 100%);
  border-color: #67c23a;
}
.card-no {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}
.masked {
  font-family: 'Courier New', monospace;
  letter-spacing: 2px;
}
.card-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.separator { margin: 0 6px; }
.card-actions { display: flex; gap: 8px; }
.loading, .empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #909399;
}
.rotating { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>