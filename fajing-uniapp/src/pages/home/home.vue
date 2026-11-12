<template>
  <view class="page active">
    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="home-head">
        <view style="display:flex;align-items:center;gap:8px">
          <view class="logo-mark"><fj-icon name="scissors" :size="16" color="#FFFFFF" /></view>
          <text style="font-weight:800;letter-spacing:-.3px">发镜 AI</text>
        </view>
        <view class="pill" @click="goProfile"><fj-icon name="user" :size="16" />我的</view>
      </view>

      <view class="hero">
        <view class="hero-eyebrow">AI HAIR TRY-ON</view>
        <view class="hero-title">换个发型<view><text class="grad-text">换张脸</text></view></view>
        <view class="hero-sub">8 秒 AI 生成 · 4 款同时对比 · 照片 24h 删除</view>
        <view class="cta-wrap">
          <view class="cta" @click="goUpload">
            <fj-icon name="camera" :size="26" color="#FFFFFF" />
            <text class="cta-main">上传我的照片</text>
            <text class="cta-sub">{{ ctaSub }}</text>
          </view>
        </view>
        <view class="sell-row">
          <view class="sell">
            <view class="sell-ico"><fj-icon name="timer" :size="16" color="#C95676" /></view>
            <view class="sell-t">8 秒出图</view>
            <view class="sell-d">不用等太久</view>
          </view>
          <view class="sell">
            <view class="sell-ico"><fj-icon name="lock" :size="16" color="#C95676" /></view>
            <view class="sell-t">用完即删</view>
            <view class="sell-d">24h 自动清空</view>
          </view>
          <view class="sell">
            <view class="sell-ico"><fj-icon name="users" :size="16" color="#C95676" /></view>
            <view class="sell-t">男女都有</view>
            <view class="sell-d">20+ 热门款</view>
          </view>
        </view>
      </view>

      <view class="sec-head">
        <view class="sec-title">本周热门</view>
        <view class="sec-more" @click="goStyles">全部发型<fj-icon name="chevron-right" :size="13" /></view>
      </view>
      <scroll-view scroll-x class="hot-strip">
        <view v-for="item in hotList" :key="item.id" class="hot-card" @click="goStyles">
          <view class="hot-thumb">
            <image :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
            <text class="hot-tag">{{ item.tag }}</text>
          </view>
          <view class="hot-name">{{ item.name }}</view>
        </view>
      </scroll-view>

      <view class="privacy-note">
        <fj-icon name="shield" :size="20" color="#E8A9BF" />
        <view style="flex:1">
          <view style="font-size:12.5px;font-weight:700">人脸照片不留存</view>
          <view style="font-size:10.5px;color:rgba(255,255,255,.6);margin-top:2px">仅用于本次生成，24 小时后自动删除</view>
        </view>
      </view>
    </scroll-view>
    <fj-tabbar active="home" />
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/store/app'
import { login } from '@/api/request'
import { getToken } from '@/utils/auth'
import { PORTRAIT, STYLES } from '@/mock/constants'

const app = useAppStore()

const ctaSub = computed(() => {
  if (app.quota.freeLeft > 0) return `今日还剩 ${app.quota.freeLeft} 次免费`
  return app.quota.shareClaimed ? '今日免费已用完 · 分享奖励已领过' : '今日免费已用完 · 分享可再得 1 次'
})

const hotList = computed(() => {
  const tags = ['热门', '新上', '圆脸友好', '显脸小', '学生感', '好打理']
  return STYLES.female.slice(0, 6).map((s, i) => ({
    id: s.id,
    name: s.name,
    tag: tags[i],
    img: PORTRAIT(s.en, 'female')
  }))
})

function goUpload() {
  if (getToken()) {
    uni.navigateTo({ url: '/pages/upload/upload' })
    return
  }
  // 登录态失效时再重试一次登录，仍失败则去登录页
  login()
    .then(() => uni.navigateTo({ url: '/pages/upload/upload' }))
    .catch(() => uni.navigateTo({ url: '/pages/login/login' }))
}
function goStyles() {
  // 首页「全部发型 / 热门款」只做图鉴浏览，不能直接发起生成
  uni.navigateTo({ url: '/pages/styles/styles?view=1' })
}
function goProfile() {
  uni.reLaunch({ url: '/pages/profile/profile' })
}
</script>

<style lang="scss" scoped>
.privacy-note {
  margin: 18px 20px 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #2d3436, #3f4a4d);
  color: #fff;
  display: flex;
  gap: 12px;
  align-items: center;
}
.hot-strip {
  white-space: nowrap;
}
</style>
