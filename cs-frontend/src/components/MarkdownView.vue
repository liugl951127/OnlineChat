<template>
  <div class="markdown-view" v-html="rendered"></div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  source: { type: String, default: '' }
})

marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false
})

const rendered = computed(() => {
  if (!props.source) return ''
  try {
    return marked(props.source)
  } catch {
    return props.source.replace(/\n/g, '<br/>')
  }
})
</script>

<style scoped>
.markdown-view {
  font-size: 14px;
  line-height: 1.7;
  color: #262626;
}
.markdown-view :deep(h1) {
  font-size: 20px; font-weight: 600;
  border-bottom: 1px solid #e8e8e8; padding-bottom: 4px;
  margin: 12px 0 8px;
}
.markdown-view :deep(h2) {
  font-size: 18px; font-weight: 600; margin: 10px 0 6px;
}
.markdown-view :deep(h3) {
  font-size: 16px; font-weight: 600; margin: 8px 0 4px;
}
.markdown-view :deep(p) { margin: 6px 0; }
.markdown-view :deep(strong) { font-weight: 600; color: #1d39c4; }
.markdown-view :deep(em) { font-style: italic; }
.markdown-view :deep(code) {
  background: #f5f5f5; padding: 2px 6px;
  border-radius: 3px; font-family: monospace;
  font-size: 12px;
}
.markdown-view :deep(pre) {
  background: #1f2329; color: #f5f5f5;
  padding: 12px; border-radius: 6px;
  overflow-x: auto; margin: 8px 0;
}
.markdown-view :deep(pre code) {
  background: transparent; color: inherit; padding: 0;
}
.markdown-view :deep(ul), .markdown-view :deep(ol) {
  padding-left: 20px; margin: 6px 0;
}
.markdown-view :deep(li) { margin: 2px 0; }
.markdown-view :deep(a) { color: #1677ff; text-decoration: none; }
.markdown-view :deep(a):hover { text-decoration: underline; }
.markdown-view :deep(blockquote) {
  border-left: 3px solid #d9d9d9;
  padding-left: 12px; color: #595959;
  margin: 6px 0;
}
.markdown-view :deep(hr) {
  border: none; border-top: 1px solid #e8e8e8;
  margin: 12px 0;
}
.markdown-view :deep(table) {
  border-collapse: collapse; margin: 8px 0;
}
.markdown-view :deep(th), .markdown-view :deep(td) {
  border: 1px solid #e8e8e8; padding: 6px 10px;
}
</style>