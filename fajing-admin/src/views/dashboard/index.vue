<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Download, InfoFilled, Refresh, Right } from '@element-plus/icons-vue'
import { fetchOverview } from '@/api'
import { formatMoney } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const loadFailed = ref(false)
const mode = ref(7)
const customRange = ref([])
const platform = ref('all')
const metric = ref('netRevenue')
const data = ref(null)

const platforms = [
  { code: 'all', label: '全部渠道' },
  { code: 'mp-weixin', label: '微信小程序' },
  { code: 'h5', label: '公众号 H5' },
  { code: 'mp-toutiao', label: '抖音小程序' },
  { code: 'mp-xhs', label: '小红书小程序' },
  { code: 'unknown', label: '未标记' }
]

const kpiCards = [
  { key: 'netRevenue', label: '净实收', desc: '实付金额扣除退款后', money: true },
  { key: 'paidOrders', label: '已支付订单', desc: '按订单创建日统计' },
  { key: 'pendingOrders', label: '待支付订单', desc: '下单后未完成支付' },
  { key: 'failedPayments', label: '支付失败', desc: '下单或支付异常关闭' },
  { key: 'paidAmount', label: '已支付金额', desc: '已支付订单实付总额', money: true }
]

const platformLabel = computed(
  () => platforms.find((item) => item.code === platform.value)?.label || '全部渠道'
)
const range = computed(() => data.value?.range || {})
const rangeText = computed(() => {
  if (!range.value.start) return ''
  return `${range.value.start} → ${range.value.end} · ${platformLabel.value}`
})
const prevRangeText = computed(() => {
  if (!range.value.prevStart) return ''
  return `前一等长周期 ${range.value.prevStart} → ${range.value.prevEnd}`
})

const trend = computed(() => data.value?.trend || { current: [], previous: [] })
const trendMetricMoney = computed(() => metric.value === 'netRevenue')

const chart = computed(() => {
  const current = trend.value.current || []
  const previous = trend.value.previous || []
  const key = metric.value
  const values = [...current, ...previous].map((row) => Number(row[key]) || 0)
  const max = Math.max(...values, 1)
  const width = 640
  const height = 200
  const padX = 8
  const padTop = 12
  const padBottom = 14
  const count = Math.max(current.length, 1)
  const step = count > 1 ? (width - padX * 2) / (count - 1) : 0
  const x = (index) => padX + step * index
  const y = (value) => height - padBottom - (value / max) * (height - padTop - padBottom)
  const line = (rows) =>
    rows.map((row, index) => `${x(index).toFixed(1)},${y(Number(row[key]) || 0).toFixed(1)}`).join(' ')
  const labelIndexes = count <= 2 ? [0, count - 1] : [0, Math.floor((count - 1) / 2), count - 1]
  const labels = labelIndexes
    .filter((index, i, arr) => index >= 0 && arr.indexOf(index) === i)
    .map((index) => ({ text: current[index]?.date || '', left: `${(x(index) / width) * 100}%` }))
  const points = current.map((row, index) => ({
    x: x(index),
    y: y(Number(row[key]) || 0)
  }))
  return {
    currentLine: line(current),
    previousLine: line(previous),
    points,
    showDots: count <= 7,
    max,
    labels,
    empty: values.every((value) => value === 0)
  }
})

const channels = computed(() => data.value?.channels || [])
const channelTotal = computed(() =>
  channels.value.reduce((sum, item) => sum + Number(item.netRevenue || 0), 0)
)
const donutColors = ['#c95676', '#7257b5', '#a8712e', '#3f8f7a', '#b9aeb3']
const donutStyle = computed(() => {
  const total = channelTotal.value
  if (!total) return 'conic-gradient(#f1eaed 0% 100%)'
  let start = 0
  const parts = channels.value.map((item, index) => {
    const percent = (Number(item.netRevenue || 0) / total) * 100
    const segment = `${donutColors[index % donutColors.length]} ${start}% ${start + percent}%`
    start += percent
    return segment
  })
  return `conic-gradient(${parts.join(', ')})`
})

const todos = computed(() => data.value?.todos || [])
const hotStyles = computed(() => data.value?.hotStyles || [])

function load() {
  const params = { platform: platform.value }
  if (mode.value === 'custom') {
    if (!customRange.value || customRange.value.length !== 2) return
    params.start = customRange.value[0]
    params.end = customRange.value[1]
  } else {
    params.days = Number(mode.value)
  }
  loading.value = true
  loadFailed.value = false
  fetchOverview(params)
    .then((res) => {
      data.value = res || null
    })
    .catch(() => {
      data.value = null
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function onModeChange(value) {
  if (value === 'custom') {
    if (customRange.value && customRange.value.length === 2) load()
    return
  }
  load()
}

function onCustomRangeChange(value) {
  if (mode.value === 'custom' && value && value.length === 2) load()
}

function kpiValue(card) {
  const item = data.value?.kpis?.[card.key]
  if (!item) return card.money ? '¥ 0.00' : '0'
  return card.money ? `¥ ${formatMoney(item.value)}` : String(item.value ?? 0)
}

function deltaText(card) {
  const item = data.value?.kpis?.[card.key]
  if (!item || item.delta === null || item.delta === undefined) return '较前一等长周期 —'
  const sign = item.delta > 0 ? '+' : ''
  return `较前一等长周期 ${sign}${item.delta}%`
}

function channelValue(item) {
  const value = Number(item.netRevenue || 0)
  return value > 0 ? `¥ ${formatMoney(value)}` : '—'
}

function go(link) {
  if (link) router.push(link)
}

function axisLabel() {
  if (chart.value.max === 0) return ''
  return trendMetricMoney.value ? `¥${formatMoney(chart.value.max)}` : String(chart.value.max)
}

function exportCsv() {
  if (!data.value) return
  const rows = []
  rows.push(['数据看板', `${range.value.start} ~ ${range.value.end}`, platformLabel.value])
  rows.push([])
  rows.push(['指标', '数值', '前一周期', '变化'])
  kpiCards.forEach((card) => {
    const item = data.value.kpis?.[card.key] || {}
    rows.push([
      card.label,
      item.value ?? 0,
      item.prev ?? 0,
      item.delta === null || item.delta === undefined ? '—' : `${item.delta}%`
    ])
  })
  rows.push([])
  rows.push(['日期', '净实收', '已支付订单', '上一周期净实收', '上一周期已支付订单'])
  const current = trend.value.current || []
  const previous = trend.value.previous || []
  current.forEach((row, index) => {
    const prev = previous[index] || {}
    rows.push([row.date, row.netRevenue, row.paidOrders, prev.netRevenue ?? '', prev.paidOrders ?? ''])
  })
  rows.push([])
  rows.push(['渠道', '净实收', '已支付订单'])
  channels.value.forEach((item) => rows.push([item.label, item.netRevenue, item.paidOrders]))
  rows.push([])
  rows.push(['热门发型', '解锁数'])
  hotStyles.value.forEach((item) => rows.push([item.name, item.count]))

  const csv = rows
    .map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `数据看板-${range.value.start}-${range.value.end}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="overview">
    <div class="page-head">
      <div class="head-left">
        <div class="eyebrow">OPERATIONS / OVERVIEW</div>
        <div class="page-title">数据看板</div>
        <div class="page-sub">查看成交、交付与用户回流，处理今天的运营待办。</div>
      </div>
      <div class="head-actions">
        <el-popover placement="bottom-end" :width="340" trigger="click">
          <template #reference>
            <el-button text :icon="InfoFilled">口径说明</el-button>
          </template>
          <div class="policy">
            <p>净实收 = 已支付订单实付金额 - 已退款金额。</p>
            <p>订单 KPI 按订单创建日统计；趋势与前一等长周期对照。</p>
            <p>渠道按订单下单来源端归因，历史订单无来源端时计入「未标记」。</p>
            <p>热门发型按所选周期内解锁数排序。</p>
          </div>
        </el-popover>
        <el-button :icon="Download" :disabled="!data" @click="exportCsv">导出报表 CSV</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-left">
        <el-radio-group v-model="mode" @change="onModeChange">
          <el-radio-button :value="1">今日</el-radio-button>
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="mode === 'custom'"
          v-model="customRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="→"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
          @change="onCustomRangeChange"
        />
        <el-select v-model="platform" style="width: 140px" @change="load">
          <el-option v-for="item in platforms" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </div>
      <div class="filter-right">
        <div class="range-main">{{ rangeText }}</div>
        <div class="range-sub">{{ prevRangeText }}</div>
      </div>
    </div>

    <div class="kpi-grid">
      <div v-for="card in kpiCards" :key="card.key" class="kpi-card">
        <div class="kpi-label">{{ card.label }}</div>
        <div class="kpi-value">{{ kpiValue(card) }}</div>
        <div class="kpi-desc">{{ card.desc }}</div>
        <div class="kpi-delta">{{ deltaText(card) }}</div>
      </div>
    </div>

    <div class="chart-row">
      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">经营趋势</div>
            <div class="panel-sub">当前周期与上一等长周期对照</div>
          </div>
          <el-radio-group v-model="metric" size="small">
            <el-radio-button value="netRevenue">净实收</el-radio-button>
            <el-radio-button value="paidOrders">已支付订单</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="chart.empty" class="chart-empty">
          <div class="empty-icon">—</div>
          <div class="empty-title">暂无可查询的经营趋势</div>
          <div class="empty-sub">所选周期内暂无已支付订单数据。</div>
        </div>
        <template v-else>
          <div class="chart-wrap">
            <div class="chart-axis">{{ axisLabel() }}</div>
            <svg class="chart-svg" viewBox="0 0 640 200" preserveAspectRatio="none">
              <line v-for="index in 4" :key="index" x1="8" :y1="12 + (index - 1) * 58" x2="632" :y2="12 + (index - 1) * 58" class="grid-line" />
              <polyline :points="chart.previousLine" class="line-previous" />
              <polyline :points="chart.currentLine" class="line-current" />
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
          </div>
          <div class="chart-legend">
            <span class="legend-dot is-current" />所选周期
            <span class="legend-dot is-previous" />上一周期
          </div>
        </template>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">渠道收入构成</div>
            <div class="panel-sub">各渠道扣除退款后的实收占比</div>
          </div>
          <el-tag size="small" type="info">{{ channels.length }} 渠道</el-tag>
        </div>

        <div class="donut-wrap">
          <div class="donut" :style="{ background: donutStyle }">
            <div class="donut-hole">
              <div class="donut-label">净实收 / 元</div>
              <div class="donut-value">¥ {{ formatMoney(channelTotal) }}</div>
            </div>
          </div>
        </div>

        <div class="channel-list">
          <div v-for="(item, index) in channels" :key="item.code" class="channel-row">
            <span class="channel-dot" :style="{ background: donutColors[index % donutColors.length] }" />
            <span class="channel-name">{{ item.label }}</span>
            <span class="channel-value">{{ channelValue(item) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="bottom-row">
      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">运营待办</div>
            <div class="panel-sub">所有日期的待处理事项</div>
          </div>
          <el-tag size="small" round>工作队列</el-tag>
        </div>

        <div class="todo-list">
          <div v-for="item in todos" :key="item.code" class="todo-row">
            <div class="todo-main">
              <div class="todo-label">{{ item.label }}</div>
              <div class="todo-desc">{{ item.desc }}</div>
            </div>
            <div class="todo-count">{{ item.count }}</div>
            <el-button v-if="item.link" link type="primary" @click="go(item.link)">
              {{ item.linkText }}
              <el-icon><Right /></el-icon>
            </el-button>
            <span v-else class="todo-hold">待处理</span>
          </div>
        </div>
        <div class="panel-foot">待办数据不聚合；生成任务与退款记录可在对应页面核对。</div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">热门发型</div>
            <div class="panel-sub">按所选周期内解锁数排序</div>
          </div>
          <el-button link type="primary" @click="go('/hairstyles')">管理发型 →</el-button>
        </div>

        <div v-if="hotStyles.length" class="hot-list">
          <div v-for="(item, index) in hotStyles" :key="item.styleId" class="hot-row">
            <span class="hot-rank">{{ index + 1 }}</span>
            <span class="hot-name">{{ item.name }}</span>
            <span class="hot-count">{{ item.count }} 次</span>
          </div>
        </div>
        <div v-else class="chart-empty is-small">
          <div class="empty-icon">—</div>
          <div class="empty-title">暂无可排序的发型数据</div>
          <div class="empty-sub">所选周期内暂无解锁记录。</div>
        </div>
      </div>
    </div>

    <div v-if="loadFailed" class="load-failed">数据加载失败，请点击右上角刷新重试</div>
  </div>
</template>

<style scoped lang="scss">
.overview {
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

.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.policy p {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #5c5157;
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

.filter-left {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-right {
  text-align: right;
}

.range-main {
  font-size: 12px;
  color: #3b3337;
}

.range-sub {
  margin-top: 3px;
  font-size: 11px;
  color: #a0949a;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
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

.kpi-delta {
  margin-top: 10px;
  font-size: 11px;
  color: #a0949a;
}

.chart-row {
  display: grid;
  grid-template-columns: 1.7fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}

.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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

.chart-wrap {
  position: relative;
  padding-top: 4px;
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

.line-previous {
  fill: none;
  stroke: #d8ccd2;
  stroke-width: 1.6;
  stroke-dasharray: 5 4;
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
  gap: 14px;
  align-items: center;
  margin-top: 12px;
  font-size: 11px;
  color: #8d8187;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 3px;
  margin-right: 5px;
  vertical-align: middle;
  border-radius: 2px;
}

.legend-dot.is-current {
  background: #c95676;
}

.legend-dot.is-previous {
  background: #d8ccd2;
}

.chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 220px;
  color: #a0949a;

  &.is-small {
    height: 170px;
  }
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
  font-size: 15px;
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

.todo-list {
  display: flex;
  flex-direction: column;
}

.todo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f7f0f2;

  &:last-child {
    border-bottom: none;
  }
}

.todo-main {
  flex: 1;
  min-width: 0;
}

.todo-label {
  font-size: 13px;
  font-weight: 600;
  color: #3b3337;
}

.todo-desc {
  margin-top: 3px;
  font-size: 11px;
  color: #a0949a;
}

.todo-count {
  min-width: 28px;
  font-size: 18px;
  font-weight: 700;
  color: #c95676;
  text-align: right;
}

.todo-hold {
  font-size: 11px;
  color: #b3a8ad;
}

.panel-foot {
  margin-top: 10px;
  font-size: 10px;
  color: #b3a8ad;
}

.hot-list {
  display: flex;
  flex-direction: column;
}

.hot-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f7f0f2;

  &:last-child {
    border-bottom: none;
  }
}

.hot-rank {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  font-weight: 600;
  color: #a0949a;
  background: #faf6f8;
  border-radius: 6px;
}

.hot-name {
  flex: 1;
  font-size: 12px;
  color: #3b3337;
}

.hot-count {
  font-size: 12px;
  color: #8d8187;
}

.load-failed {
  margin-top: 14px;
  font-size: 12px;
  color: #c95676;
  text-align: center;
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .chart-row,
  .bottom-row {
    grid-template-columns: 1fr;
  }
}
</style>
