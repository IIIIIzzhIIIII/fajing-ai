<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { fetchAdminLogs } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

function load() {
  loading.value = true
  fetchAdminLogs(page.value, size.value)
    .then((data) => {
      list.value = data && Array.isArray(data.list) ? data.list : []
      total.value = data && typeof data.total === 'number' ? data.total : 0
    })
    .catch(() => {
      list.value = []
    })
    .finally(() => {
      loading.value = false
    })
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

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-title">发布与日志</div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="adminId" label="管理员" width="100" />
      <el-table-column prop="action" label="操作" width="200" />
      <el-table-column prop="target" label="对象" min-width="200" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #2b2628;
}
</style>
