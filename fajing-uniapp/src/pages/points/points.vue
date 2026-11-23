<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">充值与权益</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view class="balance-card">
          <view class="bc-label">我的魅力值</view>
          <view class="bc-num">
            {{ app.quota.unlimited ? '不限量' : (app.quota.points || 0) }}
            <text v-if="!app.quota.unlimited" class="bc-unit">点</text>
          </view>
          <view class="bc-sub">
            <text v-if="app.quota.vvip">VVIP 永久会员 · 解锁不限量</text>
            <text v-else-if="app.quota.unlimited">VIP 会员 · 解锁不限量</text>
            <text v-else>解锁 1 款发型 / 发色消耗 10 点 · 新用户赠 50 点</text>
          </view>
          <view v-if="Number(app.quota.adCredits || 0) > 0" class="bc-sub">
            另有 {{ app.quota.adCredits }} 次广告获得的免费解锁次数
          </view>
        </view>

        <view class="sec-title-row">
          <text class="sec-title">充值套餐</text>
          <text class="sec-hint">一次购买，长期可用</text>
        </view>
        <view class="pkg-grid">
          <view v-for="p in packages" :key="p.key" class="pkg" @click="buy(p)">
            <view class="pkg-badge" v-if="p.badge">{{ p.badge }}</view>
            <view class="pkg-main">{{ p.main }}</view>
            <view class="pkg-desc">{{ p.desc }}</view>
            <view class="pkg-price">¥{{ p.price }}</view>
          </view>
        </view>

        <view class="sec-title-row" style="margin-top:20px">
          <text class="sec-title">免费获取魅力值</text>
        </view>
        <view class="earn-row" @click="watchAd">
          <view class="earn-ico"><fj-icon name="sparkles" :size="16" color="#C95676" /></view>
          <view style="flex:1">
            <view class="earn-t">看激励广告</view>
            <view class="earn-d">完整观看 1 次广告，获得 1 次免费解锁</view>
          </view>
          <view class="earn-btn">剩余 {{ app.quota.adLeft == null ? 5 : app.quota.adLeft }} 次</view>
        </view>
        <view class="earn-row" @click="claimShare">
          <view class="earn-ico"><fj-icon name="share" :size="16" color="#C95676" /></view>
          <view style="flex:1">
            <view class="earn-t">分享给好友</view>
            <view class="earn-d">每账号限 1 次，分享成功赠 10 魅力值</view>
          </view>
          <view class="earn-btn" :class="{ done: app.quota.shareClaimed }">
            {{ app.quota.shareClaimed ? '已领取' : '立即分享' }}
          </view>
        </view>

        <view class="rule-note">
          <view class="rule-t">权益说明</view>
          <view class="rule-d">1. 新用户注册即赠 50 点魅力值；</view>
          <view class="rule-d">2. 前 3 次生成中，每次前 3 款发型 / 发色免费；第 4 款起用魅力值解锁；</view>
          <view class="rule-d">3. 免费次数用完后，第 1 款起每款消耗 10 点魅力值；</view>
          <view class="rule-d">4. VIP 30 天内、VVIP 永久不限量解锁；</view>
          <view class="rule-d">5. 激励广告每日最多 5 次，每次获得 1 次免费解锁。</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { track } from '@/utils/track'
import { goBack } from '@/utils/nav'

const app = useAppStore()
const ui = useUiStore()

const busy = ref(false)

const packages = ref(defaultPackages())

/** 接口不可用时的兜底套餐，保证充值页不空白 */
function defaultPackages() {
  return [
    { key: 'p1', price: 10, main: '100 魅力值', desc: '约 10 款发型解锁', bizType: 'points', points: 100 },
    { key: 'p2', price: 20, main: '200 魅力值', desc: '约 20 款发型解锁', badge: '多买多省', bizType: 'points', points: 200 },
    { key: 'p3', price: 29, main: 'VIP 包月', desc: '30 天内解锁不限量', badge: '推荐', bizType: 'vip' },
    { key: 'p4', price: 99, main: 'VVIP 永久', desc: '永久不限量解锁', bizType: 'vvip' }
  ]
}

/** 套餐价格与权益由后台下发，下单金额仍以服务端为准 */
async function loadPackages() {
  try {
    const rows = await API.pointsPackages()
    if (Array.isArray(rows) && rows.length) {
      packages.value = rows.map((row) => ({
        key: 'pkg' + row.id,
        id: row.id,
        price: Number(row.price),
        main: row.title,
        desc: row.description,
        badge: row.badge || '',
        bizType: row.bizType,
        points: row.points || 0
      }))
    }
  } catch (e) {
    // 保留兜底套餐
  }
}

loadPackages()

function back() {
  goBack()
}

async function refresh() {
  try {
    app.setQuota(await API.quota())
  } catch (e) {
    /* 同步失败沿用本地值 */
  }
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

async function buy(pkg) {
  if (busy.value) return
  uni.showModal({
    title: pkg.main,
    content: `支付 ¥${pkg.price}，${pkg.desc}`,
    confirmText: '立即支付',
    success: async (res) => {
      if (!res.confirm) return
      busy.value = true
      uni.showLoading({ title: '下单中…', mask: true })
      try {
        const data = await API.createOrder({
          bizType: pkg.bizType,
          packageId: pkg.id,
          platform: currentPlatform()
        })
        uni.hideLoading()
        if (data && (data.mock || data.paid)) {
          await refresh()
          ui.toast('购买成功')
          track('points_buy', { key: pkg.key, amount: data.amount })
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
          uni.setStorageSync('fajing_pending_virtual_order', {
            orderId: data.orderId,
            plan: pkg.key
          })
          await requestVirtualPay(data.virtualPay)
          for (let i = 0; i < 8; i++) {
            const st = await API.virtualConfirm(data.orderId).catch(() => null)
            if (st && st.paid) break
            await new Promise((resolve) => setTimeout(resolve, 1200))
          }
          uni.removeStorageSync('fajing_pending_virtual_order')
          await refresh()
          ui.toast('购买成功')
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
          await refresh()
          ui.toast('购买成功')
          return
        }
        // #endif
        ui.toast('支付未完成，请稍后重试')
      } catch (e) {
        uni.hideLoading()
        ui.toast('支付失败，请稍后重试')
      } finally {
        busy.value = false
      }
    }
  })
}

async function rewardAd() {
  try {
    const data = await API.adReward()
    app.setQuota(data)
    ui.toast('已获得 1 次免费解锁')
    track('ad_reward', { reward: data && data.reward })
  } catch (e) {
    /* request 已提示 */
  }
}

function watchAd() {
  // #ifdef MP-WEIXIN
  const adUnitId = (import.meta.env && import.meta.env.VITE_REWARDED_AD_UNIT_ID) || ''
  if (adUnitId && typeof wx !== 'undefined' && typeof wx.createRewardedVideoAd === 'function') {
    const ad = wx.createRewardedVideoAd({ adUnitId })
    ad.onClose((res) => {
      if (!res || res.isEnded) rewardAd()
    })
    ad.onError(() => ui.toast('广告加载失败，请稍后再试'))
    ad.show().catch(() => ad.load().then(() => ad.show()))
    return
  }
  // #endif
  // 未配置广告位时（本地联调）用确认弹窗模拟完整观看
  uni.showModal({
    title: '激励广告',
    content: '完整观看 1 次广告可获得 1 次免费解锁',
    confirmText: '观看完成',
    success: (res) => {
      if (res.confirm) rewardAd()
    }
  })
}

async function claimShare() {
  if (app.quota.shareClaimed) {
    ui.toast('分享奖励每账号限 1 次')
    return
  }
  try {
    app.setQuota(await API.shareReward())
    ui.toast('已获得 10 点魅力值')
  } catch (e) {
    if (String((e && e.message) || '').indexOf('已领取') > -1) {
      app.setQuota({ shareClaimed: true })
    }
  }
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

onShow(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.balance-card {
  border-radius: 22px;
  padding: 18px;
  color: #fff;
  background: linear-gradient(135deg, #3a2f34, #57414a 60%, #6d4a52);
  box-shadow: 0 10px 26px rgba(58, 47, 52, 0.22);
}
.bc-label {
  font-size: 11px;
  opacity: 0.75;
}
.bc-num {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.5px;
  margin-top: 4px;
}
.bc-unit {
  font-size: 12px;
  font-weight: 600;
  margin-left: 3px;
  opacity: 0.8;
}
.bc-sub {
  font-size: 10.5px;
  opacity: 0.72;
  margin-top: 4px;
  line-height: 1.6;
}
.sec-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 20px 2px 10px;
}
.sec-title {
  font-size: 14px;
  font-weight: 800;
}
.sec-hint {
  font-size: 10px;
  color: var(--muted);
}
.pkg-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.pkg {
  position: relative;
  background: #fff;
  border-radius: 18px;
  padding: 15px 14px;
  box-shadow: var(--sh-sm);
  border: 1.5px solid #f7eef1;
}
.pkg-badge {
  position: absolute;
  top: 0;
  right: 0;
  font-size: 9px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d98a63, #c95676);
  padding: 3px 9px;
  border-radius: 0 16px 0 12px;
}
.pkg-main {
  font-size: 14.5px;
  font-weight: 800;
}
.pkg-desc {
  font-size: 10px;
  color: var(--muted);
  margin-top: 4px;
}
.pkg-price {
  margin-top: 12px;
  font-size: 18px;
  font-weight: 800;
  color: #b54263;
}
.earn-row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 16px;
  padding: 13px 14px;
  box-shadow: var(--sh-sm);
  margin-bottom: 10px;
}
.earn-ico {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: var(--pink-50);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.earn-t {
  font-size: 12.5px;
  font-weight: 700;
}
.earn-d {
  font-size: 10px;
  color: var(--muted);
  margin-top: 2px;
  line-height: 1.5;
}
.earn-btn {
  flex: none;
  font-size: 10.5px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d98a63, #c95676);
  padding: 6px 12px;
  border-radius: 99px;
}
.earn-btn.done {
  background: #eadfe3;
  color: #b7a8af;
}
.rule-note {
  margin: 22px 2px 16px;
  padding: 14px;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--sh-sm);
}
.rule-t {
  font-size: 12px;
  font-weight: 800;
  margin-bottom: 6px;
}
.rule-d {
  font-size: 10px;
  color: var(--muted);
  line-height: 1.75;
}
</style>
