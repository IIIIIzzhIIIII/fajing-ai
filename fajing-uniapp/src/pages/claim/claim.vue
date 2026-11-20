<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="18" /></view>
      <view class="topbar-title">领取试发结果</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view v-if="loading" class="loading-card">
          <fj-icon name="sparkles" :size="26" color="#C95676" />
          <text>正在领取试发结果…</text>
        </view>

        <view v-else-if="error" class="error-card">
          <fj-icon name="info" :size="26" color="#C68A25" />
          <view class="error-title">无法领取</view>
          <view class="error-desc">{{ error }}</view>
          <view class="primary-btn" @click="back">返回首页</view>
        </view>

        <block v-else-if="task">
          <view class="claim-hero">
            <view class="ch-title">你的 AI 发型预览</view>
            <view class="ch-sub">
              {{ task.storeName || '合作门店' }}
              <text v-if="task.staffName"> · 服务发型师 {{ task.staffName }}</text>
            </view>
          </view>

          <view class="result-grid">
            <view v-for="item in resultStyles" :key="item.styleId" class="result-item">
              <image v-if="item.img" :src="item.img" class="result-img" mode="aspectFill" />
              <view v-else class="result-placeholder">
                <fj-icon name="image" :size="22" color="#D8A7B6" />
              </view>
              <view class="result-name">{{ item.name }}</view>
            </view>
          </view>

          <view v-if="!resultStyles.length" class="empty">本次试发暂无可用图片，请让员工重新生成。</view>

          <view v-if="task.storeId" class="store-card" @click="openStore">
            <view class="sc-main">
              <view class="sc-title">{{ task.storeName || '合作门店' }}</view>
              <view class="sc-desc">查看门店专享券，到店出示核销码使用</view>
            </view>
            <fj-icon name="chevron-right" :size="16" color="#C9BFC3" />
          </view>

          <view class="tip">
            本次试发由门店员工为你生成，照片仅用于本次 AI 预览，结果 24 小时内有效。实际剪发效果受发质、发量与发型师操作影响，仅供造型参考。
          </view>
        </block>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { API } from '@/api/request'
import { goBack } from '@/utils/nav'

const loading = ref(true)
const error = ref('')
const task = ref(null)

const resultStyles = computed(() => {
  const result = task.value && task.value.result
  return (result && result.styles) || []
})

function back() {
  goBack()
}

async function claim(taskId, token) {
  if (!taskId || !token) {
    error.value = '领取链接不完整，请让员工重新展示二维码。'
    loading.value = false
    return
  }
  try {
    task.value = await API.employeeClaim({ taskId, token })
  } catch (e) {
    error.value = (e && e.message) || '领取失败，请联系门店员工。'
  } finally {
    loading.value = false
  }
}

function openStore() {
  if (!task.value || !task.value.storeId) return
  uni.navigateTo({ url: `/pages/store/store?id=${task.value.storeId}` })
}

onLoad((options) => {
  claim(options && options.taskId, options && options.token)
})
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 8px;
}
.icon-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 50%;
  box-shadow: var(--sh-sm);
}
.topbar-title {
  font-size: 16px;
  font-weight: 700;
}
.scroll {
  height: calc(100vh - 56px);
}
.pad {
  padding: 8px 20px 32px;
}
.loading-card,
.error-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 50px 20px;
  margin-top: 20px;
  font-size: 12px;
  color: var(--muted);
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--sh-sm);
}
.error-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}
.error-desc {
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
}
.claim-hero {
  padding: 20px;
  color: #fff;
  background: linear-gradient(135deg, #3a2f34, #57414a 60%, #6d4a52);
  border-radius: 20px;
}
.ch-title {
  font-size: 20px;
  font-weight: 800;
}
.ch-sub {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.78;
}
.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 14px;
}
.result-item {
  overflow: hidden;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
}
.result-img {
  width: 100%;
  height: 200px;
}
.result-placeholder {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #faf5f7;
}
.result-name {
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}
.store-card {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 16px;
  margin-top: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
}
.sc-main {
  flex: 1;
}
.sc-title {
  font-size: 14px;
  font-weight: 700;
}
.sc-desc {
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--muted);
}
.tip {
  margin-top: 16px;
  font-size: 10.5px;
  line-height: 1.8;
  color: var(--muted);
}
.primary-btn {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-top: 14px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d46a88, #b14a6b);
  border-radius: 999px;
}
.empty {
  padding: 40px 0;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}
</style>
