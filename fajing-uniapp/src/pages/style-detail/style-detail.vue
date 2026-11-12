<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back()"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">发型详情</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view class="detail-img-wrap">
          <image v-if="info.img" class="detail-img" :src="info.img" mode="aspectFill" />
          <view v-else class="detail-img-empty">暂无示例图</view>
        </view>

        <view class="detail-name">{{ info.name }}</view>
        <view class="detail-chips">
          <text v-if="info.cat" class="detail-chip">{{ info.cat }}</text>
          <text v-if="info.len" class="detail-chip">长度 {{ info.len }}</text>
          <text v-if="info.face" class="detail-chip">适合 {{ info.face }}</text>
        </view>

        <view class="detail-note">这里只能查看发型样式。想试这款发型，请回首页上传自己的照片。</view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const info = reactive({ id: '', name: '发型', len: '', face: '', cat: '', img: '' })

function dec(value) {
  if (!value) return ''
  try {
    return decodeURIComponent(value)
  } catch (e) {
    return value
  }
}

function back() {
  uni.navigateBack({
    fail: () => uni.reLaunch({ url: '/pages/home/home' })
  })
}

onLoad((options) => {
  const o = options || {}
  info.id = dec(o.id)
  info.name = dec(o.name) || '发型'
  info.len = dec(o.len)
  info.face = dec(o.face)
  info.cat = dec(o.cat)
  info.img = dec(o.img)
})
</script>

<style lang="scss" scoped>
.detail-img-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 3.9;
  border-radius: 22px;
  overflow: hidden;
  background: #eee6ea;
  box-shadow: 0 18px 40px rgba(45, 52, 54, 0.16);
  margin-top: 6px;
}
.detail-img {
  width: 100%;
  height: 100%;
}
.detail-img-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--muted);
}
.detail-name {
  margin-top: 16px;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.4px;
}
.detail-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
}
.detail-chip {
  font-size: 11px;
  font-weight: 600;
  color: var(--pink-700);
  background: #fdf1f3;
  border: 1px solid #f6e3e7;
  padding: 4px 9px;
  border-radius: 99px;
}
.detail-note {
  margin-top: 18px;
  padding: 11px 13px;
  border-radius: 12px;
  background: #fff7ec;
  color: var(--ink-2);
  font-size: 11.5px;
  line-height: 1.6;
}
</style>
