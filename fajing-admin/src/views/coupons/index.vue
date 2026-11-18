<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchCoupons, redeemCoupon } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const redeeming = ref(false)
const code = ref('')
const status = ref('unused')
const list = ref([])

async function load() {
  loading.value = true
  try {
    list.value = (await fetchCoupons(status.value)) || []
  } catch (e) {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

async function redeem() {
  const value = code.value.trim()
  if (!value) {
    ElMessage.warning('请输入顾客出示的核销码')
    return
  }
  try {
    await ElMessageBox.confirm(`确认核销 ${value}？核销后不可撤销。`, '门店核销', {
      confirmButtonText: '确认核销',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  redeeming.value = true
  try {
    const data = await redeemCoupon(value)
    ElMessage.success(`核销成功：${data.title}`)
    code.value = ''
    await load()
  } catch (e) {
    // 请求层已提示
  } finally {
    redeeming.value = false
  }
}

function statusText(value) {
  if (value === 'unused') return '可用'
  if (value === 'used') return '已核销'
  if (value === 'refunding') return '退款中'
  if (value === 'refunded') return '已退款'
  return '已过期'
}

function statusType(value) {
  if (value === 'unused') return 'success'
  if (value === 'used') return 'info'
  if (value === 'refunded') return 'danger'
  if (value === 'refunding') return 'warning'
  return 'warning'
}

function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="coupons-page">
    <div class="page-head">
      <div>
        <div class="page-title">团购券核销</div>
        <div class="page-sub">顾客到店出示核销二维码，输入核销码完成核销</div>
      </div>
    </div>

    <div class="redeem-card">
      <el-input
        v-model="code"
        placeholder="输入核销码，例如 C1730…"
        size="large"
        clearable
        style="max-width: 360px"
        @keyup.enter="redeem"
      />
      <el-button type="primary" size="large" :loading="redeeming" @click="redeem">确认核销</el-button>
      <el-select v-model="status" style="width: 140px" @change="load">
        <el-option label="未使用" value="unused" />
        <el-option label="已核销" value="used" />
        <el-option label="已退款" value="refunded" />
        <el-option label="全部" value="" />
      </el-select>
    </div>

    <el-table :data="list" stripe style="width: 100%">
      <el-table-column prop="code" label="核销码" width="180" />
      <el-table-column prop="title" label="券档位" min-width="160" />
      <el-table-column label="实付 / 抵扣" width="150">
        <template #default="{ row }">¥{{ money(row.payAmount) }} / ¥{{ money(row.valueAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="有效期至" width="180">
        <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column label="核销时间" width="180">
        <template #default="{ row }">{{ row.usedAt ? formatDateTime(row.usedAt) : '—' }}</template>
      </el-table-column>
    </el-table>
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
  font-size: 12px;
  color: #8b9295;
  margin-top: 4px;
}
.redeem-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 12px;
}
</style>
