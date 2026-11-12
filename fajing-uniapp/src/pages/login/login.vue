<template>
  <view class="page active login-page">
    <view class="login-brand">
      <view class="logo-mark"><fj-icon name="scissors" :size="22" color="#FFFFFF" /></view>
      <view class="brand-name">发镜 AI</view>
      <view class="brand-slogan">剪之前，先看见</view>
    </view>

    <view class="login-main">
      <view class="login-title">上传一张正脸照，先看看新发型</view>
      <view class="login-sub">微信一键登录，照片仅用于本次生成，24 小时自动删除。</view>
    </view>

    <view class="login-foot">
      <button class="btn btn-primary btn-block" :loading="loading" @click="start">
        {{ loading ? '登录中…' : '开始试发型' }}
      </button>
      <view class="login-agree">
        登录即表示同意
        <text class="hl" @click="openDoc('terms')">《用户协议》</text>
        与
        <text class="hl" @click="openDoc('privacy')">《隐私政策》</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/request'

const loading = ref(false)

async function start() {
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
  background: linear-gradient(135deg, #e08aa2, #c95676);
  border-radius: 18px;
  box-shadow: 0 12px 26px rgba(201, 86, 118, 0.28);
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
</style>
