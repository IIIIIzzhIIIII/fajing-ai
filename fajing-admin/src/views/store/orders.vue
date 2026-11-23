<script setup>
import { onMounted, ref } from 'vue'
import { fetchStoreOrders } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const status = ref('')
const list = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (status.value) params.status = status.value
    const data = await fetchStoreOrders(params)
    list.value = data && Array.isArray(data.list) ? data.list : []
    total.value = data && typeof data.total === 'number' ? data.total : 0
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  page.value = 1
  load()
}

function onPageChange(value) {
  page.value = value
  load()
}

function onSizeChange(value) {
  size.value = value
  page.value = 1
  load()
}

function statusText(row) {
  if (row.orderStatus !== 'paid') return '未支付'
  if (row.refundStatus === 'refunding') return '退款中'
  if (row.refundStatus === 'refunded') return '已退款'
  return '已支付'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">门店订单</div>
        <div class="page-sub">只展示本门店售出的团购券订单，订单归因到员工码</div>
      </div>
      <el-select v-model="status" style="width: 140px" @change="onStatusChange">
        <el-option label="全部" value="" />
        <el-option label="已支付" value="paid" />
        <el-option label="未支付" value="created" />
        <el-option label="已关闭" value="closed" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="outTradeNo" label="订单号" min-width="180" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="customerName" label="顾客" width="120" />
      <el-table-column prop="staffName" label="归因员工" width="110" />
      <el-table-column prop="couponCode" label="券核销码" width="160" />
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
            {{ statusText(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="payChannel" label="渠道" width="90" />
      <el-table-column label="下单时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="支付时间" width="160">
        <template #default="{ row }">{{ row.paidAt ? formatDateTime(row.paidAt) : '—' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />
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
