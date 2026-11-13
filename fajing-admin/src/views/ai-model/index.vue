<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, Upload } from '@element-plus/icons-vue'
import { fetchHairstyles, generateModelShot, updateHairstyle, uploadImage } from '@/api'

const router = useRouter()

const imageUrl = ref('')
const resultUrl = ref('')
const loading = ref(false)
const uploaded = ref(false)
const hairstyles = ref([])
const targetId = ref('')

const canGenerate = computed(() => !!imageUrl.value.trim() && !loading.value)

function loadHairstyles() {
  fetchHairstyles()
    .then((data) => {
      hairstyles.value = Array.isArray(data) ? data : []
    })
    .catch(() => {
      hairstyles.value = []
    })
}

async function handleUpload(options) {
  const file = options && options.file
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过 10MB')
    return
  }
  uploaded.value = true
  try {
    const data = await uploadImage(file)
    imageUrl.value = (data && (data.url || data.path)) || ''
    ElMessage.success('图片已上传')
  } catch {
    // 拦截器已提示
  } finally {
    uploaded.value = false
  }
}

async function generate() {
  if (!canGenerate.value) return
  loading.value = true
  try {
    const data = await generateModelShot(imageUrl.value.trim())
    resultUrl.value = (data && data.url) || ''
    ElMessage.success('已生成标准模特照')
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function writeBack() {
  if (!resultUrl.value) {
    ElMessage.warning('请先生成标准模特照')
    return
  }
  const target = hairstyles.value.find((item) => item.id === targetId.value)
  if (!target) {
    ElMessage.warning('请选择要写入的发型')
    return
  }
  try {
    await updateHairstyle(target.id, { ...target, imgUrl: resultUrl.value })
    ElMessage.success(`已写入「${target.name}」`)
  } catch {
    // 拦截器已提示
  }
}

function copyResult() {
  if (!resultUrl.value) return
  navigator.clipboard
    ?.writeText(resultUrl.value)
    .then(() => ElMessage.success('图片地址已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动复制'))
}

/** 跳到发型库并自动打开「新增发型」，示例照片预填生成图 */
function addToLibrary() {
  if (!resultUrl.value) {
    ElMessage.warning('请先生成标准模特照')
    return
  }
  router.push({ path: '/hairstyles', query: { imgUrl: resultUrl.value } })
}

onMounted(loadHairstyles)
</script>

<template>
  <div class="ai-model">
    <div class="intro">
      <div class="intro-title">AI 标准模特照</div>
      <div class="intro-desc">
        上传或粘贴一张网上找的图片，AI 会保留人物的脸部、五官、发型与表情，只调整为面向正前方、
        头部水平、只露脖子与肩膀以上，并换成干净的浅灰摄影棚背景；输出统一裁成 768×1024（3:4）竖图，
        与发型库卡片一致。生成结果可一键写入发型库。模型可能对发丝细节做轻微重绘，可多点几次挑最接近的一张。
      </div>
    </div>

    <div class="form-row">
      <el-input
        v-model="imageUrl"
        placeholder="粘贴公网图片地址（http/https），或点右侧上传本地图片"
        clearable
      />
      <el-upload
        :show-file-list="false"
        :http-request="handleUpload"
        accept="image/jpeg,image/png,image/webp"
      >
        <el-button :icon="Upload" :loading="uploaded">上传图片</el-button>
      </el-upload>
      <el-button type="primary" :icon="Picture" :loading="loading" :disabled="!canGenerate" @click="generate">
        生成标准模特照
      </el-button>
    </div>

    <div class="preview-grid">
      <div class="preview-card">
        <div class="preview-title">源图</div>
        <el-image v-if="imageUrl" :src="imageUrl" fit="contain" class="preview-img" :preview-src-list="[imageUrl]" />
        <div v-else class="preview-empty">还没有选择图片</div>
      </div>
      <div class="preview-card">
        <div class="preview-title">生成结果</div>
        <el-image
          v-if="resultUrl"
          :src="resultUrl"
          fit="contain"
          class="preview-img"
          :preview-src-list="[resultUrl]"
        />
        <div v-else class="preview-empty">{{ loading ? '生成中，通常需要 20~60 秒…' : '生成后会显示在这里' }}</div>
      </div>
    </div>

    <div v-if="resultUrl" class="result-actions">
      <el-button type="primary" @click="addToLibrary">添加到发型库</el-button>
      <el-select v-model="targetId" filterable placeholder="或选择已有发型直接替换" style="width: 260px">
        <el-option v-for="item in hairstyles" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button @click="writeBack">替换该发型示例图</el-button>
      <el-button @click="copyResult">复制图片地址</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-model {
  max-width: 980px;
}
.intro {
  padding: 16px 18px;
  margin-bottom: 18px;
  background: #fdf6f8;
  border: 1px solid #f3e6eb;
  border-radius: 10px;
}
.intro-title {
  font-size: 15px;
  font-weight: 600;
  color: #2b2628;
}
.intro-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #7d7278;
}
.form-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 20px;
}
.preview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.preview-card {
  padding: 14px;
  background: #fafafa;
  border: 1px solid #f0e9ec;
  border-radius: 10px;
}
.preview-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #3b3337;
}
.preview-img {
  width: 100%;
  height: 420px;
  background: #fff;
  border-radius: 8px;
}
.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 420px;
  font-size: 13px;
  color: #b3a8ad;
  background: #fff;
  border-radius: 8px;
}
.result-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 18px;
}
</style>
