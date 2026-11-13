<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Tickets, Wallet } from '@element-plus/icons-vue'
import { fetchOrders, refundOrder } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const loadFailed = ref(false)
const list = ref([])

const refundVisible = ref(false)
const refundSubmitting = ref(false)
const refundRow = ref(null)
const refundForm = reactive({ amount: 0, reason: '' })

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

function channelLabel(channel) {
  if (channel === 'wx') return '微信支付'
  if (channel === 'alipay') return '支付宝'
  if (channel === 'mock') return '模拟支付'
  return channel || '-'
}

function refundMeta(status) {
  if (status === 'refunded') return { label: '已退款', type: 'info' }
  if (status === 'refunding') return { label: '退款中', type: 'warning' }
  return { label: '未退款', type: '' }
}

function openRefund(row) {
  refundRow.value = row
  refundForm.amount = Number(row.amount) || 0
  refundForm.reason = ''
  refundVisible.value = true
}

async function submitRefund() {
  if (!refundRow.value) return
  if (!(refundForm.amount > 0)) {
    ElMessage.warning('请输入正确的退款金额')
    return
  }
  refundSubmitting.value = true
  try {
    await refundOrder(refundRow.value.id, {
      amount: refundForm.amount,
      reason: refundForm.reason
    })
    ElMessage.success('退款已提交')
    refundVisible.value = false
    loadList()
  } catch {
    // 拦截器已提示
  } finally {
    refundSubmitting.value = false
  }
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
      <el-table-column label="支付渠道" width="110">
        <template #default="{ row }">{{ channelLabel(row.payChannel) }}</template>
      </el-table-column>
      <el-table-column label="渠道流水号" min-width="180">
        <template #default="{ row }">
          <span class="time-text">{{ row.transactionId || row.outTradeNo || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="支付时间" width="160">
        <template #default="{ row }">
          <span class="time-text">{{ row.paidAt ? formatDateTime(row.paidAt) : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="退款" width="100">
        <template #default="{ row }">
          <el-tag :type="refundMeta(row.refundStatus).type" effect="light" round>
            {{ refundMeta(row.refundStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            size="small"
            :disabled="row.status !== 'paid' || row.refundStatus === 'refunded'"
            @click="openRefund(row)"
          >
            退款
          </el-button>
        </template>
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

    <el-dialog v-model="refundVisible" title="订单退款" width="420px">
      <el-form label-width="90px">
        <el-form-item label="订单号">#{{ refundRow ? refundRow.id : '' }}</el-form-item>
        <el-form-item label="订单金额">¥ {{ refundRow ? formatMoney(refundRow.amount) : '0.00' }}</el-form-item>
        <el-form-item label="退款金额">
          <el-input-number v-model="refundForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="refundForm.reason" placeholder="选填" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>
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
