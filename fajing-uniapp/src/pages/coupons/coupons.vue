<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">我的券包</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view class="coupon-hero">
          <view class="ch-label">可用团购券</view>
          <view class="ch-num">{{ unused.length }}<text class="ch-unit">张</text></view>
          <view class="ch-sub">到店出示核销二维码，由门店扫码核销</view>
        </view>

        <view class="seg coupon-seg">
          <view class="seg-item" :class="{ on: filter === 'unused' }" @click="filter = 'unused'">可用</view>
          <view class="seg-item" :class="{ on: filter === 'used' }" @click="filter = 'used'">已使用</view>
          <view class="seg-item" :class="{ on: filter === 'all' }" @click="filter = 'all'">全部</view>
        </view>

        <view v-if="!filtered.length" class="coupon-empty">
          <fj-icon name="crown" :size="26" color="#D8B4BF" />
          <text class="ce-t">暂无团购券</text>
          <text class="ce-d">在「你的新发型」结果页可购买周边门店优惠券</text>
        </view>

        <view v-for="c in filtered" :key="c.id" class="coupon-item" :class="{ used: c.status !== 'unused' }">
          <image v-if="c.qrUrl && c.status === 'unused'" :src="c.qrUrl" class="coupon-qr" mode="aspectFit" />
          <view v-else class="coupon-qr coupon-qr-empty">
            <fj-icon :name="c.status === 'used' ? 'check-circle' : 'refresh'" :size="22" color="#B7A8AF" />
          </view>
          <view style="flex:1;min-width:0">
            <view class="coupon-name">{{ c.title }}</view>
            <view class="coupon-amount">
              实付 ¥{{ money(c.payAmount) }} · 可抵 ¥{{ money(c.valueAmount) }}
            </view>
            <view class="coupon-code">核销码 {{ c.code }}</view>
            <view class="coupon-tip">{{ tipOf(c) }}</view>
          </view>
          <view class="coupon-side">
            <view class="coupon-status" :class="statusClass(c)">{{ statusText(c) }}</view>
            <view v-if="c.status === 'unused'" class="coupon-refund" @click.stop="askRefund(c)">申请退款</view>
          </view>
        </view>

        <view class="coupon-note">团购券有效期 90 天，仅限指定合作门店使用；未核销的券可在售后期限内申请退款。</view>
      </view>
    </scroll-view>

    <!-- 退款确认弹窗 -->
    <view v-if="refundDlg.show" class="dlg-mask" @click="closeRefund">
      <view class="dlg" @click.stop>
        <view class="dlg-ico"><fj-icon name="crown" :size="22" color="#C68A25" /></view>
        <view class="dlg-title">申请退款</view>
        <view class="dlg-desc">
          退款后该团购券立即失效，¥{{ money(refundDlg.coupon && refundDlg.coupon.payAmount) }} 将原路退回。
        </view>
        <view class="dlg-actions">
          <view class="dlg-btn ghost" @click="closeRefund">再想想</view>
          <view class="dlg-btn primary" @click="confirmRefund">{{ refunding ? '退款中…' : '确认退款' }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { API } from '@/api/request'
import { goBack } from '@/utils/nav'

const app = useAppStore()

const coupons = ref([])
const filter = ref('unused')
const refundDlg = ref({ show: false, coupon: null })
const refunding = ref(false)

const unused = computed(() => coupons.value.filter((c) => c.status === 'unused'))
const filtered = computed(() => {
  if (filter.value === 'all') return coupons.value
  return coupons.value.filter((c) => c.status === filter.value)
})

function back() {
  goBack()
}

function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(2)) : '0'
}

function statusText(c) {
  if (!c) return ''
  if (c.status === 'unused') return '可用'
  if (c.status === 'used') return '已使用'
  if (c.status === 'refunded') return '已退款'
  if (c.status === 'refunding') return '退款中'
  return '已过期'
}

function statusClass(c) {
  return {
    off: c.status !== 'unused',
    refunded: c.status === 'refunded' || c.status === 'refunding'
  }
}

function tipOf(c) {
  if (c.status === 'unused') return '到店出示二维码，由门店核销'
  if (c.status === 'used') return '已核销，不可重复使用'
  if (c.status === 'refunded') return '已退款，款项原路退回'
  if (c.status === 'refunding') return '退款处理中，请留意到账通知'
  return '已过期'
}

function askRefund(c) {
  refundDlg.value = { show: true, coupon: c }
}

function closeRefund() {
  if (refunding.value) return
  refundDlg.value = { show: false, coupon: null }
}

async function confirmRefund() {
  const coupon = refundDlg.value.coupon
  if (!coupon || refunding.value) return
  refunding.value = true
  try {
    const data = await API.refundCoupon(coupon.id)
    refundDlg.value = { show: false, coupon: null }
    uni.showToast({
      title: data && data.alreadyRefunded ? '订单已退款，券已作废' : '退款成功，款项原路退回',
      icon: 'none',
      duration: 2200
    })
    await refresh()
  } catch (e) {
    // request 已统一提示
  } finally {
    refunding.value = false
  }
}

async function refresh() {
  try {
    const data = await API.myCoupons()
    coupons.value = (data && data.list) || []
    app.setQuota({ couponCount: typeof (data && data.count) === 'number' ? data.count : unused.value.length })
  } catch (e) {
    // request 已统一提示
  }
}
onShow(() => {
  refresh()
})
</script>

<style lang="scss" scoped>
.coupon-hero {
  border-radius: 22px;
  padding: 18px;
  color: #fff;
  background: linear-gradient(135deg, #3a2f34, #57414a 60%, #6d4a52);
  box-shadow: 0 10px 26px rgba(58, 47, 52, 0.22);
}
.ch-label {
  font-size: 11px;
  opacity: 0.75;
}
.ch-num {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.5px;
  margin-top: 4px;
}
.ch-unit {
  font-size: 12px;
  font-weight: 600;
  margin-left: 3px;
  opacity: 0.8;
}
.ch-sub {
  font-size: 10.5px;
  opacity: 0.72;
  margin-top: 4px;
}
.coupon-seg {
  margin: 16px 0 14px;
}
.coupon-empty {
  padding: 60px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}
.ce-t {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-2);
}
.ce-d {
  font-size: 10.5px;
  color: var(--muted);
  line-height: 1.6;
}
.coupon-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 16px;
  padding: 12px 14px;
  box-shadow: var(--sh-sm);
  margin-bottom: 10px;
}
.coupon-item.used {
  opacity: 0.72;
}
.coupon-qr {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  flex: none;
  background: #fff;
}
.coupon-qr-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4eef0;
}
.coupon-name {
  font-size: 12.5px;
  font-weight: 800;
}
.coupon-amount {
  font-size: 10px;
  color: var(--pink-700);
  margin-top: 3px;
}
.coupon-code {
  font-size: 10px;
  color: #8b7f85;
  margin-top: 2px;
  letter-spacing: 0.3px;
}
.coupon-tip {
  font-size: 9.5px;
  color: var(--muted);
  margin-top: 2px;
}
.coupon-status {
  flex: none;
  font-size: 10px;
  font-weight: 700;
  color: #3f9e6b;
}
.coupon-status.off {
  color: #b7a8af;
}
.coupon-status.refunded {
  color: #c68a25;
}
.coupon-side {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.coupon-refund {
  font-size: 9.5px;
  font-weight: 700;
  color: #a9701b;
  background: #fff6e8;
  border: 1px solid #f6e3c8;
  border-radius: 99px;
  padding: 4px 9px;
}
.dlg-mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(30, 22, 26, 0.46);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 40px;
}
.dlg {
  width: 100%;
  max-width: 310px;
  background: #fff;
  border-radius: 22px;
  padding: 22px 20px 18px;
  text-align: center;
  box-shadow: 0 24px 60px rgba(46, 26, 34, 0.28);
}
.dlg-ico {
  width: 46px;
  height: 46px;
  margin: 0 auto 12px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fdeef1, #fff6e8);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dlg-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}
.dlg-desc {
  margin-top: 8px;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--muted);
}
.dlg-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.dlg-btn {
  flex: 1;
  height: 40px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  font-weight: 700;
}
.dlg-btn.ghost {
  background: #f6eef0;
  color: #7b6b71;
}
.dlg-btn.primary {
  color: #fff;
  background: linear-gradient(135deg, #d98a63, #c95676);
  box-shadow: 0 8px 18px rgba(201, 86, 118, 0.28);
}
.coupon-note {
  margin: 18px 4px 16px;
  font-size: 9.5px;
  color: #b7a8af;
  line-height: 1.7;
  text-align: center;
}
</style>
