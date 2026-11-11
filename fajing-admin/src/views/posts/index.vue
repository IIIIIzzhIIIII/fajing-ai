<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { deletePost, fetchPosts } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const loadFailed = ref(false)
const list = ref([])

const badgeMap = {
  real: { label: '真实作业', className: 'is-real' },
  ask: { label: '求建议', className: 'is-ask' }
}

async function loadList() {
  loading.value = true
  loadFailed.value = false
  try {
    const data = await fetchPosts()
    list.value = Array.isArray(data) ? data : []
  } catch {
    list.value = []
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function summary(content, length = 42) {
  if (!content) return '-'
  return content.length > length ? `${content.slice(0, length)}…` : content
}

function badgeOf(value) {
  return badgeMap[value] || { label: value || '-', className: 'is-default' }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name || '匿名'}」的这篇帖子吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  try {
    await deletePost(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // 错误提示统一由 axios 拦截器处理
  }
}

onMounted(loadList)
</script>

<template>
  <div class="post-page">
    <div class="page-toolbar">
      <span class="list-total">共 {{ list.length }} 篇帖子</span>
      <el-button :icon="Refresh" circle title="刷新" @click="loadList" />
    </div>

    <el-table v-loading="loading" :data="list" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="作者" min-width="150">
        <template #default="{ row }">
          <div class="author">
            <span class="author-name">{{ row.name || '匿名' }}</span>
            <span v-if="row.meta" class="author-meta">{{ row.meta }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="styleName" label="发型" min-width="120">
        <template #default="{ row }">{{ row.styleName || '-' }}</template>
      </el-table-column>
      <el-table-column label="内容摘要" min-width="260">
        <template #default="{ row }">
          <el-tooltip
            :content="row.content"
            placement="top"
            :disabled="!row.content || row.content.length <= 42"
            :show-after="200"
          >
            <span class="summary-text">{{ summary(row.content) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="徽章" width="110">
        <template #default="{ row }">
          <el-tag class="badge-tag" :class="badgeOf(row.badge).className" effect="light" round>
            {{ badgeOf(row.badge).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="likes" label="点赞数" width="90" align="center" />
      <el-table-column label="发布时间" width="160">
        <template #default="{ row }">
          <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty
          :description="loadFailed ? '数据加载失败，请点击刷新重试' : '暂无帖子数据'"
          :image-size="90"
        />
      </template>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.list-total {
  font-size: 13px;
  color: #8b7f86;
}

.author {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.author-name {
  font-weight: 600;
  color: #2b2628;
}

.author-meta {
  font-size: 12px;
  color: #a0949a;
}

.summary-text {
  display: -webkit-box;
  overflow: hidden;
  color: #534a4f;
  cursor: default;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.badge-tag {
  border: none;

  &.is-real {
    --el-tag-bg-color: #eaf6ef;
    --el-tag-text-color: #3e8e5f;
  }

  &.is-ask {
    --el-tag-bg-color: #fdf2e3;
    --el-tag-text-color: #b07a2a;
  }

  &.is-default {
    --el-tag-bg-color: #f2f0f1;
    --el-tag-text-color: #7d7278;
  }
}

.time-text {
  font-size: 13px;
  color: #7d7278;
}
</style>
