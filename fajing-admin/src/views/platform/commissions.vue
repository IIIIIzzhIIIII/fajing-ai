<script setup>
import { onMounted, ref } from 'vue'
import { fetchCommissions, fetchPlatformOrders } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const tab = ref('commissions')
const status = ref('')
const commissions = ref([])
const orders = ref([])

async function load() {
  loading.value = true
  try {
    const [c, o] = await Promise.all([fetchCommissions(status.value), fetchPlatformOrders()])
    commissions.value = c || []
    orders.value = o || []
  } finally {
    loading.value = false
  }
}

function statusText(value) {
  if (value === 'available') return '待结算'
  if (value === 'settled') return '已结算'
  if (value === 'reversed') return '已冲正'
  return value
}

function statusType(value) {
  if (value === 'available') return 'warning'
  if (value === 'settled') return 'success'
  if (value === 'reversed') return 'danger'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <div class="page-title">佣金台账</div>
        <div class="page-sub">支付记待结算，门店核销后转已结算，退款自动冲正</div>
      </div>
      <el-select v-model="status" style="width: 140px" @change="load">
        <el-option label="全部状态" value="" />
        <el-option label="待结算" value="available" />
        <el-option label="已结算" value="settled" />
        <el-option label="已冲正" value="reversed" />
      </el-select>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="佣金台账" name="commissions">
        <el-table :data="commissions" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="tenantName" label="商户" min-width="140" />
          <el-table-column prop="storeName" label="门店" min-width="140" />
          <el-table-column prop="staffName" label="归因员工" width="100" />
          <el-table-column prop="outTradeNo" label="订单号" min-width="170" />
          <el-table-column label="订单金额" width="110">
            <template #default="{ row }">¥{{ formatMoney(row.orderAmount) }}</template>
          </el-table-column>
          <el-table-column label="佣金比例" width="100">
            <template #default="{ row }">{{ row.commissionRate }}%</template>
          </el-table-column>
          <el-table-column label="佣金" width="100">
            <template #default="{ row }">¥{{ formatMoney(row.commissionAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="结算/冲正" width="160">
            <template #default="{ row }">
              {{ row.settledAt ? formatDateTime(row.settledAt) : row.reversedAt ? formatDateTime(row.reversedAt) : '—' }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="平台订单" name="orders">
        <el-table :data="orders" stripe>
          <el-table-column prop="outTradeNo" label="订单号" min-width="180" />
          <el-table-column prop="tenantName" label="商户" min-width="140" />
          <el-table-column prop="storeName" label="门店" min-width="140" />
          <el-table-column prop="staffName" label="归因员工" width="100" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="couponCode" label="核销码" width="160" />
          <el-table-column label="券状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.couponStatus" :type="row.couponStatus === 'unused' ? 'success' : 'info'" size="small">
                {{ row.couponStatus === 'unused' ? '待核销' : row.couponStatus === 'used' ? '已核销' : row.couponStatus }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="订单状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.orderStatus === 'paid' ? 'success' : 'info'" size="small">
                {{ row.orderStatus === 'paid' ? '已支付' : row.orderStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
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
</style>
