<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh, Tickets, Wallet } from '@element-plus/icons-vue'
import { fetchOrders } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const loadFailed = ref(false)
const list = ref([])

const totalCount = computed(() => list.value.length)
const totalAmount = computed(() =>
  list.value.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
)

function loadList() {
  loading.value = true
  loadFailed.value = false
  fetchOrders()
    .then((data) => {
      list.value = Array.isArray(data) ? data : []
    })
    .catch(() => {
      list.value = []
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function planLabel(plan) {
  if (plan === 'one') return '单张'
  if (plan === 'all') return '全部'
  return plan || '-'
}

function statusMeta(status) {
  const map = {
    paid: { label: '已支付', type: 'success' },
    pending: { label: '待支付', type: 'warning' },
    refunded: { label: '已退款', type: 'info' },
    closed: { label: '已关闭', type: 'info' }
  }
  return map[status] || { label: status || '-', type: 'info' }
}

function unlockCount(styleIds) {
  if (!styleIds) return 0
  return String(styleIds).split(',').filter((item) => item.trim()).length
}

onMounted(loadList)
</script>

<template>
  <div class="order-page">
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-icon"><el-icon><Tickets /></el-icon></div>
        <div class="stat-body">
          <div class="stat-label">总订单数</div>
          <div class="stat-value">{{ totalCount }}<span class="stat-unit">单</span></div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon is-amount"><el-icon><Wallet /></el-icon></div>
        <div class="stat-body">
          <div class="stat-label">总金额</div>
          <div class="stat-value">¥ {{ formatMoney(totalAmount) }}</div>
        </div>
      </div>
    </div>

    <div class="page-toolbar">
      <span class="list-total">订单明细</span>
      <el-button :icon="Refresh" circle title="刷新" @click="loadList" />
    </div>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column label="订单号" width="110">
        <template #default="{ row }">
          <span class="order-no">#{{ row.id }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户ID" width="90" />
      <el-table-column prop="taskId" label="任务ID" width="100" />
      <el-table-column label="套餐" width="100">
        <template #default="{ row }">
          <el-tag
            class="plan-tag"
            :class="row.plan === 'all' ? 'is-all' : 'is-one'"
            effect="light"
            round
          >
            {{ planLabel(row.plan) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">
          <span class="amount-text">¥ {{ formatMoney(row.amount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type" effect="light" round>
            {{ statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解锁款数" width="100" align="center">
        <template #default="{ row }">{{ unlockCount(row.styleIds) || '-' }}</template>
      </el-table-column>
      <el-table-column label="时间" min-width="160">
        <template #default="{ row }">
          <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty
          :description="loadFailed ? '数据加载失败，请点击刷新重试' : '暂无订单数据'"
          :image-size="90"
        />
      </template>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 22px;
}

.stat-card {
  display: flex;
  flex: 0 0 240px;
  gap: 14px;
  align-items: center;
  padding: 18px 20px;
  background: linear-gradient(135deg, #fdf7f9 0%, #fbf1f4 100%);
  border: 1px solid #f6e6eb;
  border-radius: 12px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  font-size: 20px;
  color: #c95676;
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 10px rgba(201, 86, 118, 0.12);

  &.is-amount {
    color: #a8712e;
  }
}

.stat-label {
  font-size: 12px;
  color: #8b7f86;
}

.stat-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #2b2628;
}

.stat-unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #a0949a;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.list-total {
  font-size: 14px;
  font-weight: 600;
  color: #3b3337;
}

.order-no {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  color: #5c5157;
}

.amount-text {
  font-weight: 600;
  color: #c95676;
}

.plan-tag {
  border: none;

  &.is-one {
    --el-tag-bg-color: #fbeaef;
    --el-tag-text-color: #c95676;
  }

  &.is-all {
    --el-tag-bg-color: #f0ecfa;
    --el-tag-text-color: #7257b5;
  }
}

.time-text {
  font-size: 13px;
  color: #7d7278;
}
</style>
