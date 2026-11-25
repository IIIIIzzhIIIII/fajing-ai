<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="18" /></view>
      <view class="topbar-title">{{ store ? store.name : '合作门店' }}</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view v-if="!store" class="nearby-list">
          <view class="section-title">附近合作门店</view>
          <view v-for="item in nearby" :key="item.id" class="nearby-item" @click="openStore(item.id)">
            <view class="ni-icon"><fj-icon name="scissors" :size="18" color="#C95676" /></view>
            <view class="ni-main">
              <view class="ni-name">{{ item.name }}</view>
              <view class="ni-addr">{{ item.address || '到店咨询' }}</view>
              <view class="ni-meta">{{ item.businessHours || '营业时间以门店为准' }} · {{ item.couponCount || 0 }} 张券</view>
            </view>
            <view class="ni-price" v-if="item.minPayAmount">¥{{ money(item.minPayAmount) }}起</view>
          </view>
          <view v-if="!nearby.length" class="empty">附近暂无合作门店</view>
        </view>

        <block v-else>
          <view class="store-hero">
            <view class="sh-brand">{{ store.brandName || store.name }}</view>
            <view class="sh-name">{{ store.name }}</view>
            <view class="sh-line">{{ store.address || '地址以门店为准' }}</view>
            <view class="sh-line">{{ store.businessHours || '营业时间以门店为准' }} · {{ store.phone || '电话以门店为准' }}</view>
          </view>

          <view class="section-head">
            <view class="section-title">门店专享券</view>
            <view class="section-sub">仅限本店核销</view>
          </view>

          <view v-for="tier in tiers" :key="tier.id" class="tier-card">
            <view class="tc-left">
              <view class="tc-pay"><text class="tc-yen">¥</text>{{ money(tier.payAmount) }}</view>
              <view class="tc-value">可抵 ¥{{ money(tier.valueAmount) }}</view>
            </view>
            <view class="tc-main">
              <view class="tc-title">{{ tier.title }}</view>
              <view class="tc-desc">{{ tier.description || '到店出示核销码使用' }}</view>
            </view>
            <view class="tc-buy" :class="{ disabled: payHidden }" @click="buy(tier)">
              {{ payHidden ? 'iOS 暂不支持' : '购买' }}
            </view>
          </view>

          <view v-if="!tiers.length" class="empty">该门店暂未上架团购券</view>

          <view class="my-coupon" @click="openCoupons">
            <fj-icon name="crown" :size="18" color="#C68A25" />
            <text>查看我的券包</text>
            <fj-icon name="chevron-right" :size="15" color="#C9BFC3" />
          </view>

          <view class="store-note">
            购券后到店出示核销码，由门店核销；未核销的券可在有效期内申请退款。平台只提供展示与撮合，服务由门店提供。
          </view>
        </block>
      </view>
    </scroll-view>

    <FjLoginModal v-if="loginVisible" @done="onLoginDone" @cancel="onLoginCancel" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { API } from '@/api/request'
import { goBack } from '@/utils/nav'
import { requestWxPay, isIOSMiniProgram } from '@/utils/pay'
import FjLoginModal from '@/components/FjLoginModal.vue'
import { useLoginGate } from '@/composables/useLoginGate'

const store = ref(null)
const tiers = ref([])
const nearby = ref([])
const buying = ref(false)

/** iOS 小程序不展示团购券购买入口 */
const payHidden = isIOSMiniProgram()

const { visible: loginVisible, ensureLogin, finishLogin, closeLogin } = useLoginGate()

function onLoginDone() {
  finishLogin()
}

function onLoginCancel() {
  closeLogin()
}

function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(2)) : '0'
}

function back() {
  goBack()
}

function openStore(id) {
  loadStore(id)
}

async function loadNearby() {
  try {
    nearby.value = (await API.nearbyStores()) || []
  } catch (e) { /* 已统一提示 */ }
}

async function loadStore(id) {
  try {
    const data = await API.storeDetail(id)
    store.value = data
    tiers.value = (data && data.tiers) || []
  } catch (e) { /* 已统一提示 */ }
}

/** 购券前必须完成登录授权，未授权先弹授权引导 */
function buy(tier) {
  if (payHidden) {
    uni.showToast({ title: 'iOS 端暂不支持购买，请使用安卓或在 H5 中购买', icon: 'none' })
    return
  }
  ensureLogin(() => doBuy(tier))
}

async function doBuy(tier) {
  if (buying.value) return
  buying.value = true
  try {
    const platform = getPlatform()
    const data = await API.createOrder({
      bizType: 'coupon',
      tierId: tier.id,
      platform
    })
    if (data && data.paid) {
      buySuccess()
      return
    }
    // #ifdef MP-WEIXIN
    if (data && data.virtualPay) {
      await requestVirtualPay(data.virtualPay)
      for (let i = 0; i < 8; i++) {
        const st = await API.virtualConfirm(data.orderId).catch(() => null)
        if (st && st.paid) break
        await new Promise((resolve) => setTimeout(resolve, 1200))
      }
      buySuccess()
      return
    }
    if (data && data.payParams) {
      await requestWxPay(data.payParams)
      // 回调可能延迟，先主动查单兜底一次
      await API.syncOrder(data.orderId).catch(() => null)
      for (let i = 0; i < 8; i++) {
        await new Promise((resolve) => setTimeout(resolve, 1500))
        // 回调可能丢失：轮询期间持续主动查单兜底
        await API.syncOrder(data.orderId).catch(() => null)
        const st = await API.orderStatus(data.orderId)
        if (st && st.paid) break
      }
      buySuccess()
      return
    }
    // #endif
    // #ifdef H5
    if (data && data.payParams) {
      await requestWxPay(data.payParams)
      // 回调可能延迟，先主动查单兜底一次
      await API.syncOrder(data.orderId).catch(() => null)
      for (let i = 0; i < 8; i++) {
        await new Promise((resolve) => setTimeout(resolve, 1500))
        // 回调可能丢失：轮询期间持续主动查单兜底
        await API.syncOrder(data.orderId).catch(() => null)
        const st = await API.orderStatus(data.orderId)
        if (st && st.paid) break
      }
      buySuccess()
      return
    }
    if (data && data.payForm) {
      document.write(data.payForm)
      document.close()
      return
    }
    // #endif
    uni.showModal({
      title: '支付未完成',
      content: '支付通道未配置，请联系门店',
      showCancel: false
    })
  } catch (e) {
    // request 已统一提示
  } finally {
    buying.value = false
  }
}

function buySuccess() {
  uni.showToast({ title: '购买成功，已放入券包', icon: 'none', duration: 2000 })
  setTimeout(() => uni.navigateTo({ url: '/pages/coupons/coupons' }), 800)
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

function getPlatform() {
  // #ifdef MP-WEIXIN
  return 'mp-weixin'
  // #endif
  // #ifndef MP-WEIXIN
  return 'h5'
  // #endif
}

function openCoupons() {
  uni.navigateTo({ url: '/pages/coupons/coupons' })
}

onLoad((options) => {
  const storeId = options && options.id ? String(options.id) : ''
  if (storeId) {
    loadStore(storeId)
  } else {
    loadNearby()
  }
})
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 8px;
}
.icon-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 50%;
  box-shadow: var(--sh-sm);
}
.topbar-title {
  font-size: 16px;
  font-weight: 700;
}
.scroll {
  height: calc(100vh - 56px);
}
.pad {
  padding: 8px 20px 32px;
}
.store-hero {
  padding: 20px;
  color: #fff;
  background: linear-gradient(135deg, #3a2f34, #57414a 60%, #6d4a52);
  border-radius: 20px;
  box-shadow: 0 10px 26px rgba(58, 47, 52, 0.2);
}
.sh-brand {
  font-size: 11px;
  opacity: 0.7;
  letter-spacing: 1px;
}
.sh-name {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 800;
}
.sh-line {
  margin-top: 6px;
  font-size: 11.5px;
  opacity: 0.78;
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.section-title {
  font-size: 15px;
  font-weight: 800;
}
.section-sub {
  font-size: 10.5px;
  color: var(--muted);
}
.tier-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
}
.tc-left {
  width: 78px;
  text-align: center;
  padding-right: 10px;
  border-right: 1px dashed #f0e0e5;
}
.tc-pay {
  font-size: 22px;
  font-weight: 800;
  color: #c95676;
}
.tc-yen {
  font-size: 12px;
}
.tc-value {
  margin-top: 2px;
  font-size: 10px;
  color: var(--muted);
}
.tc-main {
  flex: 1;
  min-width: 0;
}
.tc-title {
  font-size: 14px;
  font-weight: 700;
}
.tc-desc {
  margin-top: 4px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--muted);
}
.tc-buy {
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d46a88, #b14a6b);
  border-radius: 999px;
  white-space: nowrap;
}
.tc-buy.disabled {
  background: #e7e1e3;
  color: #9b8f95;
}
.my-coupon {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 14px 16px;
  margin-top: 16px;
  font-size: 13px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-sm);
}
.my-coupon text {
  flex: 1;
}
.store-note {
  margin-top: 16px;
  font-size: 10.5px;
  line-height: 1.8;
  color: var(--muted);
}
.nearby-list {
  padding-top: 8px;
}
.nearby-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
}
.ni-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff4f7;
  border-radius: 12px;
}
.ni-main {
  flex: 1;
  min-width: 0;
}
.ni-name {
  font-size: 14px;
  font-weight: 700;
}
.ni-addr {
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--muted);
}
.ni-meta {
  margin-top: 3px;
  font-size: 10px;
  color: #b0a6ab;
}
.ni-price {
  font-size: 12px;
  font-weight: 700;
  color: #c95676;
}
.empty {
  padding: 50px 0;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
}
</style>
