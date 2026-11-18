<template>
  <view class="page active login-page">
    <view class="login-brand">
      <view class="logo-mark"><fj-icon name="scissors" :size="22" color="#C95676" /></view>
      <view class="brand-name">发镜 AI</view>
      <view class="brand-slogan">剪之前，先看见</view>
    </view>

    <view class="login-main">
      <view class="login-title">上传一张正脸照，先看看新发型</view>
      <!-- #ifdef H5 -->
      <view class="login-sub">无需注册即可试用，照片仅用于本次生成，24 小时自动删除。</view>
      <!-- #endif -->
      <!-- #ifndef H5 -->
      <view class="login-sub">微信一键登录，照片仅用于本次生成，24 小时自动删除。</view>
      <!-- #endif -->
    </view>

    <view class="login-foot">
      <view class="login-agree login-agree-check" @click="toggleAgree">
        <view :class="['checkbox', agreed ? 'checked' : '']">
          <fj-icon v-if="agreed" name="check" :size="10" color="#fff" />
        </view>
        <text>我已阅读并同意</text>
        <text class="hl" @click.stop="openDoc('terms')">《用户协议》</text>
        <text>与</text>
        <text class="hl" @click.stop="openDoc('privacy')">《隐私政策》</text>
      </view>
      <button class="btn btn-primary btn-block" :disabled="!agreed" :loading="loading" @click="start">
        {{ loading ? '登录中…' : '开始试发型' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/request'

const loading = ref(false)
const agreed = ref(false)

function toggleAgree() {
  agreed.value = !agreed.value
}

async function start() {
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议与隐私政策', icon: 'none' })
    return
  }
  if (loading.value) return
  loading.value = true
  try {
    await login()
    uni.reLaunch({ url: '/pages/home/home' })
  } catch (e) {
    uni.showToast({ title: '登录失败，请稍后重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function openDoc(name) {
  uni.navigateTo({ url: `/pages/agreement/${name}` })
}
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  flex-direction: column;
  padding: 0 28px calc(30px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, #fdf7f9 0%, #faf8f7 60%);
}
.login-brand {
  padding-top: 96px;
  text-align: center;
}
.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 14px;
  border-radius: 18px;
}
.brand-name {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.6px;
}
.brand-slogan {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 2px;
}
.login-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  text-align: center;
}
.login-title {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.3px;
  line-height: 1.5;
}
.login-sub {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--muted);
}
.login-foot {
  padding-bottom: 20px;
}
.login-agree {
  margin-top: 14px;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}
.login-agree .hl {
  color: var(--pink-700);
  font-weight: 600;
}
.login-agree-check {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-bottom: 14px;
  line-height: 1;
}
.checkbox {
  width: 14px;
  height: 14px;
  border: 1.5px solid #d0d0d0;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  flex-shrink: 0;
}
.checkbox.checked {
  background: var(--pink-600);
  border-color: var(--pink-600);
}
.btn[disabled] {
  opacity: 0.5;
}
</style>
