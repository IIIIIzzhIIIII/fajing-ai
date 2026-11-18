<script setup>
import { onMounted, ref } from 'vue'
import { Refresh, TrendCharts } from '@element-plus/icons-vue'
import { fetchChannelsStats } from '@/api'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const loadFailed = ref(false)
const days = ref(7)
const data = ref({ totals: {}, channels: [], payChannels: [] })

const ranges = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 }
]

const cards = [
  { key: 'visitUsers', label: '访问用户', unit: '人' },
  { key: 'viewUsers', label: '成图预览', unit: '人' },
  { key: 'generateSuccess', label: '生成成功', unit: '次' },
  { key: 'saveImages', label: '保存成图', unit: '次' },
  { key: 'shareEvents', label: '分享行为', unit: '次' },
  { key: 'paidOrders', label: '付费订单', unit: '单' },
  { key: 'newUsers', label: '新客', unit: '人' }
]

function load() {
  loading.value = true
  loadFailed.value = false
  fetchChannelsStats(days.value)
    .then((res) => {
      data.value = {
        totals: res?.totals || {},
        channels: Array.isArray(res?.channels) ? res.channels : [],
        payChannels: Array.isArray(res?.payChannels) ? res.payChannels : []
      }
    })
    .catch(() => {
      data.value = { totals: {}, channels: [], payChannels: [] }
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

onMounted(load)
</script>

<template>
  <div class="channel-page">
    <div class="page-head">
      <div class="head-left">
        <div class="page-title">渠道与传播</div>
        <div class="page-sub">查看小程序 / H5 各渠道的访问、成图与付费使用情况</div>
      </div>
      <div class="head-right">
        <el-radio-group v-model="days" size="default" @change="load">
          <el-radio-button v-for="item in ranges" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="card in cards" :key="card.key" class="stat-card">
        <div class="stat-label">{{ card.label }}</div>
        <div class="stat-value">
          {{ data.totals[card.key] || 0 }}<span class="stat-unit">{{ card.unit }}</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">生图成本</div>
        <div class="stat-value">¥ {{ formatMoney(data.totals.aiCost || 0) }}</div>
      </div>
      <div class="stat-card is-revenue">
        <div class="stat-label">净实收</div>
        <div class="stat-value">¥ {{ formatMoney(data.totals.netRevenue || 0) }}</div>
      </div>
    </div>

    <div class="section">
      <div class="section-head">
        <el-icon class="section-icon"><TrendCharts /></el-icon>
        <span class="section-title">渠道表现</span>
        <span class="section-tip">付费订单按下单来源端归因；净实收 = 实付金额 - 退款 - 大模型生图成本</span>
      </div>
      <el-table v-loading="loading" :data="data.channels" stripe>
        <el-table-column label="渠道" min-width="130">
          <template #default="{ row }">
            <span class="channel-name">{{ row.label }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="visitUsers" label="访问用户" width="100" align="right" />
        <el-table-column prop="viewUsers" label="成图预览" width="100" align="right" />
        <el-table-column prop="generateSuccess" label="生成成功" width="100" align="right" />
        <el-table-column prop="saveImages" label="保存成图" width="100" align="right" />
        <el-table-column prop="shareEvents" label="分享行为" width="100" align="right" />
        <el-table-column prop="paidOrders" label="付费订单" width="100" align="right" />
        <el-table-column label="付费转化" width="100" align="right">
          <template #default="{ row }">{{ row.payRate || 0 }}%</template>
        </el-table-column>
        <el-table-column label="生图成本" width="120" align="right">
          <template #default="{ row }">
            <span class="amount-text is-cost">¥ {{ formatMoney(row.aiCost || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="净实收" width="120" align="right">
          <template #default="{ row }">
            <span class="amount-text">¥ {{ formatMoney(row.netRevenue || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="newUsers" label="新客" width="90" align="right" />
        <template #empty>
          <el-empty
            :description="loadFailed ? '数据加载失败，请点击刷新重试' : '暂无渠道数据'"
            :image-size="90"
          />
        </template>
      </el-table>
    </div>

    <div class="section">
      <div class="section-head">
        <span class="section-title">支付渠道构成</span>
        <span class="section-tip">按订单实际支付渠道统计</span>
      </div>
      <el-table :data="data.payChannels" stripe>
        <el-table-column prop="label" label="支付渠道" min-width="140" />
        <el-table-column prop="orders" label="订单数" width="120" align="right" />
        <template #empty>
          <el-empty description="暂无支付数据" :image-size="90" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #2b2628;
}

.page-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #a0949a;
}

.head-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 26px;
}

.stat-card {
  padding: 16px 18px;
  background: linear-gradient(135deg, #fdf6f8 0%, #ffffff 100%);
  border: 1px solid #f3e6eb;
  border-radius: 12px;

  &.is-revenue .stat-value {
    color: #a8712e;
  }
}

.stat-label {
  font-size: 12px;
  color: #8d8187;
}

.stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: #c95676;
}

.stat-unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #b3a8ad;
}

.section {
  margin-bottom: 26px;
}

.section-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.section-icon {
  font-size: 16px;
  color: #c95676;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #3b3337;
}

.section-tip {
  font-size: 12px;
  color: #b3a8ad;
}

.channel-name {
  font-weight: 600;
  color: #3b3337;
}

.amount-text {
  font-weight: 600;
  color: #c95676;

  &.is-cost {
    color: #a8712e;
  }
}
</style>
