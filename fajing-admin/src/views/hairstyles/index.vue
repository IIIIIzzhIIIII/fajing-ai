<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Upload } from '@element-plus/icons-vue'
import service, { createHairstyle, fetchHairstyles, updateHairstyle, updateHairstyleStatus } from '@/api'

const route = useRoute()
const router = useRouter()

const genderOptions = [
  { label: '女款', value: 'female' },
  { label: '男款', value: 'male' }
]

const loading = ref(false)
const loadFailed = ref(false)
const list = ref([])
const gender = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref('')
const formRef = ref()
const form = reactive({
  name: '',
  gender: 'female',
  cat: '',
  len: '',
  face: '',
  en: '',
  imgUrl: '',
  sort: 99,
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入发型名称', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }]
}

const dialogTitle = computed(() => (editingId.value ? '编辑发型' : '新增发型'))
const uploading = ref(false)

/** 上传示例照片：走 /api/upload，返回可直接访问的 URL */
async function uploadImage(options) {
  const file = options?.file
  if (!file) return
  if (!/^image\//.test(file.type)) {
    ElMessage.error('只能上传图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过 10MB')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const data = await service.post('/api/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data && data.url) {
      form.imgUrl = data.url
      ElMessage.success('示例照片已上传')
    }
  } catch {
    // 错误提示由拦截器处理
  } finally {
    uploading.value = false
  }
}

async function loadList() {
  loading.value = true
  loadFailed.value = false
  try {
    const data = await fetchHairstyles(gender.value)
    list.value = Array.isArray(data) ? data : []
  } catch {
    list.value = []
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    gender: 'female',
    cat: '',
    len: '',
    face: '',
    en: '',
    imgUrl: '',
    sort: 99,
    status: 1
  })
}

function openCreate() {
  editingId.value = ''
  resetForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name || '',
    gender: row.gender || 'female',
    cat: row.cat || '',
    len: row.len || '',
    face: row.face || '',
    en: row.en || '',
    imgUrl: row.imgUrl || '',
    sort: row.sort ?? 99,
    status: row.status ?? 1
  })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      await updateHairstyle(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createHairstyle(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch {
    // 错误提示统一由 axios 拦截器处理
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateHairstyleStatus(row.id, nextStatus)
    row.status = nextStatus
    ElMessage.success(nextStatus === 1 ? '已上架' : '已下架')
  } catch {
    // 错误提示统一由 axios 拦截器处理
  }
}

function genderLabel(value) {
  return value === 'male' ? '男款' : '女款'
}

/** 从「AI 模特照」跳转过来：示例照片已生成，直接打开新增弹窗并预填 */
function openCreateWithImage(imageUrl) {
  openCreate()
  form.imgUrl = imageUrl
  // 清掉 query，避免刷新页面又弹一次
  router.replace({ path: '/hairstyles' })
}

onMounted(loadList)

// 支持同页 hash 变化（已在发型库时从 AI 页跳过来不会重新挂载组件）
watch(
  () => route.query.imgUrl,
  (val) => {
    if (val) {
      openCreateWithImage(String(val))
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="hairstyle-page">
    <div class="page-toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增发型</el-button>
      <div class="toolbar-right">
        <el-select
          v-model="gender"
          placeholder="全部性别"
          clearable
          style="width: 140px"
          @change="loadList"
        >
          <el-option v-for="item in genderOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button :icon="Refresh" circle title="刷新" @click="loadList" />
      </div>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column label="示例图" width="84">
        <template #default="{ row }">
          <el-image
            v-if="row.imgUrl"
            :src="row.imgUrl"
            fit="cover"
            class="thumb"
            :preview-src-list="[row.imgUrl]"
            preview-teleported
          />
          <span v-else class="thumb-auto">自动生成</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="130">
        <template #default="{ row }">
          <span class="name-text">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="性别" width="90">
        <template #default="{ row }">
          <el-tag
            class="gender-tag"
            :class="row.gender === 'male' ? 'is-male' : 'is-female'"
            effect="light"
            round
          >
            {{ genderLabel(row.gender) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cat" label="分类" min-width="100">
        <template #default="{ row }">{{ row.cat || '-' }}</template>
      </el-table-column>
      <el-table-column prop="len" label="长度" min-width="100">
        <template #default="{ row }">{{ row.len || '-' }}</template>
      </el-table-column>
      <el-table-column prop="face" label="适合脸型" min-width="150">
        <template #default="{ row }">{{ row.face || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            size="small"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty
          :description="loadFailed ? '数据加载失败，请点击刷新重试' : '暂无发型数据'"
          :image-size="90"
        />
      </template>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <div class="form-grid">
          <el-form-item label="发型名称" prop="name">
            <el-input v-model="form.name" placeholder="如：法式波波头" maxlength="64" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-select v-model="form.gender" placeholder="请选择" style="width: 100%">
              <el-option v-for="item in genderOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类" prop="cat">
            <el-input v-model="form.cat" placeholder="如：短发 / 卷发" maxlength="32" />
          </el-form-item>
          <el-form-item label="长度" prop="len">
            <el-input v-model="form.len" placeholder="如：肩部以上" maxlength="32" />
          </el-form-item>
          <el-form-item label="适合脸型" prop="face">
            <el-input v-model="form.face" placeholder="如：圆脸 / 鹅蛋脸" maxlength="64" />
          </el-form-item>
          <el-form-item label="排序" prop="sort">
            <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="英文提示词" prop="en" class="span-2">
            <el-input
              v-model="form.en"
              type="textarea"
              :rows="2"
              placeholder="用于 AI 生成的英文提示词，如：french bob haircut, chin length"
              maxlength="255"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="示例照片" class="span-2">
            <div class="img-field">
              <div v-if="form.imgUrl" class="img-preview">
                <el-image :src="form.imgUrl" fit="cover" :preview-src-list="[form.imgUrl]" preview-teleported />
                <el-button link type="danger" size="small" @click="form.imgUrl = ''">移除</el-button>
              </div>
              <el-upload
                :show-file-list="false"
                :http-request="uploadImage"
                accept="image/*"
              >
                <el-button :icon="Upload" :loading="uploading">上传照片</el-button>
              </el-upload>
              <el-input v-model="form.imgUrl" placeholder="或直接填写图片 URL" />
            </div>
            <div class="form-tip">
              推荐上传正脸、光线均匀的示例照。设置后：发型库展示与 AI 换发的参考图都会优先使用这张照片；留空则按英文提示词自动生成。
            </div>
          </el-form-item>

          <el-form-item label="状态" prop="status" class="span-2">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">上架</el-radio>
              <el-radio :value="0">下架</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.name-text {
  font-weight: 600;
  color: #2b2628;
}

.gender-tag {
  border: none;

  &.is-female {
    --el-tag-bg-color: #fbeaef;
    --el-tag-text-color: #c95676;
  }

  &.is-male {
    --el-tag-bg-color: #eaf0f7;
    --el-tag-text-color: #4f6d90;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 18px;

  .span-2 {
    grid-column: span 2;
  }
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: block;
}

.thumb-auto {
  font-size: 12px;
  color: #a8a2a4;
}

.img-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.img-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex: none;

  :deep(.el-image) {
    width: 64px;
    height: 64px;
    border-radius: 8px;
    border: 1px solid #eee3e5;
  }
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #a8a2a4;
}
</style>
