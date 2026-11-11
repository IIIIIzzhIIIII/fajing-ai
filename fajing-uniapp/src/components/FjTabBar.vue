<template>
  <view class="tabbar">
    <view class="tab" :class="{ on: active === 'home' }" @click="go('/pages/home/home')">
      <fj-icon name="home" :size="20" :color="active === 'home' ? '#B54263' : '#B4A9AE'" />
      <text>首页</text>
      <view class="tab-dot" />
    </view>
    <view class="tab" :class="{ on: active === 'community' }" @click="go('/pages/community/community')">
      <fj-icon name="message" :size="20" :color="active === 'community' ? '#B54263' : '#B4A9AE'" />
      <text>讨论</text>
      <view class="tab-dot" />
    </view>
    <view class="tab" :class="{ on: active === 'profile' }" @click="go('/pages/profile/profile')">
      <fj-icon name="user" :size="20" :color="active === 'profile' ? '#B54263' : '#B4A9AE'" />
      <text>我的</text>
      <view class="tab-dot" />
    </view>
  </view>
</template>

<script setup>
defineProps({
  active: { type: String, default: 'home' }
})

function go(url) {
  uni.reLaunch({ url })
}
</script>

<style lang="scss" scoped>
.tabbar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 8px 6px calc(8px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.94);
  border-top: 1px solid var(--line);
  backdrop-filter: blur(12px);
}
.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: #b4a9ae;
  font-weight: 600;
  flex: 1;
}
.tab.on {
  color: var(--pink-600);
}
.tab.on .tab-dot {
  opacity: 1;
}
.tab-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--pink-600);
  opacity: 0;
  transition: opacity 0.18s;
}
</style>
