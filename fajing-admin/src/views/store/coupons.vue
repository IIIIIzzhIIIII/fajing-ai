<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createStoreCouponTier, disableStoreCouponTier, fetchStoreCouponTiers, updateStoreCouponTier
} from '@/api'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const list = ref([])
const formRef = ref()
const form = reactive({
  id: null,
  title: '',
  payAmount: 50,
  valueAmount: 69,
  description: '',
  status: 1,
  sort: 0
})

const rules = {
  title: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  payAmount: [{ required: true, message: '请输入支付金额', trigger: 'blur' }],
  valueAmount: [{ required: true, message: '请输入抵扣金额', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    list.value = (await fetchStoreCouponTiers()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, title: '', payAmount: 50, valueAmount: 69, description: '', status: 1, sort: 0
  })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    payAmount: Number(row.payAmount),
    valueAmount: Number(row.valueAmount),
    description: row.description || '',
    status: row.status,
    sort: row.sort || 0
  })
  dialog.value = true
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      id: form.id,
      title: form.title.trim(),
      payAmount: Number(form.payAmount),
      valueAmount: Number(form.valueAmount),
      description: form.description.trim(),
      status: form.status,
      sort: Number(form.sort) || 0
    }
    if (form.id) {
      await updateStoreCouponTier(payload)
      ElMessage.success('券档位已更新')
    } else {
      await createStoreCouponTier(payload)
      ElMessage.success('券档位已创建')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function disable(row) {
  try {
    await ElMessageBox.confirm(`确认下架「${row.title}」？已售出的券仍可核销。`, '下架券档位', {
      type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await disableStoreCouponTier(row.id)
  ElMessage.success('已下架')
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <div class="page-title">门店券管理</div>
        <div class="page-sub">门店专属团购券，顾客购买后只能在本店核销</div>
      </div>
      <el-button type="primary" @click="openCreate">新增券档位</el-button>
    </div>

    <el-table :data="list" stripe>
      <el-table-column prop="title" label="券名称" min-width="160" />
      <el-table-column label="实付 / 抵扣" width="150">
        <template #default="{ row }">¥{{ formatMoney(row.payAmount) }} / ¥{{ formatMoney(row.valueAmount) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 1" link type="danger" @click="disable(row)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑券档位' : '新增券档位'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="券名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：50 元抵 69 元" />
        </el-form-item>
        <el-form-item label="实付金额" prop="payAmount">
          <el-input-number v-model="form.payAmount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="抵扣金额" prop="valueAmount">
          <el-input-number v-model="form.valueAmount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="使用范围、是否可叠加等" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
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
</style>
