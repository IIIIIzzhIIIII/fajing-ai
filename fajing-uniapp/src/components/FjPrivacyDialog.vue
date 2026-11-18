<template>
  <view v-if="privacy.visible" class="privacy-mask" @click.stop @touchmove.stop.prevent>
    <view class="privacy-card">
      <view class="privacy-title">用户隐私保护提示</view>
      <view class="privacy-body">
        感谢你使用「发镜 AI」。请阅读并同意<text class="privacy-link" @click.stop="openPrivacyContract">《用户隐私保护指引》</text>，我们会在你上传照片时使用该照片生成发型效果，生成结束 24 小时内自动删除，不留存人脸特征，也不用于模型训练。
      </view>
      <view v-if="privacy.contractName" class="privacy-contract">{{ privacy.contractName }}</view>
      <view class="privacy-actions">
        <button class="privacy-btn ghost" @click.stop="onReject">不同意</button>
        <button
          :id="agreeId"
          class="privacy-btn primary"
          :open-type="agreeOpenType"
          @click.stop="onAgreeTap"
          @agreeprivacyauthorization="onAgreeAuthorized"
        >
          同意并继续
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import {
  privacy,
  agreePrivacy,
  rejectPrivacy,
  openPrivacyContract,
  hasPendingAuth
} from '@/utils/privacy'

/** 微信要求自定义弹窗的同意按钮带 open-type 并回传按钮 id */
const agreeId = 'fajing-privacy-agree'
const agreeOpenType = computed(() => {
  let type = ''
  // #ifdef MP-WEIXIN
  type = 'agreePrivacyAuthorization'
  // #endif
  return type
})

/** 官方授权事件会单独触发；这里只处理无需官方校验的演示场景，避免重复回调 */
function onAgreeTap() {
  if (hasPendingAuth()) return
  agreePrivacy(agreeId)
}

function onAgreeAuthorized() {
  agreePrivacy(agreeId)
}

function onReject() {
  rejectPrivacy()
  uni.showToast({ title: '需同意隐私协议后使用', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.privacy-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 28px;
  background: rgba(24, 20, 22, 0.5);
}
.privacy-card {
  width: 100%;
  padding: 22px 20px 18px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.18);
}
.privacy-title {
  font-size: 16px;
  font-weight: 800;
  text-align: center;
  letter-spacing: -0.3px;
}
.privacy-body {
  margin-top: 12px;
  font-size: 12.5px;
  line-height: 1.85;
  color: #6b6067;
}
.privacy-link {
  color: #c95676;
  font-weight: 600;
}
.privacy-contract {
  margin-top: 10px;
  font-size: 11px;
  line-height: 1.6;
  color: #a99aa1;
}
.privacy-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.privacy-btn {
  flex: 1;
  height: 44px;
  margin: 0;
  padding: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 44px;
  border-radius: 12px;

  &::after {
    border: none;
  }

  &.ghost {
    color: #8d8087;
    background: #f5f0f2;
  }

  &.primary {
    color: #fff;
    background: linear-gradient(135deg, #e08aa2, #c95676);
  }
}
</style>
