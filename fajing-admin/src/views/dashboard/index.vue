<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { fetchTodayStats } from '@/api'

const loading = ref(false)
const stats = ref({})

function load() {
  loading.value = true
  fetchTodayStats()
    .then((data) => {
      stats.value = data || {}
    })
    .catch(() => {
      stats.value = {}
    })
    .finally(() => {
      loading.value = false
    })
}

onMounted(load)
</script>

<template>
  <div class="dashboard">
    <div class="dashboard-head">
      <div class="dashboard-title">今日数据</div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">今日生成量</div>
        <div class="stat-value">{{ stats.generateCount || 0 }}</div>
        <div class="stat-sub">次生成任务</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日成本</div>
        <div class="stat-value">¥{{ stats.costYuan || '0.00' }}</div>
        <div class="stat-sub">{{ stats.aiCalls || 0 }} 次 AI 调用</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.dashboard-title {
  font-size: 15px;
  font-weight: 600;
  color: #2b2628;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.stat-card {
  padding: 20px 22px;
  background: linear-gradient(135deg, #fdf6f8 0%, #ffffff 100%);
  border: 1px solid #f3e6eb;
  border-radius: 12px;
}
.stat-label {
  font-size: 13px;
  color: #8d8187;
}
.stat-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: #c95676;
}
.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #b3a8ad;
}
</style>
