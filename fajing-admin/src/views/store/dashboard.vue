<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { fetchStoreDashboard } from '@/api'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const mode = ref(7)
const metric = ref('revenue')
const data = ref({})

const kpis = computed(() => [
  { key: 'periodRevenue', label: '周期营业额', money: true, desc: '所选周期已支付门店券实付' },
  { key: 'periodOrders', label: '周期订单', desc: '所选周期已支付券订单数' },
  { key: 'periodVerified', label: '周期核销', desc: '所选周期完成核销的券' },
  { key: 'periodAiCalls', label: '员工试发', desc: '所选周期员工代客 AI 试发' },
  { key: 'commissionTotal', label: '平台佣金合计', money: true, desc: '未退款订单佣金合计' },
  { key: 'commissionPending', label: '待核销佣金', money: true, desc: '已支付未核销部分' },
  { key: 'staffCount', label: '在职员工', desc: '当前启用的员工账号' },
  { key: 'couponUnused', label: '待使用券', desc: '顾客券包中待核销' }
])

const metrics = [
  { key: 'revenue', label: '营业额' },
  { key: 'orders', label: '订单数' },
  { key: 'verified', label: '核销量' },
  { key: 'aiCalls', label: '试发数' }
]

const trend = computed(() => data.value.trend || [])
const metricMoney = computed(() => metric.value === 'revenue')

const chart = computed(() => {
  const rows = trend.value
  const values = rows.map((row) => Number(row[metric.value]) || 0)
  const max = Math.max(...values, 1)
  const width = 640
  const height = 200
  const padX = 8
  const padTop = 12
  const padBottom = 14
  const count = Math.max(rows.length, 1)
  const step = count > 1 ? (width - padX * 2) / (count - 1) : 0
  const x = (index) => padX + step * index
  const y = (value) => height - padBottom - (value / max) * (height - padTop - padBottom)
  const line = rows.map((row, index) => `${x(index).toFixed(1)},${y(Number(row[metric.value]) || 0).toFixed(1)}`).join(' ')
  const points = rows.map((row, index) => ({
    x: x(index),
    y: y(Number(row[metric.value]) || 0),
    date: row.date
  }))
  const labelIndexes = count <= 2 ? [0, count - 1] : [0, Math.floor((count - 1) / 2), count - 1]
  const labels = labelIndexes
    .filter((index, i, arr) => index >= 0 && arr.indexOf(index) === i)
    .map((index) => ({ text: rows[index]?.date || '', left: `${(x(index) / width) * 100}%` }))
  return {
    line,
    points,
    labels,
    showDots: count <= 10,
    max,
    empty: values.every((value) => value === 0)
  }
})

const staffRanking = computed(() => data.value.staffRanking || [])
const couponStatus = computed(() => data.value.couponStatus || [])
const couponTotal = computed(() =>
  couponStatus.value.reduce((sum, item) => sum + Number(item.count || 0), 0)
)
const donutColors = { unused: '#c95676', used: '#3f8f7a', refunded: '#b9aeb3', expired: '#c9bcc2' }
const statusLabels = { unused: '待核销', used: '已核销', refunded: '已退款', expired: '已过期' }
const donutStyle = computed(() => {
  const total = couponTotal.value
  if (!total) return 'conic-gradient(#f1eaed 0% 100%)'
  let start = 0
  const parts = couponStatus.value.map((item) => {
    const percent = (Number(item.count || 0) / total) * 100
    const color = donutColors[item.status] || '#c9bcc2'
    const segment = `${color} ${start}% ${start + percent}%`
    start += percent
    return segment
  })
  return `conic-gradient(${parts.join(', ')})`
})

const rangeText = computed(() => {
  const start = data.value.rangeStart || ''
  const end = data.value.rangeEnd || ''
  return start ? `${start} → ${end}` : ''
})

async function load() {
  loading.value = true
  try {
    data.value = (await fetchStoreDashboard(Number(mode.value))) || {}
  } finally {
    loading.value = false
  }
}

function axisLabel() {
  if (!chart.value.max) return ''
  return metricMoney.value ? `¥${formatMoney(chart.value.max)}` : String(chart.value.max)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="store-dashboard">
    <div class="page-head">
      <div>
        <div class="eyebrow">STORE / OVERVIEW</div>
        <div class="page-title">门店工作台</div>
        <div class="page-sub">经营、核销、佣金与员工业绩一屏掌握。</div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="mode" @change="load">
        <el-radio-button :value="1">今日</el-radio-button>
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
      <div class="range-main">{{ rangeText }}</div>
    </div>

    <div class="kpi-grid">
      <div v-for="item in kpis" :key="item.key" class="kpi-card">
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-value">
          {{ item.money ? '¥ ' + formatMoney(data[item.key]) : (data[item.key] ?? 0) }}
        </div>
        <div class="kpi-desc">{{ item.desc }}</div>
      </div>
    </div>

    <div class="chart-row">
      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">经营趋势</div>
            <div class="panel-sub">按所选周期逐日统计</div>
          </div>
          <el-radio-group v-model="metric" size="small">
            <el-radio-button v-for="item in metrics" :key="item.key" :value="item.key">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="chart.empty" class="chart-empty">
          <div class="empty-icon">—</div>
          <div class="empty-title">所选周期暂无数据</div>
          <div class="empty-sub">有新订单或核销后这里会显示趋势。</div>
        </div>
        <template v-else>
          <div class="chart-axis">{{ axisLabel() }}</div>
          <svg class="chart-svg" viewBox="0 0 640 200" preserveAspectRatio="none">
            <line v-for="index in 4" :key="index" x1="8" :y1="12 + (index - 1) * 58" x2="632" :y2="12 + (index - 1) * 58" class="grid-line" />
            <polyline :points="chart.line" class="line-current" />
            <circle
              v-for="(point, index) in chart.points"
              v-show="chart.showDots"
              :key="index"
              :cx="point.x"
              :cy="point.y"
              r="2.6"
              class="point-current"
            />
          </svg>
          <div class="chart-labels">
            <span v-for="label in chart.labels" :key="label.text + label.left" :style="{ left: label.left }">
              {{ label.text }}
            </span>
          </div>
          <div class="chart-legend">
            <span class="legend-dot" />{{ metrics.find((item) => item.key === metric)?.label }}
          </div>
        </template>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">券状态分布</div>
            <div class="panel-sub">本门店全部团购券</div>
          </div>
          <el-tag size="small" type="info">{{ couponTotal }} 张</el-tag>
        </div>

        <div class="donut-wrap">
          <div class="donut" :style="{ background: donutStyle }">
            <div class="donut-hole">
              <div class="donut-label">团购券 / 张</div>
              <div class="donut-value">{{ couponTotal }}</div>
            </div>
          </div>
        </div>

        <div class="channel-list">
          <div v-for="item in couponStatus" :key="item.status" class="channel-row">
            <span class="channel-dot" :style="{ background: donutColors[item.status] || '#c9bcc2' }" />
            <span class="channel-name">{{ statusLabels[item.status] || item.status }}</span>
            <span class="channel-value">{{ item.count }} 张</span>
          </div>
          <div v-if="!couponStatus.length" class="channel-empty">暂无券数据</div>
        </div>
      </div>
    </div>

    <div class="panel staff-panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">员工业绩</div>
          <div class="panel-sub">按所选周期归因订单金额排序</div>
        </div>
        <el-tag size="small" round>{{ staffRanking.length }} 人</el-tag>
      </div>

      <el-table :data="staffRanking" size="small" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="name" label="员工" min-width="110" />
        <el-table-column prop="orderCount" label="归因订单" width="100" />
        <el-table-column label="归因金额" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="verifiedCount" label="核销券" width="100" />
        <el-table-column label="归因佣金" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.commissionAmount) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="!staffRanking.length" class="table-empty">暂无员工数据</div>
    </div>
  </div>
</template>

<style scoped>
.store-dashboard {
  min-height: 320px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}
.eyebrow {
  font-size: 10px;
  color: #b3a8ad;
  letter-spacing: 2px;
}
.page-title {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #2b2628;
}
.page-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #a0949a;
}
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #faf8f9;
  border: 1px solid #f1eaed;
  border-radius: 12px;
}
.range-main {
  font-size: 12px;
  color: #3b3337;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.kpi-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #f1eaed;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(43, 38, 40, 0.03);
}
.kpi-label {
  font-size: 12px;
  color: #8d8187;
}
.kpi-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 700;
  color: #2b2628;
  word-break: break-all;
}
.kpi-desc {
  margin-top: 4px;
  font-size: 11px;
  color: #b3a8ad;
}
.chart-row {
  display: grid;
  grid-template-columns: 1.7fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}
.panel {
  padding: 18px;
  background: #fff;
  border: 1px solid #f1eaed;
  border-radius: 14px;
  box-shadow: 0 4px 14px rgba(43, 38, 40, 0.03);
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #2b2628;
}
.panel-sub {
  margin-top: 4px;
  font-size: 11px;
  color: #a0949a;
}
.chart-axis {
  font-size: 10px;
  color: #b3a8ad;
}
.chart-svg {
  width: 100%;
  height: 180px;
  margin-top: 4px;
}
.grid-line {
  stroke: #f2ecee;
  stroke-width: 1;
}
.line-current {
  fill: none;
  stroke: #c95676;
  stroke-width: 2.2;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.point-current {
  fill: #fff;
  stroke: #c95676;
  stroke-width: 1.6;
}
.chart-labels {
  position: relative;
  height: 16px;
  margin-top: 2px;
}
.chart-labels span {
  position: absolute;
  font-size: 10px;
  color: #b3a8ad;
  transform: translateX(-50%);
  white-space: nowrap;
}
.chart-legend {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 12px;
  font-size: 11px;
  color: #8d8187;
}
.legend-dot {
  display: inline-block;
  width: 10px;
  height: 3px;
  border-radius: 2px;
  background: #c95676;
}
.chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 220px;
  color: #a0949a;
}
.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-bottom: 10px;
  font-size: 16px;
  color: #c9bcc2;
  background: #faf6f8;
  border-radius: 50%;
}
.empty-title {
  font-size: 12px;
  font-weight: 600;
  color: #7d7278;
}
.empty-sub {
  margin-top: 5px;
  font-size: 11px;
}
.donut-wrap {
  display: flex;
  justify-content: center;
  padding: 6px 0 16px;
}
.donut {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 150px;
  height: 150px;
  border-radius: 50%;
}
.donut-hole {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 104px;
  height: 104px;
  background: #fff;
  border-radius: 50%;
}
.donut-label {
  font-size: 10px;
  color: #a0949a;
}
.donut-value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: #2b2628;
}
.channel-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.channel-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #5c5157;
}
.channel-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.channel-name {
  flex: 1;
}
.channel-value {
  color: #2b2628;
  font-variant-numeric: tabular-nums;
}
.channel-empty {
  font-size: 11px;
  color: #b3a8ad;
}
.staff-panel {
  margin-bottom: 8px;
}
.table-empty {
  padding: 18px 0;
  font-size: 12px;
  color: #b3a8ad;
  text-align: center;
}
@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
