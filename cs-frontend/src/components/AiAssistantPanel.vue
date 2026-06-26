<template>
  <div class="ai-panel" v-if="show">
    <div class="ai-header">
      <span class="ai-icon">🤖</span>
      <span class="ai-title">AI 助手</span>
      <span class="ai-status" v-if="loading">思考中...</span>
      <span class="ai-status" v-else-if="suggestion">{{ suggestion.suggestionType }}</span>
      <el-button link size="small" @click="show = false">收起</el-button>
    </div>

    <!-- 推荐话术 -->
    <div v-if="suggestion" class="ai-suggestion">
      <div class="suggestion-content">{{ suggestion.content }}</div>
      <div class="suggestion-meta">
        <el-tag size="small" :type="confidenceType">{{ Math.round(suggestion.confidence || 0) }}% 置信度</el-tag>
        <span class="suggestion-sources" v-if="suggestion.sources">📚 {{ suggestion.sources }}</span>
      </div>
      <div class="suggestion-actions">
        <el-button size="small" type="primary" @click="onUse" :icon="Check">采纳并发送</el-button>
        <el-button size="small" @click="onModify" :icon="Edit">修改</el-button>
        <el-button size="small" plain @click="onSkip" :icon="Close">忽略</el-button>
      </div>
    </div>

    <!-- 修改弹窗 -->
    <el-dialog v-model="modifyVisible" title="修改 AI 推荐" width="500px">
      <el-input v-model="modifiedContent" type="textarea" :rows="4" />
      <template #footer>
        <el-button @click="modifyVisible = false">取消</el-button>
        <el-button type="primary" @click="onUseModified">发送修改版本</el-button>
      </template>
    </el-dialog>

    <!-- 知识库浏览 -->
    <el-collapse v-model="activeNames" class="ai-knowledge">
      <el-collapse-item title="📚 知识库" name="kb">
        <el-input v-model="kbQuery" placeholder="搜索知识..." size="small" clearable
          @change="onSearchKb" />
        <el-button size="small" @click="loadKnowledge('FAQ')" style="margin-top:8px">FAQ</el-button>
        <el-button size="small" @click="loadKnowledge('PRODUCT')">产品</el-button>
        <el-button size="small" @click="loadKnowledge('POLICY')">政策</el-button>
        <el-divider />
        <div v-for="k in knowledge" :key="k.id" class="kb-item" @click="onUseKb(k)">
          <div class="kb-title">{{ k.title }}</div>
          <div class="kb-content">{{ k.content }}</div>
          <el-tag size="small">{{ k.category }}</el-tag>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Edit, Close } from '@element-plus/icons-vue'
import { ai } from '../api'

const props = defineProps({
  agentUsername: String,
  customerId: String,
  sessionId: [Number, String]
})
const emit = defineEmits(['use', 'use-modified'])

const show = ref(true)
const loading = ref(false)
const suggestion = ref(null)
const modifyVisible = ref(false)
const modifiedContent = ref('')
const activeNames = ref([])
const knowledge = ref([])
const kbQuery = ref('')

const confidenceType = computed(() => {
  const c = suggestion.value?.confidence || 0
  if (c >= 85) return 'success'
  if (c >= 70) return 'warning'
  return 'info'
})

// 全局 stomp 单例（在 Agent.vue / Customer.vue onMounted 中连接）
function getStomp() {
  return window.__stompClient
}

onMounted(() => {
  // 轮询等待全局 stomp 连接
  let attempts = 0
  const timer = setInterval(() => {
    attempts++
    const stomp = getStomp()
    if (stomp && props.agentUsername) {
      clearInterval(timer)
      stomp.subscribe('/user/queue/ai-suggestion', msg => {
        try {
          const data = JSON.parse(msg.body)
          if (data.type === 'AI_SUGGESTION') {
            suggestion.value = data
            loading.value = false
            ElMessage.info('🤖 AI 助手有新的推荐话术')
          }
        } catch (e) { console.error(e) }
      })
    } else if (attempts > 30) {
      clearInterval(timer)
    }
  }, 500)
})

watch(() => props.sessionId, () => {
  suggestion.value = null
})

async function onUse() {
  if (!suggestion.value) return
  emit('use', suggestion.value)
  await ai.feedback({
    suggestionId: suggestion.value.suggestionId || suggestion.value.id,
    agentUsername: props.agentUsername,
    feedbackType: 'USED',
    rating: 5
  })
  ElMessage.success('已采纳')
  suggestion.value = null
}

function onModify() {
  modifiedContent.value = suggestion.value?.content || ''
  modifyVisible.value = true
}

async function onUseModified() {
  if (!modifiedContent.value.trim()) return
  emit('use-modified', modifiedContent.value)
  await ai.feedback({
    suggestionId: suggestion.value.id,
    agentUsername: props.agentUsername,
    feedbackType: 'MODIFIED',
    rating: 4,
    modifiedContent: modifiedContent.value
  })
  modifyVisible.value = false
  ElMessage.success('已发送修改版')
  suggestion.value = null
}

async function onSkip() {
  if (suggestion.value) {
    await ai.feedback({
      suggestionId: suggestion.value.id,
      agentUsername: props.agentUsername,
      feedbackType: 'SKIPPED',
      rating: 2
    })
  }
  suggestion.value = null
  ElMessage.info('已忽略')
}

async function loadKnowledge(category) {
  const resp = await ai.knowledge(category, 20)
  knowledge.value = resp.data || []
}

async function onSearchKb() {
  if (!kbQuery.value.trim()) {
    knowledge.value = []
    return
  }
  const resp = await ai.knowledgeSearch(kbQuery.value, 10)
  knowledge.value = resp.data || []
}

function onUseKb(k) {
  emit('use', { content: k.content, suggestionType: 'KNOWLEDGE' })
}
</script>

<style scoped>
.ai-panel {
  border: 1px solid #d6e4ff;
  background: #f0f5ff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.ai-header {
  display: flex; align-items: center;
  font-size: 14px; font-weight: 600;
  margin-bottom: 8px;
}
.ai-icon { font-size: 18px; margin-right: 6px; }
.ai-title { flex: 1; color: #1d39c4; }
.ai-status {
  font-size: 12px;
  color: #595959;
  margin-right: 8px;
}
.ai-suggestion {
  background: #fff;
  border-left: 3px solid #1677ff;
  padding: 12px;
  border-radius: 4px;
  margin-bottom: 12px;
}
.suggestion-content {
  font-size: 13px;
  line-height: 1.6;
  color: #262626;
  margin-bottom: 8px;
}
.suggestion-meta {
  display: flex; align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 8px;
}
.suggestion-actions {
  display: flex; gap: 6px;
}
.ai-knowledge {
  margin-top: 12px;
}
.kb-item {
  padding: 8px;
  background: #fff;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.kb-item:hover {
  background: #e6f4ff;
  border-left: 3px solid #1677ff;
}
.kb-title {
  font-weight: 600;
  font-size: 13px;
  color: #262626;
  margin-bottom: 4px;
}
.kb-content {
  font-size: 12px;
  color: #595959;
  margin-bottom: 4px;
  line-height: 1.5;
}
</style>