<template>
  <el-card class="compliance-panel" shadow="hover">
    <template #header>
      <div class="header">
        <el-icon><Document /></el-icon>
        <span>金融合规检查</span>
        <el-tag v-if="result" :type="result.overallResult === 'PASS' ? 'success' : 'danger'" size="small">
          {{ result.overallResult === 'PASS' ? '✓ 合规通过' : '✗ 合规拒绝' }}
        </el-tag>
      </div>
    </template>

    <div v-if="loading" class="loading">
      <el-icon class="rotating"><Loading /></el-icon>
      正在执行 4 项合规检查...
    </div>

    <div v-else-if="result" class="check-list">
      <div
        v-for="item in checks"
        :key="item.key"
        class="check-item"
        :class="result[item.key] === 1 ? 'pass' : 'fail'"
      >
        <el-icon class="check-icon">
          <CircleCheck v-if="result[item.key] === 1" />
          <CircleClose v-else />
        </el-icon>
        <div class="check-content">
          <div class="check-title">{{ item.title }}</div>
          <div class="check-desc">{{ item.desc }}</div>
        </div>
      </div>

      <el-alert
        v-if="result.remark"
        :title="result.remark"
        :type="result.overallResult === 'PASS' ? 'success' : 'warning'"
        :closable="false"
        show-icon
        style="margin-top: 16px"
      />
    </div>

    <div v-else class="empty">
      <el-button type="primary" @click="runCheck" :loading="loading">
        开始合规检查
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Document, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const props = defineProps({
  orderNo: { type: String, required: true }
})

const emit = defineEmits(['checked'])

const loading = ref(false)
const result = ref(null)

const checks = [
  { key: 'identityCheck', title: '① 实名认证（KYC）', desc: '核对身份证 + 人脸 + 活体' },
  { key: 'riskCheck', title: '② 风险等级评估', desc: '评估用户风险承受等级' },
  { key: 'suitabilityCheck', title: '③ 产品适配性', desc: '产品风险与用户风险等级匹配' },
  { key: 'amlCheck', title: '④ 反洗钱筛查', desc: '检查命中黑名单 + 可疑交易' }
]

async function runCheck() {
  loading.value = true
  try {
    const { data } = await request.post(`/order/${props.orderNo}/compliance`)
    result.value = data
    ElMessage[data.overallResult === 'PASS' ? 'success' : 'warning'](
      data.overallResult === 'PASS' ? '合规通过，可继续下单' : '合规未通过'
    )
    emit('checked', data)
  } catch (e) {
    ElMessage.error('合规检查失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

defineExpose({ runCheck, result })
onMounted(() => {
  // 自动执行
  runCheck()
})
</script>

<style scoped>
.compliance-panel { margin-bottom: 16px; }
.header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.check-list {
  display: grid;
  gap: 12px;
}
.check-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 6px;
  background: #f5f7fa;
}
.check-item.pass { border-left: 3px solid #67c23a; }
.check-item.fail { border-left: 3px solid #f56c6c; background: #fef0f0; }
.check-icon { font-size: 22px; }
.check-item.pass .check-icon { color: #67c23a; }
.check-item.fail .check-icon { color: #f56c6c; }
.check-title { font-weight: 500; }
.check-desc { font-size: 12px; color: #909399; margin-top: 2px; }
.loading, .empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  color: #909399;
}
.rotating { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>