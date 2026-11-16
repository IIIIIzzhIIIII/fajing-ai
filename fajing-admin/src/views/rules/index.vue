<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTradeRule, saveTradeRule } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const rule = ref({})

const form = reactive({
  refundDays: 7,
  afterSaleText: ''
})

const updateInfo = computed(() => {
  if (!rule.value.updatedAt) return '尚未保存过'
  return `最近更新：${formatDateTime(rule.value.updatedAt)}`
})

async function load() {
  loading.value = true
  try {
    const data = await fetchTradeRule()
    if (data) {
      rule.value = data
      form.refundDays = data.refundDays ?? 7
      form.afterSaleText = data.afterSaleText || ''
    }
  } catch (e) {
    // 请求层已统一提示错误
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const data = await saveTradeRule({
      refundDays: form.refundDays,
      afterSaleText: form.afterSaleText
    })
    rule.value = data || {}
    ElMessage.success('交易与售后规则已保存')
  } catch (e) {
    // 请求层已统一提示错误
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="rules-page">
    <div class="page-head">
      <div>
        <div class="page-title">交易与售后规则</div>
        <div class="page-sub">规则同时作用于用户端售后说明与后台退款校验</div>
      </div>
      <el-button type="primary" :loading="saving" @click="save">保存规则</el-button>
    </div>

    <el-form label-width="120px" class="rule-form">
      <el-form-item label="售后期限">
        <el-input-number v-model="form.refundDays" :min="0" :max="365" :step="1" />
        <span class="field-tip">单位：天；0 表示不限制。超过期限的订单在后台无法退款。</span>
      </el-form-item>
      <el-form-item label="售后说明">
        <el-input
          v-model="form.afterSaleText"
          type="textarea"
          :rows="5"
          maxlength="500"
          show-word-limit
          placeholder="填写给用户展示的退款与售后说明"
        />
      </el-form-item>
      <el-form-item label="更新信息">
        <span class="field-tip">{{ updateInfo }}</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
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

.rule-form {
  max-width: 720px;
}

.field-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #a0949a;
}
</style>
