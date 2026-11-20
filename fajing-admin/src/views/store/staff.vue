<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createStoreStaff, deleteStoreStaff, fetchStoreStaff, updateStoreStaff } from '@/api'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const list = ref([])
const formRef = ref()
const form = reactive({ id: null, name: '', phone: '', dailyLimit: 20, status: 1 })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

async function load() {
  loading.value = true
  try {
    list.value = (await fetchStoreStaff()) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', phone: '', dailyLimit: 20, status: 1 })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    phone: row.phone,
    dailyLimit: row.dailyLimit,
    status: row.status
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
    if (form.id) {
      await updateStoreStaff(form.id, {
        name: form.name.trim(),
        phone: form.phone.trim(),
        dailyLimit: Number(form.dailyLimit),
        status: form.status
      })
      ElMessage.success('员工信息已更新')
    } else {
      await createStoreStaff({
        name: form.name.trim(),
        phone: form.phone.trim(),
        dailyLimit: Number(form.dailyLimit)
      })
      ElMessage.success('员工已创建，把邀请码发给员工即可绑定')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function removeRow(row) {
  try {
    await ElMessageBox.confirm(
      `删除员工「${row.name}」？删除后其员工身份立即失效，需重新添加才能再次绑定。`,
      '删除员工',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await deleteStoreStaff(row.id)
  ElMessage.success('员工已删除')
  await load()
}

async function copy(value) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <div class="page-title">员工管理</div>
        <div class="page-sub">只需填写手机号添加员工；员工在用户端用「邀请码 + 本人已授权手机号」绑定工作台</div>
      </div>
      <el-button type="primary" @click="openCreate">新增员工</el-button>
    </div>

    <el-table :data="list" stripe>
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="邀请码" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="copy(row.inviteCode)">{{ row.inviteCode }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="今日额度" width="110">
        <template #default="{ row }">{{ row.todayUsed || 0 }} / {{ row.dailyLimit }}</template>
      </el-table-column>
      <el-table-column prop="orderCount" label="归因订单" width="100" />
      <el-table-column label="归因金额" width="110">
        <template #default="{ row }">¥{{ formatMoney(row.orderAmount) }}</template>
      </el-table-column>
      <el-table-column prop="verifiedCount" label="核销券" width="90" />
      <el-table-column label="归因佣金" width="110">
        <template #default="{ row }">¥{{ formatMoney(row.commissionAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '在职' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑员工' : '新增员工'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="员工本人已授权的手机号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="选填，默认按手机号后四位生成" />
        </el-form-item>
        <el-form-item label="每日额度">
          <el-input-number v-model="form.dailyLimit" :min="1" :max="200" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="在职" inactive-text="停用" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="员工需先在用户端完成昵称与手机号授权，再在「我的 - 员工工作台」输入邀请码和手机号完成绑定。"
        />
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
