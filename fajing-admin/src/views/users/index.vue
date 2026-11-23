<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, User } from '@element-plus/icons-vue'
import { fetchUsers, updateUserStatus } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const loadFailed = ref(false)
const list = ref([])
const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

const totalCount = computed(() => total.value)

function loadList() {
  loading.value = true
  loadFailed.value = false
  const params = { page: page.value, size: size.value }
  const kw = keyword.value.trim()
  if (kw) params.keyword = kw
  if (status.value !== '') params.status = status.value
  fetchUsers(params)
    .then((data) => {
      list.value = data && Array.isArray(data.list) ? data.list : []
      total.value = data && typeof data.total === 'number' ? data.total : 0
    })
    .catch(() => {
      list.value = []
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function resetQuery() {
  keyword.value = ''
  status.value = ''
  page.value = 1
  loadList()
}

function onPageChange(value) {
  page.value = value
  loadList()
}

function onSizeChange(value) {
  size.value = value
  page.value = 1
  loadList()
}

async function toggleStatus(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateUserStatus(row.id, nextStatus)
    row.status = nextStatus
    ElMessage.success(nextStatus === 1 ? '已启用该用户' : '已停用该用户')
  } catch {
    // 错误提示由拦截器统一处理
  }
}

function genderMeta(gender) {
  if (gender === 'female') return { label: '女', type: 'danger' }
  if (gender === 'male') return { label: '男', type: 'primary' }
  return { label: '未知', type: 'info' }
}

function initial(name) {
  return (name || '用').slice(0, 1)
}

onMounted(loadList)
</script>

<template>
  <div class="user-page">
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-icon"><el-icon><User /></el-icon></div>
        <div class="stat-body">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ totalCount }}<span class="stat-unit">人</span></div>
        </div>
      </div>
    </div>

    <div class="page-toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="keyword"
          placeholder="搜索昵称 / 手机号"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
        />
        <el-select v-model="status" placeholder="全部状态" style="width: 120px">
          <el-option label="全部状态" value="" />
          <el-option label="正常" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <el-button :icon="Refresh" circle title="刷新" @click="loadList" />
    </div>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="34" :src="row.avatar || undefined" class="user-avatar">
              {{ initial(row.nickname) }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">{{ row.nickname || '未命名用户' }}</div>
              <div class="user-phone">{{ row.phone || '未绑定手机号' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="性别" width="80">
        <template #default="{ row }">
          <el-tag :type="genderMeta(row.gender).type" effect="light" round>
            {{ genderMeta(row.gender).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="taskCount" label="生成次数" width="100" align="center" />
      <el-table-column prop="recordCount" label="试发记录" width="100" align="center" />
      <el-table-column prop="unlockCount" label="已解锁" width="90" align="center" />
      <el-table-column label="付费金额" width="120">
        <template #default="{ row }">
          <span class="amount-text">¥ {{ formatMoney(row.paidAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" min-width="160">
        <template #default="{ row }">
          <span class="time-text">{{ formatDateTime(row.registerAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近活跃" min-width="160">
        <template #default="{ row }">
          <span class="time-text">{{ formatDateTime(row.lastActiveAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
            {{ row.status === 1 ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            size="small"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty
          :description="loadFailed ? '数据加载失败，请点击刷新重试' : '暂无用户数据'"
          :image-size="90"
        />
      </template>
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

.toolbar-left {
  display: flex;
  gap: 10px;
  align-items: center;
}

.user-cell {
  display: flex;
  gap: 10px;
  align-items: center;
}

.user-avatar {
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: #c95676;
  background-color: #fbeaef;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #2b2628;
}

.user-phone {
  margin-top: 2px;
  font-size: 12px;
  color: #a0949a;
}

.amount-text {
  font-weight: 600;
  color: #c95676;
}

.time-text {
  font-size: 13px;
  color: #7d7278;
}
</style>
