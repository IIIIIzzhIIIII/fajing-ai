<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchStoreProfile, saveStoreProfile } from '@/api'

const loading = ref(false)
const saving = ref(false)
const profile = ref({})
const form = reactive({
  name: '',
  brandName: '',
  address: '',
  phone: '',
  businessHours: '',
  dailyCapacity: 20
})

const rules = {
  name: [{ required: true, message: '请输入门店名称', trigger: 'blur' }]
}

const formRef = ref()

async function load() {
  loading.value = true
  try {
    const data = (await fetchStoreProfile()) || {}
    profile.value = data
    Object.assign(form, {
      name: data.name || '',
      brandName: data.brandName || '',
      address: data.address || '',
      phone: data.phone || '',
      businessHours: data.businessHours || '',
      dailyCapacity: Number(data.dailyCapacity || 20)
    })
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    profile.value = await saveStoreProfile({ ...form })
    ElMessage.success('门店资料已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <div class="page-title">门店设置</div>
        <div class="page-sub">门店资料会展示在顾客端门店页，佣金比例由平台统一设置</div>
      </div>
    </div>

    <div class="settings-grid">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="settings-form">
        <el-form-item label="门店名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="品牌名称">
          <el-input v-model="form.brandName" />
        </el-form-item>
        <el-form-item label="门店地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="form.businessHours" placeholder="例如 10:00-21:00" />
        </el-form-item>
        <el-form-item label="每日接待量">
          <el-input-number v-model="form.dailyCapacity" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">保存门店资料</el-button>
        </el-form-item>
      </el-form>

      <div class="info-card">
        <div class="info-title">平台配置</div>
        <div class="info-row"><span>所属商户</span><strong>{{ profile.tenantName || '—' }}</strong></div>
        <div class="info-row"><span>平台佣金比例</span><strong>{{ profile.commissionRate ?? 0 }}%</strong></div>
        <div class="info-row">
          <span>KYC 审核</span>
          <el-tag :type="profile.kycStatus === 'approved' ? 'success' : profile.kycStatus === 'rejected' ? 'danger' : 'warning'" size="small">
            {{ profile.kycStatus === 'approved' ? '已通过' : profile.kycStatus === 'rejected' ? '已驳回' : '审核中' }}
          </el-tag>
        </div>
        <div class="info-row">
          <span>门店状态</span>
          <el-tag :type="profile.status === 1 ? 'success' : 'info'" size="small">
            {{ profile.status === 1 ? '营业中' : '已停用' }}
          </el-tag>
        </div>
        <div class="info-tip">佣金比例与审核状态由平台总后台维护，店长不可自行修改。</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
}
.page-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8b9295;
}
.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  gap: 20px;
}
.settings-form,
.info-card {
  padding: 18px;
  background: #fff;
  border: 1px solid #f0e9ec;
  border-radius: 12px;
}
.info-title {
  margin-bottom: 14px;
  font-size: 14px;
  font-weight: 600;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
  color: #6f666b;
  border-bottom: 1px dashed #f0e9ec;
}
.info-row strong {
  color: #2b2628;
}
.info-tip {
  margin-top: 14px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.7;
  color: #9a7f88;
  background: #fdf5f7;
  border-radius: 8px;
}
@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
