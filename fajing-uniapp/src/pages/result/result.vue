<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="goBack">
        <fj-icon name="arrow-left" :size="16" />
      </view>
      <view class="topbar-title">你的新发型</view>
      <view class="pts-pill" @click="goPoints">
        <fj-icon name="crown" :size="13" color="#C68A25" />
        <text>{{ app.quota.unlimited ? '不限量' : (app.quota.points || 0) }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="result-meta">
        <view class="meta-main">
          <view class="meta-name">{{ current.name || '新发型' }}</view>
          <view class="meta-desc">
            长度：{{ current.len || '适中' }} · 适合{{ current.face || '多数脸型' }}
            <text v-if="current.colorName" class="meta-color"> · {{ current.colorName }}</text>
          </view>
        </view>
        <view class="pill" @click="again">
          <fj-icon name="refresh" :size="13" color="#4E575A" />
          <text>继续换</text>
        </view>
      </view>

      <view class="pad">
        <view v-if="isPending(current)" class="res-state">
          <fj-icon name="lock" :size="26" color="#C68A25" />
          <text class="res-state-t">{{ lockedTitle }}</text>
          <text class="res-state-d">解锁后立即生成这张效果图，可保存高清无水印版</text>
          <view class="btn btn-primary state-btn" @click="unlockCurrent">10 魅力值解锁</view>
        </view>
        <view v-else-if="isFailed(current)" class="res-state" @click="retryCurrent">
          <fj-icon name="refresh" :size="24" color="#8B9295" />
          <text class="res-state-t">{{ retrying ? '正在重新生成…' : '生成失败，点击重试' }}</text>
          <text class="res-state-d">{{ current.failReason || '生成超时/服务异常' }}</text>
        </view>
        <template v-else>
          <fj-compare-slider
            ref="sliderRef"
            v-model:pct="baPct"
            :before="beforeImg"
            :after="current.img"
            :unlocked="true"
            @dragstart="onSliderDrag"
          />

          <view class="ba-replay" @click="replayCompare">
            <fj-icon name="refresh" :size="13" color="#8B9295" />
            <text>拖动滑块对比前后 · 点此重播</text>
          </view>

          <view class="fit-card">
            <view class="fit-score">
              <!-- #ifdef H5 -->
              <svg width="54" height="54" viewBox="0 0 54 54">
                <defs>
                  <linearGradient id="fitGrad" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stop-color="#E8A0B4" />
                    <stop offset="100%" stop-color="#C95676" />
                  </linearGradient>
                </defs>
                <circle class="fit-score-bg" cx="27" cy="27" r="23" fill="none" stroke-width="5" />
                <circle
                  class="fit-score-fg"
                  cx="27"
                  cy="27"
                  r="23"
                  fill="none"
                  stroke-width="5"
                  stroke-dasharray="144.5"
                  :stroke-dashoffset="fitDash"
                />
              </svg>
              <!-- #endif -->
              <!-- #ifndef H5 -->
              <view class="fit-ring" :style="ringStyle">
                <view class="fit-ring-in" />
              </view>
              <!-- #endif -->
              <view class="fit-score-num">
                {{ fit.score }}<text style="font-size:9px;font-weight:700">分</text>
              </view>
            </view>
            <view class="fit-main">
              <view class="fit-t">
                <text>适配度评分</text>
                <text class="fit-lv">{{ fit.lv }}</text>
              </view>
              <view class="fit-d">{{ fit.desc }}</view>
              <view class="fit-tags">
                <text v-for="tag in fit.tags" :key="tag" class="fit-tag">{{ tag }}</text>
              </view>
            </view>
          </view>
        </template>

        <view class="barber-entry" @click="openCard">
          <view class="be-ico">
            <fj-icon name="scissors" :size="16" color="#F0B7C7" />
          </view>
          <view style="flex:1;min-width:0">
            <view class="be-t">生成理发师沟通卡</view>
            <view class="be-d">长度、层次、推剪卡尺写清楚，直接发给托尼</view>
          </view>
          <fj-icon name="chevron-right" :size="16" color="rgba(255,255,255,.55)" />
        </view>

        <!-- 周边团购券 -->
        <view class="coupon-sec">
          <view class="coupon-head">
            <view>
              <view class="coupon-title">周边门店团购券</view>
              <view class="coupon-sub">下单即出核销二维码，到店出示即可使用</view>
            </view>
            <view class="coupon-mine" @click="goCoupons">
              券包 {{ app.quota.couponCount || 0 }} 张
            </view>
          </view>
          <view v-for="t in tiers" :key="t.id" class="coupon-row" @click="buyTier(t)">
            <view class="coupon-amount">
              <text class="ca-pay">¥{{ money(t.payAmount) }}</text>
              <text class="ca-value">抵 ¥{{ money(t.valueAmount) }}</text>
            </view>
            <view style="flex:1;min-width:0">
              <view class="coupon-name">{{ t.title }}</view>
              <view class="coupon-desc">{{ t.description }}</view>
            </view>
            <view class="coupon-buy">购买</view>
          </view>
          <view v-if="!tiers.length" class="coupon-empty">团购券即将上线，敬请期待</view>
        </view>

        <view v-if="myCoupons.length" class="my-coupon">
          <view class="my-coupon-t">我的券包 · {{ myCoupons.length }} 张</view>
          <view v-for="c in myCoupons.slice(0, 2)" :key="c.id" class="my-coupon-row">
            <image v-if="c.qrUrl" :src="c.qrUrl" class="coupon-qr" mode="aspectFit" />
            <view style="flex:1;min-width:0">
              <view class="my-coupon-name">{{ c.title }}</view>
              <view class="my-coupon-code">核销码 {{ c.code }}</view>
              <view class="my-coupon-tip">到店出示二维码，由门店核销</view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="result-bar">
      <view class="rb-act" @click="saveImage">
        <fj-icon name="download" :size="20" color="#4E575A" />
        <text>保存</text>
      </view>
      <view class="rb-act" @click="ui.openSheet('share')">
        <fj-icon name="share" :size="20" color="#4E575A" />
        <text>分享</text>
      </view>
      <view class="rb-primary" @click="onPrimary">
        <text class="rb-t">{{ primaryTitle }}</text>
        <text class="rb-s">{{ primarySub }}</text>
      </view>
    </view>

    <fj-sheet />

    <!-- #ifdef MP-WEIXIN -->
    <canvas canvas-id="fajing-wm" class="wm-canvas" />
    <!-- #endif -->
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onReady, onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { saveRemoteImage } from '@/utils/save'
import { track } from '@/utils/track'

const app = useAppStore()
const ui = useUiStore()

const sliderRef = ref(null)
const baPct = ref(50)
const retrying = ref(false)
const activeIndex = ref(0)
const tiers = ref([])
const myCoupons = ref([])
const buying = ref(false)

const styles = computed(() => (app.result && app.result.styles) || [])
const taskId = computed(() => String((app.result && app.result.taskId) || app.taskId || ''))
const current = computed(
  () => styles.value[activeIndex.value] || { styleId: '', name: '', len: '', face: '', img: '' }
)
const beforeImg = computed(() => (app.result && app.result.before) || app.photoPath || '')
const faceShape = computed(() => (app.result && app.result.faceShape) || '鹅蛋脸')
const skinTone = computed(() => (app.result && app.result.skinTone) || '中性皮')

const score = computed(() => {
  const value = Number(current.value.score || (app.result && app.result.score))
  return Number.isFinite(value) && value > 0 ? Math.min(100, Math.round(value)) : 92
})

const fit = computed(() => {
  const lv = score.value >= 95 ? '极高' : score.value >= 90 ? '很高' : score.value >= 85 ? '高' : '中'
  const reasons = [current.value.reason, current.value.colorReason].filter(Boolean)
  const desc = reasons.length
    ? reasons.join(' · ')
    : `${faceShape.value} · ${skinTone.value} · 轮廓协调、发流自然`
  return {
    score: score.value,
    lv,
    desc,
    tags: [current.value.cat || '热门款', current.value.colorName || '原生黑发', '好打理']
  }
})

const ringStyle = computed(() => {
  const p = Math.max(0, Math.min(100, fit.value.score))
  return `background: conic-gradient(from -90deg, #C95676 0% ${p}%, #F1DDE2 ${p}% 100%)`
})

// 原型 SVG 适配环：r=23，周长 2πr ≈ 144.5
const fitDash = computed(() => 144.5 - 144.5 * (Math.max(0, Math.min(100, fit.value.score)) / 100))

const lockedTitle = computed(() =>
  current.value.colorName ? `解锁「${current.value.colorName}」发色效果` : '解锁这张高清效果图'
)
const primaryTitle = computed(() => {
  if (isPending(current.value)) return '解锁这款发型'
  if (isFailed(current.value)) return '重新生成'
  return '保存高清图'
})
const primarySub = computed(() => {
  if (isPending(current.value)) return '单款 10 魅力值 · 解锁即生成'
  if (isFailed(current.value)) return '生成失败可免费重试'
  return '高清无水印 · 可直接保存'
})

function isFailed(item) {
  return !!item && !isPending(item) && (item.failed === true || !item.img)
}

function isPending(item) {
  return !!item && item.pending === true
}

function measure() {
  if (sliderRef.value) sliderRef.value.measure()
}

function onSliderDrag() {
  if (sliderRef.value) sliderRef.value.stop()
}

function replayCompare() {
  if (sliderRef.value) sliderRef.value.replay()
}

async function saveImage() {
  const item = current.value
  if (!item || !item.img) {
    ui.toast('暂无可保存的效果图')
    return
  }
  try {
    await saveRemoteImage(item.img, false)
    ui.toast('已保存高清无水印图')
    track('save_image', { taskId: taskId.value, styleId: item.styleId })
  } catch (e) {
    ui.toast('保存失败，请检查相册权限')
  }
}

function onPrimary() {
  if (isPending(current.value)) {
    unlockCurrent()
    return
  }
  if (isFailed(current.value)) {
    retryCurrent()
    return
  }
  saveImage()
}

function needRecharge() {
  uni.showModal({
    title: '魅力值不足',
    content: '解锁单款需要 10 点魅力值，去充值或看广告获取？',
    confirmText: '去充值',
    success: (res) => {
      if (res.confirm) goPoints()
    }
  })
}

async function unlockCurrent() {
  const item = current.value
  if (!item || !item.styleId || !taskId.value) {
    ui.toast('任务已过期，请重新生成')
    return
  }
  if (isPending(current.value)) {
    const hasAd = Number(app.quota.adCredits || 0) > 0
    if (!app.quota.unlimited && !hasAd && Number(app.quota.points || 0) < 10) {
      needRecharge()
      return
    }
  }
  uni.showModal({
    title: '解锁这款发型',
    content: `将消耗 10 点魅力值，当前剩余 ${app.quota.points || 0} 点`,
    confirmText: '立即解锁',
    success: async (res) => {
      if (!res.confirm) return
      uni.showLoading({ title: '解锁中…', mask: true })
      try {
        const data = await API.unlock({ taskId: taskId.value, styleId: item.styleId })
        app.setQuota(data)
        const ok = await pollUntilImage(item.styleId)
        uni.hideLoading()
        if (ok) ui.toast('解锁成功')
      } catch (e) {
        uni.hideLoading()
        if (String((e && e.message) || '').indexOf('魅力值不足') > -1) needRecharge()
      }
    }
  })
}

async function pollUntilImage(styleId) {
  for (let i = 0; i < 50; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1500))
    try {
      const res = await API.task(taskId.value)
      const data = res && (res.result || res)
      if (data && data.styles) {
        app.setResult({ ...data, taskId: taskId.value })
        const target = data.styles.find((s) => s.styleId === styleId)
        if (target && target.img) return true
      }
    } catch (e) {
      /* 继续轮询 */
    }
  }
  return false
}

async function retryCurrent() {
  const item = current.value
  if (!item || !item.styleId || retrying.value) return
  retrying.value = true
  try {
    await API.retryStyle(taskId.value, item.styleId)
    ui.toast('已重新提交生成')
    await pollUntilImage(item.styleId)
  } catch (e) {
    /* request 已统一提示 */
  } finally {
    retrying.value = false
  }
}

function openCard() {
  const item = current.value
  if (isFailed(item) || isPending(item)) {
    ui.toast('当前款式还没有生成成功')
    return
  }
  const styleId = item && item.styleId
  uni.navigateTo({
    url: '/pages/card/card' + (styleId ? `?styleId=${encodeURIComponent(styleId)}` : '')
  })
}

function again() {
  uni.redirectTo({ url: '/pages/recommend/recommend' })
}

function goPoints() {
  uni.navigateTo({ url: '/pages/points/points' })
}

function goCoupons() {
  uni.navigateTo({ url: '/pages/coupons/coupons' })
}

function goBack() {
  // 选完发型后返回应回到「选发型/发色」页，而不是直接回首页
  uni.navigateBack({
    delta: 1,
    fail: () => uni.redirectTo({ url: '/pages/recommend/recommend' })
  })
}

function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(2)) : '0'
}

function currentPlatform() {
  let platform = 'h5'
  // #ifdef MP-WEIXIN
  platform = 'mp-weixin'
  // #endif
  // #ifdef MP-TOUTIAO
  platform = 'mp-toutiao'
  // #endif
  return platform
}

async function loadTiers() {
  try {
    const list = await API.couponTiers()
    tiers.value = Array.isArray(list) ? list : []
  } catch (e) {
    tiers.value = []
  }
}

async function loadMyCoupons() {
  try {
    const data = await API.myCoupons()
    if (data) {
      myCoupons.value = (data.list || []).filter((c) => c.status === 'unused')
      app.setQuota({ couponCount: typeof data.count === 'number' ? data.count : myCoupons.value.length })
    }
  } catch (e) {
    /* 券包加载失败不影响主流程 */
  }
}

/** 购买团购券：服务端按档位计价，支付成功后自动生成核销二维码 */
async function buyTier(tier) {
  if (buying.value) return
  uni.showModal({
    title: tier.title,
    content: `支付 ¥${money(tier.payAmount)}，到店可抵 ¥${money(tier.valueAmount)} 使用`,
    confirmText: '立即购买',
    success: async (res) => {
      if (!res.confirm) return
      buying.value = true
      uni.showLoading({ title: '下单中…', mask: true })
      try {
        const data = await API.createOrder({
          bizType: 'coupon',
          tierId: tier.id,
          platform: currentPlatform()
        })
        uni.hideLoading()
        if (data && (data.mock || data.paid)) {
          await loadMyCoupons()
          ui.toast('购买成功，核销二维码已生成')
          track('coupon_buy', { tierId: tier.id, amount: data.amount })
          return
        }
        // #ifdef H5
        if (data && data.payForm) {
          document.write(data.payForm)
          document.close()
          return
        }
        // #endif
        // #ifdef MP-WEIXIN
        if (data && data.virtualPay) {
          await requestVirtualPay(data.virtualPay)
          await API.virtualConfirm(data.orderId)
          await loadMyCoupons()
          ui.toast('购买成功，核销二维码已生成')
          return
        }
        if (data && data.payParams) {
          await requestWxPay(data.payParams)
          // 回调可能延迟，先主动查单兜底一次
          await API.syncOrder(data.orderId).catch(() => null)
          for (let i = 0; i < 8; i++) {
            await new Promise((resolve) => setTimeout(resolve, 1500))
            const st = await API.orderStatus(data.orderId)
            if (st && st.paid) break
          }
          await loadMyCoupons()
          ui.toast('购买成功，核销二维码已生成')
          return
        }
        // #endif
        ui.toast('支付未完成，请稍后重试')
      } catch (e) {
        uni.hideLoading()
        ui.toast('支付失败，请稍后重试')
      } finally {
        buying.value = false
      }
    }
  })
}

// #ifdef MP-WEIXIN
function requestVirtualPay(payload) {
  return new Promise((resolve, reject) => {
    // eslint-disable-next-line no-undef
    wx.requestVirtualPayment({
      signData: payload.signData,
      paySig: payload.paySig,
      signature: payload.signature,
      mode: payload.mode,
      success: resolve,
      fail: (err) => reject(new Error((err && err.errMsg) || '支付失败'))
    })
  })
}
// #endif

// #ifndef H5
function requestWxPay(payParams) {
  return new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      ...payParams,
      success: resolve,
      fail: (err) => reject(new Error((err && err.errMsg) || '支付失败'))
    })
  })
}
// #endif

onLoad((options) => {
  const styleId = options && options.styleId ? decodeURIComponent(options.styleId) : ''
  if (styleId) {
    const index = styles.value.findIndex((s) => s.styleId === styleId)
    if (index > -1) activeIndex.value = index
  }
  if (!app.result || !styles.value.length) {
    ui.toast('效果已过期，请重新生成')
    uni.reLaunch({ url: '/pages/home/home' })
    return
  }
  track('result_view', { taskId: taskId.value, styleId })
})

onReady(() => {
  measure()
})

onShow(() => {
  measure()
  loadTiers()
  loadMyCoupons()
  API.quota()
    .then((quota) => app.setQuota(quota))
    .catch(() => {})
})
</script>

<style lang="scss" scoped>
.pts-pill {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 99px;
  background: #fff6e8;
  color: #a9701b;
  font-size: 12px;
  font-weight: 700;
}
.meta-main {
  flex: 1;
  min-width: 0;
}
.meta-color {
  color: var(--pink-700);
}
.wm-canvas {
  position: fixed;
  left: -2000px;
  top: 0;
  width: 750px;
  height: 1000px;
  pointer-events: none;
}
.fit-ring {
  position: absolute;
  inset: 0;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1dde2;
}
.fit-ring-in {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #fffaf7;
  display: flex;
  align-items: center;
  justify-content: center;
}
.res-state {
  height: 380px;
  border-radius: 18px;
  background: #ececee;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 24px;
  text-align: center;
}
.res-state-t {
  font-size: 13px;
  font-weight: 700;
  color: #6f7679;
}
.res-state-d {
  font-size: 11px;
  color: #a3a9ac;
  line-height: 1.6;
}
.state-btn {
  margin-top: 8px;
  padding: 9px 22px;
  font-size: 12px;
}
.coupon-sec {
  margin-top: 18px;
  background: #fff;
  border-radius: 18px;
  padding: 14px;
  box-shadow: var(--sh-sm);
}
.coupon-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
}
.coupon-title {
  font-size: 14px;
  font-weight: 800;
}
.coupon-sub {
  font-size: 10px;
  color: var(--muted);
  margin-top: 3px;
}
.coupon-mine {
  font-size: 10.5px;
  font-weight: 700;
  color: #b54263;
  background: var(--pink-50);
  padding: 4px 9px;
  border-radius: 99px;
  flex: none;
}
.coupon-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 0;
  border-top: 1px solid #f7f0f2;
}
.coupon-amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 72px;
  padding: 7px 4px;
  border-radius: 12px;
  background: linear-gradient(135deg, #fdeef1, #fff6e8);
  flex: none;
}
.ca-pay {
  font-size: 14px;
  font-weight: 800;
  color: #b54263;
}
.ca-value {
  font-size: 9.5px;
  color: #a9701b;
  margin-top: 2px;
}
.coupon-name {
  font-size: 12px;
  font-weight: 700;
}
.coupon-desc {
  font-size: 9.5px;
  color: var(--muted);
  margin-top: 3px;
  line-height: 1.5;
}
.coupon-buy {
  flex: none;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d98a63, #c95676);
  padding: 6px 13px;
  border-radius: 99px;
}
.coupon-empty {
  font-size: 10.5px;
  color: var(--muted);
  padding: 7px 0;
}
.my-coupon {
  margin-top: 14px;
  background: #fff;
  border-radius: 18px;
  padding: 14px;
  box-shadow: var(--sh-sm);
}
.my-coupon-t {
  font-size: 12.5px;
  font-weight: 800;
  margin-bottom: 9px;
}
.my-coupon-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-top: 1px solid #f7f0f2;
}
.coupon-qr {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  background: #fff;
  flex: none;
}
.my-coupon-name {
  font-size: 12px;
  font-weight: 700;
}
.my-coupon-code {
  font-size: 10px;
  color: #b54263;
  margin-top: 3px;
  letter-spacing: 0.4px;
}
.my-coupon-tip {
  font-size: 9.5px;
  color: var(--muted);
  margin-top: 2px;
}
</style>
