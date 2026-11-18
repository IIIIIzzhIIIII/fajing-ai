<template>
  <view class="doc-content">
    <template v-for="(block, index) in blocks" :key="index">
      <view v-if="block.type === 'h'" class="doc-t">{{ block.text }}</view>
      <view v-else class="doc-p">{{ block.text }}</view>
    </template>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 纯文本正文：空行分段，## 开头为小标题 */
  text: { type: String, default: '' }
})

const blocks = computed(() => {
  const raw = String(props.text || '').replace(/\r\n/g, '\n')
  const result = []
  let paragraph = []
  const flush = () => {
    if (paragraph.length) {
      result.push({ type: 'p', text: paragraph.join('\n') })
      paragraph = []
    }
  }
  raw.split('\n').forEach((line) => {
    const text = line.trim()
    if (!text) {
      flush()
      return
    }
    if (text.startsWith('## ')) {
      flush()
      result.push({ type: 'h', text: text.slice(3).trim() })
      return
    }
    paragraph.push(text)
  })
  flush()
  return result
})
</script>

<style lang="scss" scoped>
.doc-t {
  margin: 14px 0 6px;
  font-size: 13.5px;
  font-weight: 800;
  color: var(--ink);
}
.doc-p {
  margin-bottom: 10px;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--ink-2);
}
</style>
