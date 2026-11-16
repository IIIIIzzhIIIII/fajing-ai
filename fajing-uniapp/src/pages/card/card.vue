<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="goBack">
        <fj-icon name="arrow-left" :size="16" />
      </view>
      <view class="topbar-title">理发师沟通卡</view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="pad" style="padding-top:2px">
        <view class="field-sub" style="margin:0 0 12px">把这张卡发给托尼，比“剪短一点”清楚一百倍。</view>
      </view>

      <view class="barber-wrap">
        <view class="barber-card">
          <view class="bc-head">
            <view class="bc-brand">
              <fj-icon name="scissors" :size="13" color="#FFFFFF" />
              <text>发镜 AI</text>
            </view>
            <view class="bc-tag">理发师沟通卡</view>
          </view>

          <view class="bc-photo">
            <image :src="current.img" mode="aspectFill" style="width:100%;height:100%" />
            <text class="bc-badge">参考效果图</text>
          </view>

          <view class="bc-body">
            <view class="bc-name">{{ current.name }}</view>
            <view class="bc-note">长度：{{ current.len }} · 适合{{ current.face }}</view>
            <view class="bc-specs">
              <view v-for="row in specRows" :key="row.k" class="bc-row">
                <text class="bc-k">{{ row.k }}</text>
                <text class="bc-v">{{ row.v }}</text>
              </view>
            </view>
            <view class="bc-tip">给理发师的提醒：{{ spec.tip }}</view>
          </view>

          <view class="bc-foot">
            <text>AI 生成参考图 · 仅供沟通</text>
            <text class="bc-qr">发镜 AI · 剪之前先看见</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view style="display:flex;gap:10px;margin-bottom:10px">
        <view class="btn btn-ghost" style="flex:1" @click="copyText">
          <fj-icon name="copy" :size="16" />
          <text>复制文字</text>
        </view>
        <view class="btn btn-ghost" style="flex:1" @click="saveCard">
          <fj-icon name="download" :size="16" />
          <text>保存卡片</text>
        </view>
      </view>
      <view class="btn btn-primary btn-block" @click="sendToBarber">
        <fj-icon name="share" :size="16" color="#FFFFFF" />
        <text>发给理发师</text>
      </view>
    </view>

    <fj-sheet />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { BARBER_SPEC, BARBER_FALLBACK, DEMO_BEFORE } from '@/mock/constants'
import { goBack as navBack } from '@/utils/nav'

const app = useAppStore()
const ui = useUiStore()

/** 从结果页进入时携带当前浏览的款式，避免沟通卡固定取第一款（可能是失败款） */
const activeStyleId = ref('')
onLoad((query) => {
  activeStyleId.value = (query && query.styleId) || ''
})

const FALLBACK_STYLE = {
  styleId: 'mock',
  name: '法式波波头',
  len: '肩部以上',
  face: '圆脸 / 鹅蛋脸',
  cat: '波波头',
  img: DEMO_BEFORE
}

const current = computed(() => {
  const list = (app.result && app.result.styles) || []
  const usable = list.filter((s) => s && s.img)
  return (
    usable.find((s) => s.styleId === activeStyleId.value) ||
    usable[0] ||
    list[0] ||
    FALLBACK_STYLE
  )
})

const spec = computed(() => {
  const table = BARBER_SPEC[app.gender] || {}
  return table[current.value.cat] || BARBER_FALLBACK
})

const specRows = computed(() => [
  { k: '长度', v: spec.value.len },
  { k: '层次', v: spec.value.layer },
  { k: '刘海', v: spec.value.bangs },
  { k: '两侧', v: spec.value.side }
])

const messageText = computed(() =>
  [
    '【发镜 AI · 理发师沟通卡】',
    `发型：${current.value.name}`,
    `长度：${spec.value.len}`,
    `层次：${spec.value.layer}`,
    `刘海：${spec.value.bangs}`,
    `两侧：${spec.value.side}`,
    `提醒：${spec.value.tip}`,
    '参考图见聊天图（AI 生成，仅供沟通）'
  ].join('\n')
)

function copyText() {
  uni.setClipboardData({
    data: messageText.value,
    success: () => ui.toast('沟通卡文字已复制，粘贴发给理发师即可'),
    fail: () => ui.toast('复制失败，请长按手动复制')
  })
}

function saveCard() {
  ui.toast('请直接截图保存这张卡片')
}

function sendToBarber() {
  ui.toast('已生成沟通卡，可发微信给理发师')
}

function goBack() {
  navBack()
}
</script>
