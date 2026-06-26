<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { faq } from '@/api'

/**
 * 知识库浏览组件（v1.9.0）
 *
 * 功能：
 * - 搜索 FAQ（关键词）
 * - 浏览热门 FAQ
 * - 浏览分类
 * - 点击 FAQ 显示答案
 */

const props = defineProps({
  customerId: { type: String, default: '' }
})
const emit = defineEmits(['close', 'use-faq'])

const keyword = ref('')
const searchResults = ref([])
const topFaqs = ref([])
const categories = ref([])
const selectedFaq = ref(null)

onMounted(async () => {
  await loadTop()
  await loadCategories()
})

async function loadTop() {
  try {
    const { data } = await faq.top()
    topFaqs.value = (data || []).slice(0, 10)
  } catch (e) {}
}

async function loadCategories() {
  try {
    const { data } = await faq.topCategories()
    categories.value = data || []
  } catch (e) {}
}

async function doSearch() {
  if (!keyword.value.trim()) return
  try {
    const { data } = await faq.search(keyword.value)
    searchResults.value = data || []
    if (searchResults.value.length === 0) {
      ElMessage.info('未找到相关问题')
    }
  } catch (e) {}
}

async function openFaq(f) {
  selectedFaq.value = f
  // 浏览 +1
  try { await faq.view(f.id) } catch (e) {}
}

async function loadCategoryFaq(categoryId) {
  try {
    const { data } = await faq.byCategory(categoryId)
    searchResults.value = data || []
    keyword.value = ''
  } catch (e) {}
}
</script>

<template>
  <div class="knowledge-base">
    <div class="kb-header">
      <h3>📚 知识库</h3>
      <el-button text @click="emit('close')">关闭 ✕</el-button>
    </div>

    <el-input
      v-model="keyword"
      placeholder="搜索问题或关键词"
      clearable
      @keyup.enter="doSearch"
      style="margin-bottom: 12px"
    >
      <template #append><el-button @click="doSearch">搜索</el-button></template>
    </el-input>

    <div v-if="searchResults.length" class="section">
      <h4>🔍 搜索结果（{{ searchResults.length }}）</h4>
      <div
        v-for="f in searchResults"
        :key="f.id"
        class="faq-item"
        @click="openFaq(f)"
      >
        <div class="q">Q: {{ f.question }}</div>
        <div class="a">{{ f.answer.substring(0, 80) }}{{ f.answer.length > 80 ? '...' : '' }}</div>
      </div>
    </div>

    <div v-else-if="categories.length" class="section">
      <h4>📂 分类浏览</h4>
      <div class="cat-grid">
        <div
          v-for="c in categories"
          :key="c.id"
          class="cat-card"
          @click="loadCategoryFaq(c.id)"
        >
          <div class="cat-icon">📁</div>
          <div class="cat-name">{{ c.name }}</div>
        </div>
      </div>
    </div>

    <div v-if="topFaqs.length" class="section">
      <h4>🔥 热门问题</h4>
      <div
        v-for="f in topFaqs"
        :key="f.id"
        class="faq-item"
        @click="openFaq(f)"
      >
        <div class="q">Q: {{ f.question }} 👁 {{ f.viewCount }}</div>
        <div class="a">{{ f.answer.substring(0, 80) }}{{ f.answer.length > 80 ? '...' : '' }}</div>
      </div>
    </div>

    <el-dialog v-model="selectedFaq" :title="selectedFaq?.question" width="600px" :show-close="true" @close="selectedFaq = null">
      <div v-if="selectedFaq">
        <div class="faq-content">{{ selectedFaq.answer }}</div>
        <div class="faq-meta">
          <span>👁 {{ selectedFaq.viewCount }} 浏览</span>
          <span>👍 {{ selectedFaq.helpfulCount }}</span>
          <span>👎 {{ selectedFaq.unhelpfulCount }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="emit('use-faq', selectedFaq)">发给客服</el-button>
        <el-button type="primary" @click="selectedFaq = null">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.knowledge-base { padding: 12px; }
.kb-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  h3 { margin: 0; }
}
.section {
  margin-bottom: 16px;
  h4 { font-size: 13px; color: #86909c; margin-bottom: 8px; }
}
.faq-item {
  background: #fafafa;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: #e6f4ff; transform: translateX(2px); }
  .q { font-size: 13px; color: #1677ff; font-weight: 500; margin-bottom: 4px; }
  .a { font-size: 12px; color: #595959; line-height: 18px; }
}
.cat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
}
.cat-card {
  background: #fafafa;
  border-radius: 6px;
  padding: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: #e6f4ff; }
  .cat-icon { font-size: 24px; margin-bottom: 4px; }
  .cat-name { font-size: 12px; }
}
.faq-content {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  margin-bottom: 12px;
}
.faq-meta {
  display: flex; gap: 16px;
  font-size: 12px; color: #86909c;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
</style>