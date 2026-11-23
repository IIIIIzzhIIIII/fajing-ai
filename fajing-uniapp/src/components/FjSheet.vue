<template>
  <view>
    <!-- Toast -->
    <view v-if="ui.toastVisible" class="fj-toast">{{ ui.toastText }}</view>

    <!-- 遮罩 -->
    <view class="mask" :class="{ show: !!ui.sheet }" @click="ui.close()" />

    <!-- 分享 -->
    <view class="sheet" :class="{ show: ui.sheet === 'share' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="sheet-title">分享我的新发型</view>
      <view class="sheet-sub">带上前后对比图，发朋友圈问问闺蜜像不像。</view>
      <view class="share-card">
        <view class="sc-half before">
          <image :src="beforeImg" mode="aspectFill" class="sc-img" />
          <text class="sc-tag">原图</text>
        </view>
        <view class="sc-half">
          <image :src="afterImg" mode="aspectFill" class="sc-img" />
          <text class="sc-tag r">AI 后</text>
        </view>
        <view class="sc-divider" />
        <view class="share-card-mask" />
        <view class="share-card-text">
          <view class="share-brand"><fj-icon name="scissors" :size="13" color="#FFFFFF" />发镜 AI · 剪之前，先看见</view>
          <view class="share-title">{{ currentStyle.name }}</view>
          <view class="share-slogan">同一个人 · 换了个发型</view>
        </view>
      </view>
      <view class="copy-box">
        <text class="copy-text">我在「发镜 AI」试了新发型，8 秒出图，帮我看看适不适合？</text>
        <view class="copy-btn" @click="copyText"><fj-icon name="copy" :size="13" />复制</view>
      </view>
      <view class="share-channels">
        <view class="channel" @click="shareTo('微信好友')">
          <text class="channel-ico" style="background:#22C35E">微</text>微信
        </view>
        <view class="channel" @click="shareTo('朋友圈')">
          <text class="channel-ico" style="background:#3DA35A">朋</text>朋友圈
        </view>
        <view class="channel" @click="shareTo('抖音')">
          <text class="channel-ico" style="background:#161823">抖</text>抖音
        </view>
        <view class="channel" @click="shareTo('小红书')">
          <text class="channel-ico" style="background:#FF2442">红</text>小红书
        </view>
      </view>
      <button class="btn btn-primary btn-block" style="margin-top:14px" :disabled="app.quota.shareClaimed" @click="claimShare">
        <fj-icon name="gift" :size="16" :color="app.quota.shareClaimed ? '#B7A8AF' : '#FFFFFF'" />
        {{ app.quota.shareClaimed ? '分享奖励已领过（限 1 次）' : '领取分享奖励 · 得 10 魅力值' }}
      </button>
      <view class="share-bonus">
        <fj-icon name="gift" :size="13" />每账号限 1 次 · 好友点开也能免费试
      </view>
    </view>

    <!-- 关于我们 -->
    <view class="sheet" :class="{ show: ui.sheet === 'about' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="sheet-title">关于我们</view>
      <fj-doc-content :text="site.aboutContent || '发镜 AI · 剪之前，先看见'" />
    </view>

    <!-- 隐私与数据 -->
    <view class="sheet" :class="{ show: ui.sheet === 'privacy' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="sheet-title">隐私与数据</view>
      <view class="sheet-sub">人脸照片属于敏感信息，"用完即删"是我们的默认设置，不是可选项。</view>
      <view class="priv-row">
        <view class="priv-ico"><fj-icon name="timer" :size="16" /></view>
        <view>
          <view class="priv-t">24 小时自动删除</view>
          <view class="priv-d">上传的原图仅用于本次生成，生成结束 24 小时内从服务器彻底清除。</view>
        </view>
      </view>
      <view class="priv-row">
        <view class="priv-ico"><fj-icon name="shield" :size="16" /></view>
        <view>
          <view class="priv-t">不建人脸库 · 不用于训练</view>
          <view class="priv-d">不会留存人脸特征，也不会把你的照片用于模型训练或提供给第三方。</view>
        </view>
      </view>
      <view class="priv-row">
        <view class="priv-ico"><fj-icon name="user" :size="16" /></view>
        <view>
          <view class="priv-t">试发记录仅本人可见</view>
          <view class="priv-d">历史记录保存在你的账号下，仅用于你自己查看和再次解锁。</view>
        </view>
      </view>
      <button class="btn btn-danger btn-block" style="margin-top:14px" @click="clearData">
        <fj-icon name="trash" :size="16" />立即删除我的照片与记录
      </button>
      <view class="tiny" style="text-align:center;margin-top:10px">会删除云端与本机的试发记录；分享奖励每账号限 1 次，不随清除重置</view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { track } from '@/utils/track'
import { site } from '@/utils/site'
import FjDocContent from '@/components/FjDocContent.vue'

const app = useAppStore()
const ui = useUiStore()

const currentStyle = computed(() => {
  const list = (app.result && app.result.styles) || []
  const id = app.selectedStyleIds[0]
  return list.find((s) => s.styleId === id) || list[0] || { name: '法式波波头', img: '', before: '' }
})
const beforeImg = computed(() => (app.result && app.result.before) || app.photoPath || '')
const afterImg = computed(() => currentStyle.value.img || '')

async function claimShare() {
  if (app.quota.shareClaimed) {
    ui.toast('分享奖励每账号限领 1 次')
    return
  }
  try {
    // 额度由服务端发放，本地只展示服务端返回值
    app.setQuota(await API.shareReward())
    ui.toast('已获得 10 点魅力值')
    ui.close()
  } catch (e) {
    // 服务端已判重（例如清过本地缓存）时锁定按钮，避免反复点击
    if (String((e && e.message) || '').indexOf('已领取') > -1) {
      app.setQuota({ shareClaimed: true })
    }
  }
}

function copyText() {
  uni.setClipboardData({
    data: '我在「发镜 AI」试了新发型，8 秒出图，帮我看看适不适合？',
    success: () => ui.toast('文案已复制')
  })
}

async function shareTo(channel) {
  // 渠道与传播看板按 share_click 统计分享行为，先上报再领奖励
  track('share_click', { shareChannel: channel })
  if (!app.quota.shareClaimed) {
    try {
      app.setQuota(await API.shareReward())
    } catch (e) {
      if (String((e && e.message) || '').indexOf('已领取') > -1) {
        app.setQuota({ shareClaimed: true })
      }
    }
  }
  ui.toast(`已分享到${channel}，奖励每账号限 1 次`)
}

async function clearData() {
  // 隐私承诺：同时清掉服务端试发记录，避免只清本机、云端仍留照片
  try {
    await API.clearRecords()
  } catch (e) {
    /* request 已统一 toast，本地清理继续 */
  }
  app.resetAll()
  // 清数据只清记录与本地缓存，额度仍以服务端为准
  try {
    app.setQuota(await API.quota())
  } catch (e) {
    /* 同步失败时保留默认值，下次进入会重新拉取 */
  }
  ui.toast('已清空本机与云端记录')
  ui.close()
  uni.reLaunch({ url: '/pages/home/home' })
}
</script>

<style lang="scss" scoped>
.fj-toast {
  position: fixed;
  left: 50%;
  top: 44%;
  transform: translate(-50%, -50%);
  background: rgba(28, 28, 34, 0.9);
  color: #fff;
  font-size: 12.5px;
  padding: 10px 16px;
  border-radius: 12px;
  max-width: 76%;
  text-align: center;
  z-index: 200;
  line-height: 1.5;
}
.sc-img {
  width: 100%;
  height: 100%;
}
.cm-item {
  padding: 8px 0;
  border-bottom: 1px solid #f7f0f2;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.cm-name {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-2);
}
.cm-text {
  font-size: 12px;
  color: var(--ink);
  line-height: 1.6;
}
</style>
