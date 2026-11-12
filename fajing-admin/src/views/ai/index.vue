<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAiConfig, saveAiConfig } from '@/api'

const loading = ref(false)
const saving = ref(false)

const form = reactive({
  imageProvider: 'siliconflow',
  imageBaseUrl: '',
  imageApiKey: '',
  imageModel: '',
  textBaseUrl: '',
  textApiKey: '',
  textModel: '',
  promptTemplate: '',
  systemPrompt: '',
  useTextEnhance: 0,
  enabled: 1
})

const placeholders = [
  { key: '{style}', desc: '发型名称，如「法式波波头」' },
  { key: '{en}', desc: '英文造型描述，如「french bob」' },
  { key: '{len}', desc: '长度描述，如「肩部以上」' },
  { key: '{face}', desc: '适合脸型，如「圆脸 / 鹅蛋脸」' },
  { key: '{color}', desc: '发色指令，未选发色时为空' }
]

async function load() {
  loading.value = true
  try {
    const data = await fetchAiConfig()
    if (data) {
      Object.keys(form).forEach((k) => {
        if (data[k] !== undefined && data[k] !== null) form[k] = data[k]
      })
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await saveAiConfig({ ...form })
    ElMessage.success('AI 设置已保存，下一张生成立即生效')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="ai-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="换模型只需改这里"
      description="图像模型走 OpenAI 兼容的 /images/generations 接口，填任意兼容服务（如硅基流动、火山方舟、智谱）的地址、Key 与模型名即可，无需改代码。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" class="block">
      <template #header><b>图像生成模型</b><span class="hint">负责真正的换发出图</span></template>
      <el-form label-width="120px">
        <el-form-item label="启用真实出图">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
          <span class="inline-hint">关闭后使用占位图，仅用于演示</span>
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="form.imageBaseUrl" placeholder="https://api.siliconflow.cn/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.imageApiKey" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="模型名">
          <el-input v-model="form.imageModel" placeholder="Qwen/Qwen-Image-Edit-2509" />
          <div class="hint">需支持图生图/指令编辑，例如 Qwen-Image-Edit、FLUX.1 Kontext</div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header><b>文案模型</b><span class="hint">把模板改写成更专业的编辑指令</span></template>
      <el-form label-width="120px">
        <el-form-item label="启用提示词增强">
          <el-switch v-model="form.useTextEnhance" :active-value="1" :inactive-value="0" />
          <span class="inline-hint">关闭时直接使用下方模板，速度更快</span>
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="form.textBaseUrl" placeholder="https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.textApiKey" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="模型名">
          <el-input v-model="form.textModel" placeholder="deepseek-flash" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header><b>提示词</b><span class="hint">AI 换发的核心规则，决定像不像本人</span></template>
      <el-form label-width="120px">
        <el-form-item label="编辑指令模板">
          <el-input v-model="form.promptTemplate" type="textarea" :rows="7" resize="vertical" />
        </el-form-item>
        <el-form-item label="可用占位符">
          <div class="tags">
            <el-tag v-for="p in placeholders" :key="p.key" type="info" effect="plain">
              {{ p.key }} · {{ p.desc }}
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item label="文案模型提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" resize="vertical" />
          <div class="hint">仅在「启用提示词增强」时生效，用于约束文案模型输出</div>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="actions">
      <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      <el-button :disabled="saving" @click="load">重新读取</el-button>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  padding: 4px 2px 20px;
}
.block {
  margin-bottom: 16px;
  border-radius: 10px;
}
.block :deep(.el-card__header) {
  padding: 14px 18px;
  font-size: 14px;
}
.hint {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: #9aa0a6;
}
.inline-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #9aa0a6;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.actions {
  display: flex;
  gap: 10px;
}
</style>
