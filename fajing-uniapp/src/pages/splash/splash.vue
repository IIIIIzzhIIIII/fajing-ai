<template>
  <view class="page active" id="page-splash" @click="enter">
    <view class="splash-blob" style="width:280px;height:280px;background:rgba(255,255,255,.16);left:-100px;top:-80px" />
    <view class="splash-blob" style="width:200px;height:200px;background:rgba(255,255,255,.12);right:-70px;bottom:80px" />
    <view class="splash-inner">
      <view class="splash-logo"><fj-icon name="scissors" :size="40" color="#FFFFFF" /></view>
      <view class="splash-name">发镜 AI</view>
      <view class="splash-line" />
      <view class="splash-slogan">剪之前，先看见</view>
    </view>
    <view class="splash-skip">{{ countdown }} 秒后进入 · 点击跳过</view>
  </view>
</template>

<script setup>
import { onUnmounted, ref } from 'vue'

const countdown = ref(3)
let timer = null

function enter() {
  if (timer) clearInterval(timer)
  uni.reLaunch({ url: '/pages/home/home' })
}

timer = setInterval(() => {
  countdown.value -= 1
  if (countdown.value <= 0) enter()
}, 1000)

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
