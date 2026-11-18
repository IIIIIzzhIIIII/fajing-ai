<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCouponTier,
  createPointsPackage,
  disableCouponTier,
  disablePointsPackage,
  fetchCouponTiers,
  fetchPointsPackages,
  fetchSiteSetting,
  saveSiteSetting,
  updateCouponTier,
  updatePointsPackage
} from '@/api'

const activeTab = ref('text')

/* ---------- 文案设置 ---------- */
const loading = ref(false)
const saving = ref(false)
const textForm = reactive({
  heroTitleMain: '',
  heroTitleAccent: '',
  contactWechat: '',
  contactPhone: '',
  aboutContent: '',
  termsContent: '',
  privacyContent: '',
  faceConsentText: ''
})

async function loadText() {
  loading.value = true
  try {
    const data = await fetchSiteSetting()
    if (data) {
      Object.keys(textForm).forEach((key) => {
        textForm[key] = data[key] ?? ''
      })
    }
  } catch (e) {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

async function saveText() {
  saving.value = true
  try {
    await saveSiteSetting({ ...textForm })
    ElMessage.success('前台文案已保存，用户下次进入生效')
  } catch (e) {
    // 请求层已统一提示
  } finally {
    saving.value = false
  }
}

/* ---------- 充值套餐 ---------- */
const packages = ref([])
const pkgLoading = ref(false)
const pkgDialog = ref(false)
const pkgSaving = ref(false)
const pkgForm = reactive({
  id: null,
  title: '',
  description: '',
  badge: '',
  bizType: 'points',
  price: 10,
  points: 100,
  days: 0,
  status: 1,
  sort: 0
})

async function loadPackages() {
  pkgLoading.value = true
  try {
    packages.value = (await fetchPointsPackages()) || []
  } catch (e) {
    // 请求层已统一提示
  } finally {
    pkgLoading.value = false
  }
}

function openPkg(row) {
  if (row) {
    Object.assign(pkgForm, row)
  } else {
    Object.assign(pkgForm, {
      id: null,
      title: '',
      description: '',
      badge: '',
      bizType: 'points',
      price: 10,
      points: 100,
      days: 0,
      status: 1,
      sort: packages.value.length + 1
    })
  }
  pkgDialog.value = true
}

async function submitPkg() {
  if (!pkgForm.title.trim()) {
    ElMessage.warning('请填写套餐名称')
    return
  }
  pkgSaving.value = true
  try {
    const payload = { ...pkgForm }
    if (payload.id) {
      await updatePointsPackage(payload)
    } else {
      await createPointsPackage(payload)
    }
    ElMessage.success('套餐已保存')
    pkgDialog.value = false
    await loadPackages()
  } catch (e) {
    // 请求层已统一提示
  } finally {
    pkgSaving.value = false
  }
}

async function removePkg(row) {
  try {
    await ElMessageBox.confirm(`确定下架「${row.title}」？下架后前台不再展示，历史订单不受影响。`, '下架套餐', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await disablePointsPackage(row.id)
  ElMessage.success('已下架')
  await loadPackages()
}

const pkgTypeLabel = computed(() => (type) => ({ points: '魅力值', vip: 'VIP', vvip: 'VVIP' }[type] || type))

/* ---------- 团购券档位 ---------- */
const tiers = ref([])
const tierLoading = ref(false)
const tierDialog = ref(false)
const tierSaving = ref(false)
const tierForm = reactive({
  id: null,
  title: '',
  payAmount: 50,
  valueAmount: 69,
  description: '',
  status: 1,
  sort: 0
})

async function loadTiers() {
  tierLoading.value = true
  try {
    tiers.value = (await fetchCouponTiers()) || []
  } catch (e) {
    // 请求层已统一提示
  } finally {
    tierLoading.value = false
  }
}

function openTier(row) {
  if (row) {
    Object.assign(tierForm, row)
  } else {
    Object.assign(tierForm, {
      id: null,
      title: '',
      payAmount: 50,
      valueAmount: 69,
      description: '',
      status: 1,
      sort: tiers.value.length + 1
    })
  }
  tierDialog.value = true
}

async function submitTier() {
  if (!tierForm.title.trim()) {
    ElMessage.warning('请填写档位名称')
    return
  }
  tierSaving.value = true
  try {
    const payload = { ...tierForm }
    if (payload.id) {
      await updateCouponTier(payload)
    } else {
      await createCouponTier(payload)
    }
    ElMessage.success('团购券档位已保存')
    tierDialog.value = false
    await loadTiers()
  } catch (e) {
    // 请求层已统一提示
  } finally {
    tierSaving.value = false
  }
}

async function removeTier(row) {
  try {
    await ElMessageBox.confirm(`确定下架「${row.title}」？下架后前台不再展示。`, '下架档位', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await disableCouponTier(row.id)
  ElMessage.success('已下架')
  await loadTiers()
}

onMounted(() => {
  loadText()
  loadPackages()
  loadTiers()
})
</script>

<template>
  <div class="settings-page">
    <div class="page-head">
      <div>
        <div class="page-title">基础设置</div>
        <div class="page-sub">前台文案与价格参数，保存后用户下次进入小程序即生效</div>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 文案设置 -->
      <el-tab-pane label="文案设置" name="text">
        <el-form v-loading="loading" label-width="140px" class="text-form">
          <el-form-item label="首页标题第一行">
            <el-input v-model="textForm.heroTitleMain" maxlength="64" show-word-limit />
          </el-form-item>
          <el-form-item label="首页标题强调行">
            <el-input v-model="textForm.heroTitleAccent" maxlength="64" show-word-limit />
          </el-form-item>
          <el-form-item label="客服微信">
            <el-input v-model="textForm.contactWechat" maxlength="64" placeholder="前端点击客服时展示并复制" />
          </el-form-item>
          <el-form-item label="客服电话">
            <el-input v-model="textForm.contactPhone" maxlength="32" placeholder="可留空" />
          </el-form-item>
          <el-form-item label="关于我们">
            <el-input v-model="textForm.aboutContent" type="textarea" :rows="6" />
          </el-form-item>
          <el-form-item label="用户协议">
            <el-input v-model="textForm.termsContent" type="textarea" :rows="12" />
          </el-form-item>
          <el-form-item label="隐私政策">
            <el-input v-model="textForm.privacyContent" type="textarea" :rows="12" />
          </el-form-item>
          <el-form-item label="人脸单独同意">
            <el-input v-model="textForm.faceConsentText" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveText">保存文案</el-button>
            <span class="field-tip">正文用空行分段，以「## 」开头的行渲染为小标题</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 充值套餐 -->
      <el-tab-pane label="充值套餐" name="packages">
        <div class="tab-toolbar">
          <span class="field-tip">价格与到账权益以下单时的服务端配置为准</span>
          <el-button type="primary" @click="openPkg(null)">新增套餐</el-button>
        </div>
        <el-table v-loading="pkgLoading" :data="packages" border>
          <el-table-column prop="title" label="名称" min-width="140" />
          <el-table-column prop="description" label="副标题" min-width="160" />
          <el-table-column prop="badge" label="角标" width="100" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ pkgTypeLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="价格" width="100">
            <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="points" label="魅力值" width="90" />
          <el-table-column prop="days" label="天数" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPkg(row)">编辑</el-button>
              <el-button v-if="row.status === 1" link type="danger" @click="removePkg(row)">下架</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 团购券档位 -->
      <el-tab-pane label="团购券档位" name="tiers">
        <div class="tab-toolbar">
          <span class="field-tip">用户端购买入口按档位展示，支付金额以服务端配置为准</span>
          <el-button type="primary" @click="openTier(null)">新增档位</el-button>
        </div>
        <el-table v-loading="tierLoading" :data="tiers" border>
          <el-table-column prop="title" label="名称" min-width="140" />
          <el-table-column label="支付金额" width="120">
            <template #default="{ row }">¥{{ Number(row.payAmount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="抵扣金额" width="120">
            <template #default="{ row }">¥{{ Number(row.valueAmount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTier(row)">编辑</el-button>
              <el-button v-if="row.status === 1" link type="danger" @click="removeTier(row)">下架</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 套餐编辑 -->
    <el-dialog v-model="pkgDialog" :title="pkgForm.id ? '编辑充值套餐' : '新增充值套餐'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="pkgForm.title" maxlength="64" placeholder="如 100 魅力值" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="pkgForm.description" maxlength="128" placeholder="如 约 10 款发型解锁" />
        </el-form-item>
        <el-form-item label="角标">
          <el-input v-model="pkgForm.badge" maxlength="32" placeholder="如 推荐 / 多买多省" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="pkgForm.bizType" style="width: 100%">
            <el-option label="魅力值" value="points" />
            <el-option label="VIP 包月" value="vip" />
            <el-option label="VVIP 永久" value="vvip" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格（元）">
          <el-input-number v-model="pkgForm.price" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item v-if="pkgForm.bizType === 'points'" label="到账魅力值">
          <el-input-number v-model="pkgForm.points" :min="1" :step="10" />
        </el-form-item>
        <el-form-item v-if="pkgForm.bizType === 'vip'" label="有效天数">
          <el-input-number v-model="pkgForm.days" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="上架">
          <el-switch v-model="pkgForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="pkgForm.sort" :min="0" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pkgDialog = false">取消</el-button>
        <el-button type="primary" :loading="pkgSaving" @click="submitPkg">保存</el-button>
      </template>
    </el-dialog>

    <!-- 档位编辑 -->
    <el-dialog v-model="tierDialog" :title="tierForm.id ? '编辑团购券档位' : '新增团购券档位'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="tierForm.title" maxlength="64" placeholder="如 50 元抵 69 元" />
        </el-form-item>
        <el-form-item label="支付金额">
          <el-input-number v-model="tierForm.payAmount" :min="0.01" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="抵扣金额">
          <el-input-number v-model="tierForm.valueAmount" :min="0.01" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="tierForm.description" maxlength="255" />
        </el-form-item>
        <el-form-item label="上架">
          <el-switch v-model="tierForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="tierForm.sort" :min="0" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tierDialog = false">取消</el-button>
        <el-button type="primary" :loading="tierSaving" @click="submitTier">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #2b2628;
}
.page-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #9b8f95;
}
.text-form {
  max-width: 860px;
}
.field-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #9b8f95;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
@media (max-width: 960px) {
  .text-form {
    max-width: 100%;
  }
}
</style>
