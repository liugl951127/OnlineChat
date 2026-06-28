<script setup>
/**
 * 公众号关注引导弹窗 (v2.2.80)
 *
 * 场景: 用户点击公众号登录, 后端检测未关注公众号,
 *        抛 451 错误, 客户端弹此弹窗引导用户扫码关注.
 *
 * Props:
 *   modelValue: 是否显示
 *   qrcodeUrl:  公众号关注二维码 URL (mock 时是 "MOCK-QRCODE-xxx")
 *   subscribeUrl: 公众号主页 URL (用于点击跳转)
 *   openid:     用户的 openid
 */
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  qrcodeUrl: { type: String, default: '' },
  subscribeUrl: { type: String, default: '' },
  openid: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'recheck'])

const rechecking = ref(false)

watch(() => props.modelValue, (v) => {
  if (v) rechecking.value = false
})

async function recheck() {
  rechecking.value = true
  try {
    const { auth } = await import('@/api')
    const { data } = await auth.checkOaSubscribe(props.openid)
    if (data.code === 0 && data.data?.subscribed) {
      emit('recheck', data.data)
    } else {
      rechecking.value = false
      // 仍未关注, 保持弹窗
    }
  } catch (e) {
    console.warn('[SubscribeDialog] recheck failed:', e)
    rechecking.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function openSubscribe() {
  if (props.subscribeUrl) {
    window.open(props.subscribeUrl, '_blank')
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="请先关注公众号"
    width="420px"
    :show-close="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="subscribe-content">
      <!-- mock 模式: 显示占位 + 提示 -->
      <div v-if="qrcodeUrl?.startsWith('MOCK-')" class="mock-qrcode">
        <div class="mock-box">
          <div class="qr-icon">📱</div>
          <p class="qr-label">公众号二维码</p>
          <p class="qr-mock-hint">[ MOCK 模式 - 真实环境显示二维码 ]</p>
          <p class="qr-mock-info">openid: {{ openid }}</p>
        </div>
      </div>
      <!-- 真实模式: 显示二维码图片 -->
      <div v-else-if="qrcodeUrl" class="real-qrcode">
        <img :src="qrcodeUrl" alt="公众号二维码" class="qr-img" />
      </div>

      <el-alert type="info" :closable="false" style="margin: 16px 0">
        <template #title>
          <small>
            为正常使用客服功能，请先扫码关注我们的公众号<br>
            关注后点击下方「我已关注」按钮重新检测
          </small>
        </template>
      </el-alert>

      <div class="subscribe-actions">
        <el-button type="primary" plain @click="openSubscribe">
          打开公众号主页
        </el-button>
        <el-button :loading="rechecking" @click="recheck">
          我已关注，重新检测
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.subscribe-content {
  text-align: center;
  padding: 8px;
}
.mock-qrcode {
  display: flex;
  justify-content: center;
}
.mock-box {
  background: #f0f7ff;
  border: 2px dashed #1677ff;
  border-radius: 12px;
  padding: 32px 24px;
  width: 200px;
  .qr-icon {
    font-size: 48px;
    margin-bottom: 8px;
  }
  .qr-label {
    color: #595959;
    font-size: 13px;
    margin: 4px 0;
  }
  .qr-mock-hint {
    color: #fa8c16;
    font-size: 11px;
    margin: 4px 0;
  }
  .qr-mock-info {
    color: #bfbfbf;
    font-size: 10px;
    font-family: monospace;
    word-break: break-all;
  }
}
.real-qrcode {
  display: flex;
  justify-content: center;
  .qr-img {
    width: 200px;
    height: 200px;
    border: 1px solid #f0f0f0;
    border-radius: 8px;
  }
}
.subscribe-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}
</style>