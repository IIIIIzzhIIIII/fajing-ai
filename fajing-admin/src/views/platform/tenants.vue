<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTenant, fetchPlatformDashboard, fetchTenants, updateStoreCommission, updateStoreKyc,
  updateTenantStatus
} from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const list = ref([])
const dashboard = ref({})
const formRef = ref()
const form = reactive({
  tenantName: '',
  contactName: '',
  contactPhone: '',
  storeName: '',
  storeAddress: '',
  storePhone: '',
  businessHours: '10:00-21:00',
  dailyCapacity: 20,
  commissionRate: 10,
  managerName: '店长',
  managerUsername: '',
  managerPassword: 'store123'
})

const rules = {
  tenantName: [{ required: true, message: '请输入商户名称', trigger: 'blur' }],
  storeName: [{ required: true, message: '请输入门店名称', trigger: 'blur' }],
  managerUsername: [
    { required: true, message: '请输入店长账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,32}$/, message: '4-32 位字母、数字或下划线', trigger: 'blur' }
  ],
  managerPassword: [
    { required: true, message: '请输入店长密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const kpis = computed(() => [
  { label: '租户数', value: dashboard.value.tenantCount || 0 },
  { label: '门店数', value: dashboard.value.storeCount || 0 },
  { label: '已审核门店', value: dashboard.value.approvedStoreCount || 0 },
  { label: '团购券订单', value: dashboard.value.paidOrders || 0 },
  { label: '平台 GMV', value: '¥' + formatMoney(dashboard.value.gmv) },
  { label: '累计佣金', value: '¥' + formatMoney(dashboard.value.commissionTotal) },
  { label: '待结算佣金', value: '¥' + formatMoney(dashboard.value.commissionPending) },
  { label: '今日 AI 成本', value: '¥' + formatMoney(dashboard.value.aiCostToday) }
])

async function load() {
  loading.value = true
  try {
    const [dash, tenants] = await Promise.all([fetchPlatformDashboard(), fetchTenants()])
    dashboard.value = dash || {}
    list.value = tenants || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    tenantName: '', contactName: '', contactPhone: '', storeName: '', storeAddress: '',
    storePhone: '', businessHours: '10:00-21:00', dailyCapacity: 20, commissionRate: 10,
    managerName: '店长', managerUsername: '', managerPassword: 'store123'
  })
  dialog.value = true
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
    const data = await createTenant({ ...form })
    dialog.value = false
    await load()
    await ElMessageBox.alert(
      `店长账号：${data.managerUsername}\n初始密码：${data.managerPassword}\n请把账号密码交给店长，登录后进入店长后台。`,
      '租户开通成功',
      { confirmButtonText: '知道了' }
    )
  } finally {
    saving.value = false
  }
}

async function toggleTenant(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 1 ? `确认启用「${row.name}」？` : `确认停用「${row.name}」？停用后店长和员工都无法登录。`,
      '租户状态',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateTenantStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await load()
}

async function approveStore(store, status) {
  await updateStoreKyc(store.id, status)
  ElMessage.success(status === 'approved' ? 'KYC 已通过' : 'KYC 已驳回')
  await load()
}

async function editCommission(store) {
  let value
  try {
    const result = await ElMessageBox.prompt(
      `设置「${store.name}」的平台佣金比例（0-50%）`,
      '佣金比例',
      { inputValue: String(store.commissionRate ?? 10), inputPattern: /^\d+(\.\d{1,2})?$/ }
    )
    value = result.value
  } catch {
    return
  }
  await updateStoreCommission(store.id, Number(value))
  ElMessage.success('佣金比例已更新')
  await load()
}

function kycText(value) {
  if (value === 'approved') return '已通过'
  if (value === 'rejected') return '已驳回'
  return '审核中'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <div class="page-title">租户与门店</div>
        <div class="page-sub">开通店长账号、审核门店资质、设置平台佣金比例</div>
      </div>
      <el-button type="primary" @click="openCreate">开通租户</el-button>
    </div>

    <div class="kpi-grid">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-value">{{ item.value }}</div>
      </div>
    </div>

    <el-table :data="list" stripe style="margin-top: 18px">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="store-list">
            <div v-for="store in row.stores" :key="store.id" class="store-item">
              <div class="store-main">
                <div class="store-name">{{ store.name }}</div>
                <div class="store-address">{{ store.address || '未填写地址' }}</div>
              </div>
              <el-tag :type="store.kycStatus === 'approved' ? 'success' : store.kycStatus === 'rejected' ? 'danger' : 'warning'" size="small">
                {{ kycText(store.kycStatus) }}
              </el-tag>
              <span class="store-rate">佣金 {{ store.commissionRate }}%</span>
              <el-button v-if="store.kycStatus !== 'approved'" link type="success" @click="approveStore(store, 'approved')">通过</el-button>
              <el-button v-if="store.kycStatus !== 'rejected'" link type="danger" @click="approveStore(store, 'rejected')">驳回</el-button>
              <el-button link type="primary" @click="editCommission(store)">改佣金</el-button>
            </div>
            <div v-if="!row.stores || !row.stores.length" class="empty-store">该租户暂无门店</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商户" min-width="160" />
      <el-table-column label="联系人" width="160">
        <template #default="{ row }">{{ row.contactName || '—' }} {{ row.contactPhone || '' }}</template>
      </el-table-column>
      <el-table-column label="店长账号" width="140">
        <template #default="{ row }">{{ row.manager ? row.manager.username : '—' }}</template>
      </el-table-column>
      <el-table-column prop="storeCount" label="门店数" width="90" />
      <el-table-column prop="orderCount" label="订单数" width="90" />
      <el-table-column label="GMV" width="110">
        <template #default="{ row }">¥{{ formatMoney(row.gmv) }}</template>
      </el-table-column>
      <el-table-column label="平台佣金" width="120">
        <template #default="{ row }">¥{{ formatMoney(row.commission) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleTenant(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="开通租户与店长账号" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-divider content-position="left">商户信息</el-divider>
        <el-form-item label="商户名称" prop="tenantName">
          <el-input v-model="form.tenantName" placeholder="例如：上海XX美发" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-divider content-position="left">门店信息</el-divider>
        <el-form-item label="门店名称" prop="storeName">
          <el-input v-model="form.storeName" />
        </el-form-item>
        <el-form-item label="门店地址">
          <el-input v-model="form.storeAddress" />
        </el-form-item>
        <el-form-item label="门店电话">
          <el-input v-model="form.storePhone" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="form.businessHours" />
        </el-form-item>
        <el-form-item label="每日接待量">
          <el-input-number v-model="form.dailyCapacity" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="佣金比例">
          <el-input-number v-model="form.commissionRate" :min="0" :max="50" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">店长账号</el-divider>
        <el-form-item label="店长姓名">
          <el-input v-model="form.managerName" />
        </el-form-item>
        <el-form-item label="登录账号" prop="managerUsername">
          <el-input v-model="form.managerUsername" placeholder="4-32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="初始密码" prop="managerPassword">
          <el-input v-model="form.managerPassword" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">开通并创建店长</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
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
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.kpi {
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #f0e9ec;
  border-radius: 12px;
}
.kpi-label {
  font-size: 12px;
  color: #8b9295;
}
.kpi-value {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 800;
}
.store-list {
  padding: 8px 24px 12px;
}
.store-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #f0e9ec;
}
.store-main {
  flex: 1;
  min-width: 0;
}
.store-name {
  font-size: 13px;
  font-weight: 600;
}
.store-address {
  margin-top: 2px;
  font-size: 12px;
  color: #8b9295;
}
.store-rate {
  font-size: 12px;
  color: #6f666b;
}
.empty-store {
  padding: 12px 0;
  font-size: 12px;
  color: #8b9295;
}
@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
