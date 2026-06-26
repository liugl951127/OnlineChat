<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kyc } from '@/api'

/**
 * KYC 完整流程页（v2.0.0）
 *
 * 7 步：
 *   1. 创建申请单
 *   2. 身份证 OCR 上传
 *   3. 活体检测（3 个动作）
 *   4. 人脸比对
 *   5. 视频双录（朗读风险声明）
 *   6. 提交审核
 *   7. 银行卡四要素绑卡
 */

const props = defineProps({
  customerId: { type: String, default: '' }
})
const emit = defineEmits(['close', 'completed'])

const currentStep = ref(0)
const steps = [
  { title: '创建申请', desc: '初始化 KYC 申请单' },
  { title: '身份证 OCR', desc: '上传身份证正反面' },
  { title: '活体检测', desc: '完成 3 个动作' },
  { title: '人脸比对', desc: '对比身份证照片' },
  { title: '视频双录', desc: '朗读风险声明' },
  { title: '坐席审核', desc: '后台审核员审核' },
  { title: '绑卡鉴权', desc: '银行卡四要素' }
]

const application = ref(null)
const statements = ref([])
const currentStatement = ref(null)
const recording = ref(false)
const recorded = ref(false)
const videoBlob = ref(null)
const videoUrl = ref('')
const videoDuration = ref(0)
const idCardForm = reactive({
  frontImgUrl: '',
  backImgUrl: ''
})
const livenessForm = reactive({
  faceImgUrl: '',
  actions: []
})
const bankForm = reactive({
  cardNo: '',
  cardName: '',
  idCardNo: '',
  mobile: ''
})

onMounted(async () => {
  await loadMy()
  await loadStatements()
  if (application.value && application.value.status !== 'COMPLETED' && application.value.status !== 'REJECTED') {
    jumpToCurrentStep()
  }
})

function jumpToCurrentStep() {
  const map = {
    'INIT': 0,
    'OCR_UPLOADED': 1,
    'LIVENESS_PASSED': 2,
    'FACE_MATCHED': 3,
    'VIDEO_RECORDED': 4,
    'AUDITING': 5,
    'APPROVED': 6,
    'BANK_BINDING': 6
  }
  currentStep.value = map[application.value.status] ?? 0
}

async function loadMy() {
  try {
    const { data } = await kyc.my()
    application.value = data
  } catch (e) {}
}

async function loadStatements() {
  try {
    const { data } = await kyc.statements()
    statements.value = data || []
    currentStatement.value = statements.value[0]
  } catch (e) {}
}

// ============ 步骤 1: 创建申请 ============
async function stepCreate() {
  try {
    const { data } = await kyc.create()
    application.value = data
    currentStep.value = 1
    ElMessage.success('申请单已创建')
  } catch (e) {}
}

// ============ 步骤 2: 身份证 OCR ============
async function uploadFront(file) {
  // Mock：直接用文件名当 URL
  idCardForm.frontImgUrl = 'https://oss.example.com/idcard/front-' + Date.now() + '.jpg'
  ElMessage.success('正面已上传')
}
async function uploadBack(file) {
  idCardForm.backImgUrl = 'https://oss.example.com/idcard/back-' + Date.now() + '.jpg'
  ElMessage.success('反面已上传')
}
async function stepUploadIdcard() {
  if (!idCardForm.frontImgUrl || !idCardForm.backImgUrl) {
    return ElMessage.warning('请先上传身份证正反面')
  }
  try {
    const { data } = await kyc.uploadIdcard(idCardForm)
    application.value = data
    if (data.status === 'REJECTED') {
      return ElMessage.error('该身份证命中黑名单')
    }
    currentStep.value = 2
    ElMessage.success('OCR 识别完成')
  } catch (e) {}
}

// ============ 步骤 3: 活体检测 ============
const livenessActions = [
  { value: 'MOUTH', label: '张嘴' },
  { value: 'BLINK', label: '眨眼' },
  { value: 'SHAKE', label: '摇头' },
  { value: 'NOD', label: '点头' }
]
async function uploadFace(file) {
  livenessForm.faceImgUrl = 'https://oss.example.com/face-' + Date.now() + '.jpg'
  ElMessage.success('活体照片已上传')
}
async function stepLiveness() {
  if (!livenessForm.faceImgUrl) return ElMessage.warning('请先上传活体照片')
  if (livenessForm.actions.length < 3) return ElMessage.warning('请完成至少 3 个动作')
  try {
    const { data } = await kyc.liveness(livenessForm)
    application.value = data
    if (data.status === 'REJECTED') {
      return ElMessage.error('活体检测未通过，请重试')
    }
    currentStep.value = 3
    ElMessage.success('活体检测通过')
  } catch (e) {}
}

// ============ 步骤 4: 人脸比对 ============
async function stepFaceMatch() {
  try {
    const { data } = await kyc.faceMatch()
    application.value = data
    if (data.status === 'REJECTED') {
      return ElMessage.error('人脸比对未通过，请重新活体检测')
    }
    currentStep.value = 4
    ElMessage.success(`人脸比对通过（相似度 ${data.faceMatchScore}）`)
  } catch (e) {}
}

// ============ 步骤 5: 视频双录 ============
let mediaRecorder = null
let recordingStartTime = 0
let recordingTimer = null

async function startRecording() {
  try {
    if (!currentStatement.value) return ElMessage.warning('请选择风险声明')
    // Mock 录制：用 MediaRecorder 录 webm
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    mediaRecorder = new MediaRecorder(stream)
    const chunks = []
    mediaRecorder.ondataavailable = e => chunks.push(e.data)
    mediaRecorder.onstop = () => {
      videoBlob.value = new Blob(chunks, { type: 'video/webm' })
      videoUrl.value = URL.createObjectURL(videoBlob.value)
      recorded.value = true
      stream.getTracks().forEach(t => t.stop())
      clearInterval(recordingTimer)
    }
    mediaRecorder.start()
    recordingStartTime = Date.now()
    recording.value = true
    recordingTimer = setInterval(() => {
      videoDuration.value = Math.floor((Date.now() - recordingStartTime) / 1000)
    }, 200)
    ElMessage.success('开始录制')
  } catch (e) {
    // 没有摄像头/麦克风权限时降级 Mock
    recording.value = true
    videoDuration.value = 0
    const timer = setInterval(() => {
      videoDuration.value++
      if (videoDuration.value >= 15) {
        clearInterval(timer)
        recording.value = false
        recorded.value = true
        videoUrl.value = ''
      }
    }, 1000)
    ElMessage.warning('无摄像头权限，使用 Mock 录制 15 秒')
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.stop()
  }
  recording.value = false
  // Mock 模式下：手动确认时长
  if (!recorded.value && videoDuration.value >= 15) {
    recorded.value = true
  }
}

function selectStatement(s) {
  currentStatement.value = s
}

async function stepUploadVideo() {
  if (!recorded.value) return ElMessage.warning('请先完成视频录制')
  if (!currentStatement.value) return ElMessage.warning('请选择风险声明')
  try {
    // Mock：直接传 base64
    const { data } = await kyc.submitVideo({
      statementCode: currentStatement.value.code,
      videoBase64: 'mock-base64-' + Date.now(),
      durationSec: videoDuration.value
    })
    application.value = data
    currentStep.value = 5
    ElMessage.success('视频提交成功')
  } catch (e) {}
}

// ============ 步骤 6: 提交审核 ============
async function stepSubmitAudit() {
  try {
    const { data } = await kyc.submitAudit()
    application.value = data
    ElMessage.success('已提交审核，请等待坐席审核（通常 1 个工作日）')
    currentStep.value = 5
  } catch (e) {}
}

// ============ 步骤 7: 绑卡 ============
async function stepBindCard() {
  if (!bankForm.cardNo || !bankForm.cardName || !bankForm.idCardNo || !bankForm.mobile) {
    return ElMessage.warning('请填写完整银行卡四要素')
  }
  try {
    const { data } = await kyc.bindCard(bankForm)
    application.value = data
    if (data.status === 'COMPLETED') {
      ElMessage.success('🎉 KYC 认证完成！')
      emit('completed', data)
      // 1.5 秒后自动关闭
      setTimeout(() => emit('close'), 1500)
    }
  } catch (e) {}
}

// ============ 状态辅助 ============
const statusLabel = (s) => ({
  INIT: '待创建', OCR_UPLOADED: '已上传身份证', LIVENESS_PASSED: '活体已通过',
  FACE_MATCHED: '人脸已比对', VIDEO_RECORDED: '视频已录制', AUDITING: '审核中',
  APPROVED: '审核通过', BANK_BINDING: '绑卡中', COMPLETED: '已完成', REJECTED: '已拒绝'
}[s] || s)

const statusColor = (s) => ({
  COMPLETED: 'success', APPROVED: 'success', AUDITING: 'warning',
  REJECTED: 'danger', INIT: 'info'
}[s] || '')
</script>

<template>
  <div class="kyc-flow">
    <div class="kf-header">
      <h3>🔐 实名认证 (KYC)</h3>
      <el-button text @click="emit('close')">关闭 ✕</el-button>
    </div>

    <!-- 状态条 -->
    <div v-if="application" class="status-bar">
      <el-tag :type="statusColor(application.status)" size="large">
        {{ statusLabel(application.status) }}
      </el-tag>
      <span class="app-no">申请单号：{{ application.applicationNo }}</span>
    </div>

    <!-- 步骤条 -->
    <el-steps :active="currentStep" finish-status="success" align-center class="kf-steps">
      <el-step v-for="(s, idx) in steps" :key="idx" :title="s.title" :description="s.desc" />
    </el-steps>

    <!-- 步骤内容 -->
    <div class="step-content">
      <!-- 0: 创建申请 -->
      <div v-if="currentStep === 0">
        <h4>📋 准备开始 KYC 认证</h4>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
          为保障您的资金安全，监管要求所有客户必须完成 KYC（Know Your Customer）认证。
          整个流程约 5 分钟，包括：身份证识别、活体检测、人脸比对、视频双录、绑卡鉴权。
        </el-alert>
        <el-button type="primary" size="large" @click="stepCreate">开始认证</el-button>
      </div>

      <!-- 1: 身份证 OCR -->
      <div v-if="currentStep === 1">
        <h4>📷 上传身份证</h4>
        <el-form label-width="100px">
          <el-form-item label="身份证正面">
            <el-input v-model="idCardForm.frontImgUrl" placeholder="点击上传" readonly>
              <template #append>
                <el-upload :show-file-list="false" :http-request="uploadFront">
                  <el-button>上传</el-button>
                </el-upload>
              </template>
            </el-input>
            <div class="upload-preview" v-if="idCardForm.frontImgUrl">✓ 正面已上传</div>
          </el-form-item>
          <el-form-item label="身份证反面">
            <el-input v-model="idCardForm.backImgUrl" placeholder="点击上传" readonly>
              <template #append>
                <el-upload :show-file-list="false" :http-request="uploadBack">
                  <el-button>上传</el-button>
                </el-upload>
              </template>
            </el-input>
            <div class="upload-preview" v-if="idCardForm.backImgUrl">✓ 反面已上传</div>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="stepUploadIdcard">提交 OCR 识别</el-button>
      </div>

      <!-- 2: 活体检测 -->
      <div v-if="currentStep === 2">
        <h4>👤 活体检测</h4>
        <el-form label-width="100px">
          <el-form-item label="活体照片">
            <el-input v-model="livenessForm.faceImgUrl" placeholder="点击拍照" readonly>
              <template #append>
                <el-upload :show-file-list="false" :http-request="uploadFace">
                  <el-button>拍照</el-button>
                </el-upload>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="动作序列">
            <el-checkbox-group v-model="livenessForm.actions">
              <el-checkbox v-for="a in livenessActions" :key="a.value" :label="a.value">
                {{ a.label }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="stepLiveness">提交活体检测</el-button>
      </div>

      <!-- 3: 人脸比对 -->
      <div v-if="currentStep === 3">
        <h4>🔍 人脸比对</h4>
        <el-alert type="info" :closable="false" show-icon>
          系统将自动对比身份证照片与活体照片，确认是您本人。
        </el-alert>
        <el-button type="primary" @click="stepFaceMatch" style="margin-top: 16px">开始比对</el-button>
      </div>

      <!-- 4: 视频双录 -->
      <div v-if="currentStep === 4">
        <h4>🎥 视频双录</h4>

        <h5>请选择风险声明（监管要求朗读）：</h5>
        <el-radio-group v-model="currentStatement" class="statement-list">
          <el-radio v-for="s in statements" :key="s.code" :value="s" border @change="selectStatement(s)">
            <div>
              <div><b>{{ s.title }}</b>（{{ s.requiredDurationSec }}秒）</div>
              <div class="statement-content">{{ s.content.substring(0, 100) }}...</div>
            </div>
          </el-radio>
        </el-radio-group>

        <div v-if="currentStatement" class="video-area">
          <h5>📜 请朗读：</h5>
          <div class="script">{{ currentStatement.content }}</div>
        </div>

        <div class="record-controls">
          <div v-if="!recording && !recorded">
            <el-button type="primary" size="large" @click="startRecording">🎬 开始录制</el-button>
          </div>
          <div v-else-if="recording">
            <el-button type="danger" size="large" @click="stopRecording">⏹ 停止录制 ({{ videoDuration }}s)</el-button>
            <div class="rec-tip">正在录制中...请清晰朗读上方声明</div>
          </div>
          <div v-else-if="recorded">
            <el-alert type="success" :closable="false" show-icon>
              录制完成（{{ videoDuration }}秒）
            </el-alert>
            <div style="margin-top: 12px;">
              <el-button @click="recorded = false; videoDuration = 0">重新录制</el-button>
              <el-button type="primary" @click="stepUploadVideo">提交视频</el-button>
            </div>
          </div>
        </div>

        <video v-if="videoUrl" :src="videoUrl" controls style="max-width: 100%; margin-top: 12px;" />
      </div>

      <!-- 5: 审核 -->
      <div v-if="currentStep === 5">
        <h4>⏳ 等待坐席审核</h4>
        <el-alert v-if="application && application.status === 'AUDITING'" type="warning" :closable="false" show-icon>
          已提交审核，请等待 1 个工作日内完成。
        </el-alert>
        <el-alert v-else-if="application && application.status === 'APPROVED'" type="success" :closable="false" show-icon>
          审核通过！请继续下一步绑卡。
        </el-alert>
        <el-alert v-else-if="application && application.status === 'REJECTED'" type="error" :closable="false" show-icon>
          审核未通过：{{ application.auditRemark }}，需重新提交。
        </el-alert>
        <div v-if="application && application.status !== 'AUDITING'" style="margin-top: 16px;">
          <el-button type="primary" @click="currentStep = 6">下一步：绑卡</el-button>
        </div>
      </div>

      <!-- 6: 绑卡 -->
      <div v-if="currentStep === 6">
        <h4>💳 银行卡四要素鉴权</h4>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
          请填写您本人的储蓄卡信息（监管要求四要素一致）。
        </el-alert>
        <el-form label-width="100px">
          <el-form-item label="卡号">
            <el-input v-model="bankForm.cardNo" placeholder="如 6222 0212 3456 7890 123" />
          </el-form-item>
          <el-form-item label="持卡人姓名">
            <el-input v-model="bankForm.cardName" placeholder="如 张三" />
          </el-form-item>
          <el-form-item label="身份证号">
            <el-input v-model="bankForm.idCardNo" placeholder="如 110105199001151234" />
          </el-form-item>
          <el-form-item label="预留手机号">
            <el-input v-model="bankForm.mobile" placeholder="如 13800138000" />
          </el-form-item>
        </el-form>
        <el-button type="primary" size="large" @click="stepBindCard">提交鉴权</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.kyc-flow {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  max-height: 750px;
  overflow-y: auto;
}
.kf-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  h3 { margin: 0; }
}
.status-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 6px;
  margin-bottom: 16px;
  .app-no { font-size: 12px; color: #86909c; }
}
.kf-steps {
  margin-bottom: 24px;
}
.step-content {
  padding: 16px;
  background: #fafbfc;
  border-radius: 8px;
  h4 { margin: 0 0 12px; font-size: 16px; }
  h5 { margin: 12px 0 8px; font-size: 13px; color: #595959; }
}
.upload-preview {
  font-size: 12px; color: #389e0d; margin-top: 4px;
}
.statement-list {
  display: flex; flex-direction: column; gap: 8px;
  width: 100%;
  :deep(.el-radio) { width: 100%; margin-right: 0; padding: 12px; }
  .statement-content { font-size: 12px; color: #86909c; margin-top: 4px; }
}
.script {
  background: #fff;
  border-left: 4px solid #1677ff;
  padding: 12px;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.8;
  max-height: 200px;
  overflow-y: auto;
}
.video-area { margin: 16px 0; }
.record-controls { margin-top: 16px; }
.rec-tip { font-size: 12px; color: #d46b08; margin-top: 8px; }
</style>