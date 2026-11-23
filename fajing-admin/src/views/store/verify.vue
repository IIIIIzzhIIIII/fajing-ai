<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Html5Qrcode } from 'html5-qrcode'
import { fetchStoreCouponInstances, fetchStoreStaff, verifyStoreCoupon } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const verifying = ref(false)
const code = ref('')
const status = ref('unused')
const list = ref([])
const staffList = ref([])
const staffId = ref(null)
const scanVisible = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
let scanner = null

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (status.value) params.status = status.value
    const data = await fetchStoreCouponInstances(params)
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

async function loadStaff() {
  staffList.value = (await fetchStoreStaff()) || []
}

async function verify() {
  const value = code.value.trim()
  if (!value) {
    ElMessage.warning('请输入或扫描顾客出示的核销码')
    return
  }
  if (!staffId.value) {
    ElMessage.warning('请先选择归因员工')
    return
  }
  const staff = staffList.value.find((item) => item.id === staffId.value)
  try {
    await ElMessageBox.confirm(
      `确认核销 ${value}？业绩归因给「${staff ? staff.name : '所选员工'}」，核销后不可撤销。`,
      '门店核销',
      { type: 'warning', confirmButtonText: '确认核销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  verifying.value = true
  try {
    const data = await verifyStoreCoupon(value, staffId.value)
    ElMessage.success(
      `核销成功：${data.title}，归因员工 ${data.staffName || staff?.name || '—'}，平台佣金 ¥${formatMoney(data.commissionAmount)}`
    )
    code.value = ''
    await load()
  } finally {
    verifying.value = false
  }
}

function openScan() {
  if (!staffId.value) {
    ElMessage.warning('请先选择归因员工，再扫码核销')
    return
  }
  scanVisible.value = true
}

async function startScan() {
  await nextTick()
  try {
    scanner = new Html5Qrcode('fajing-verify-reader')
    await scanner.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 240, height: 240 } },
      (text) => {
        code.value = String(text || '').trim()
        closeScan()
        ElMessage.success('已识别核销码')
      },
      () => {}
    )
  } catch (e) {
    ElMessage.error('无法打开摄像头，请检查浏览器权限，或手动输入核销码')
  }
}

async function closeScan() {
  scanVisible.value = false
  const instance = scanner
  scanner = null
  if (instance) {
    try {
      await instance.stop()
      await instance.clear()
    } catch {
      // 摄像头未启动时忽略
    }
  }
}

function statusText(value) {
  if (value === 'unused') return '待核销'
  if (value === 'used') return '已核销'
  if (value === 'refunding') return '退款中'
  if (value === 'refunded') return '已退款'
  return '已过期'
}

onMounted(() => {
  load()
  loadStaff()
})

onBeforeUnmount(() => {
  closeScan()
})
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">核销台</div>
        <div class="page-sub">选择归因员工后核销本门店售出的券，核销后对应佣金台账自动结算</div>
      </div>
    </div>

    <div class="verify-card">
      <el-select
        v-model="staffId"
        placeholder="选择归因员工"
        size="large"
        style="width: 180px"
      >
        <el-option
          v-for="item in staffList"
          :key="item.id"
          :label="`${item.name}（${item.phone}）`"
          :value="item.id"
        />
      </el-select>
      <el-input
        v-model="code"
        placeholder="输入顾客出示的核销码，例如 C1730…"
        size="large"
        clearable
        style="max-width: 340px"
        @keyup.enter="verify"
      />
      <el-button type="primary" size="large" :loading="verifying" @click="verify">确认核销</el-button>
      <el-button size="large" :disabled="!staffId" @click="openScan">扫码验券</el-button>
      <el-select v-model="status" style="width: 140px" @change="onStatusChange">
        <el-option label="待核销" value="unused" />
        <el-option label="已核销" value="used" />
        <el-option label="已退款" value="refunded" />
        <el-option label="全部" value="" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="code" label="核销码" width="160" />
      <el-table-column prop="title" label="券" min-width="150" />
      <el-table-column prop="customerName" label="顾客" width="120" />
      <el-table-column prop="staffName" label="归因员工" width="110" />
      <el-table-column label="实付 / 抵扣" width="140">
        <template #default="{ row }">¥{{ formatMoney(row.payAmount) }} / ¥{{ formatMoney(row.valueAmount) }}</template>
      </el-table-column>
      <el-table-column label="平台佣金" width="110">
        <template #default="{ row }">¥{{ formatMoney(row.commissionAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'unused' ? 'success' : 'info'" size="small">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="有效期至" width="160">
        <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column label="核销时间" width="160">
        <template #default="{ row }">{{ row.usedAt ? formatDateTime(row.usedAt) : '—' }}</template>
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

    <el-dialog
      v-model="scanVisible"
      title="扫码验券"
      width="420px"
      :close-on-click-modal="false"
      @opened="startScan"
      @closed="closeScan"
    >
      <div id="fajing-verify-reader" class="scan-reader" />
      <div class="scan-tip">将顾客券包中的核销码对准摄像头，识别后自动填入核销码。</div>
      <template #footer>
        <el-button @click="closeScan">关闭</el-button>
      </template>
    </el-dialog>
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
.verify-card {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #f0e9ec;
  border-radius: 12px;
}
.scan-reader {
  width: 100%;
  min-height: 260px;
  overflow: hidden;
  background: #111;
  border-radius: 12px;
}
.scan-reader :deep(video) {
  width: 100% !important;
  border-radius: 12px;
}
.scan-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #8b9295;
  text-align: center;
}
</style>
