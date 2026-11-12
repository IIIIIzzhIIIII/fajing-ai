<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { deletePost, fetchReports, handleReport } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])

function load() {
  loading.value = true
  fetchReports()
    .then((data) => {
      list.value = Array.isArray(data) ? data : []
    })
    .catch(() => {
      list.value = []
    })
    .finally(() => {
      loading.value = false
    })
}

async function mark(row, status) {
  try {
    await handleReport(row.id, status)
    row.status = status
    ElMessage.success(status === 'handled' ? '已处理' : '已忽略')
  } catch {
    // 拦截器已提示
  }
}

async function removePost(row) {
  try {
    await ElMessageBox.confirm('删除帖子后用户将看不到该内容，确认继续？', '删除帖子', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deletePost(row.postId)
    await mark(row, 'handled')
    ElMessage.success('帖子已删除')
  } catch {
    // 拦截器已提示
  }
}

function statusMeta(status) {
  if (status === 'handled') return { label: '已处理', type: 'success' }
  if (status === 'ignored') return { label: '已忽略', type: 'info' }
  return { label: '待处理', type: 'warning' }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-title">举报处理</div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="postTitle" label="帖子" min-width="140" show-overflow-tooltip />
      <el-table-column prop="postContent" label="内容" min-width="200" show-overflow-tooltip />
      <el-table-column prop="reason" label="举报原因" width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type" size="small">
            {{ statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="举报时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="mark(row, 'handled')">处理</el-button>
          <el-button size="small" @click="mark(row, 'ignored')">忽略</el-button>
          <el-button size="small" type="danger" @click="removePost(row)">删帖</el-button>
        </template>
      </el-table-column>
    </el-table>
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
