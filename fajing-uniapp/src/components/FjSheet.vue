<template>
  <view>
    <!-- Toast -->
    <view v-if="ui.toastVisible" class="fj-toast">{{ ui.toastText }}</view>

    <!-- 遮罩 -->
    <view class="mask" :class="{ show: !!ui.sheet }" @click="ui.close()" />

    <!-- 每日额度 -->
    <view class="sheet" :class="{ show: ui.sheet === 'quota' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="quota-ico"><fj-icon name="clock" :size="24" /></view>
      <view class="quota-title">今天的免费次数用完了</view>
      <view class="quota-sub">{{ quotaSub }}</view>
      <button class="btn btn-primary btn-block" :disabled="app.quota.shareClaimed" @click="claimShare">
        <fj-icon name="share" :size="16" :color="app.quota.shareClaimed ? '#B7A8AF' : '#FFFFFF'" />
        {{ app.quota.shareClaimed ? '分享奖励已领过（限 1 次）' : '分享给好友 · 再得 1 次' }}
      </button>
      <button class="btn btn-ghost btn-block" style="margin-top: 9px" @click="watchAd">
        <fj-icon name="eye" :size="16" />看广告 · 再得 1 次
      </button>
      <view class="tiny" style="text-align:center;margin-top:12px">每账号分享奖励限 1 次，广告次数每日重置</view>
    </view>

    <!-- 付费墙 -->
    <view class="sheet" :class="{ show: ui.sheet === 'pay' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view style="display:flex;align-items:flex-start;gap:10px">
        <view class="fee-ico"><fj-icon name="crown" :size="16" color="#C68A25" /></view>
        <view>
          <view class="sheet-title">{{ payTitle }}</view>
          <view class="sheet-sub">去掉右下角水印，导出 2K 高清图，可直接打印给理发师看。</view>
        </view>
      </view>
      <view class="plan" :class="{ on: payPlan === 'one' }" @click="ui.payPlan = 'one'">
        <view class="plan-radio" />
        <view style="flex:1">
          <view class="plan-t">单张解锁</view>
          <view class="plan-d">仅解锁当前这 1 张</view>
        </view>
        <view class="plan-price">¥6<text class="plan-unit">/张</text></view>
      </view>
      <view v-if="allPlanAvailable" class="plan" :class="{ on: payPlan === 'all' }" @click="ui.payPlan = 'all'">
        <view class="plan-radio" />
        <view style="flex:1">
          <view class="plan-t">本次全部 {{ lockedCount }} 款 <text class="plan-tag">限时 5 折</text></view>
          <view class="plan-d">平均 ¥3.0/张 · 推荐</view>
        </view>
        <view class="plan-price"><text class="plan-old">¥{{ lockedCount * 6 }}</text>¥{{ lockedCount * 3 }}<text class="plan-unit">/全部</text></view>
      </view>
      <view class="pay-methods">
        <view class="pay-methods-title">选择支付方式</view>
        <view
          v-for="m in channelOptions"
          :key="m.code"
          class="pay-method"
          :class="{ on: selectedChannel === m.code }"
          @click="selectChannel(m.code)"
        >
          <view class="method-logo" :class="'is-' + m.code">{{ methodLogo(m.code) }}</view>
          <view class="method-name">{{ m.label }}</view>
          <view class="plan-radio" />
        </view>
        <view v-if="!channelOptions.length" class="tiny pay-methods-empty">{{ payMethodHint }}</view>
      </view>
      <button
        v-if="channelOptions.length && lockedCount > 0"
        class="btn btn-primary btn-block"
        style="margin-top:6px"
        :disabled="!selectedChannel || paying"
        @click="payNow"
      >
        {{ paying ? '正在拉起支付…' : `确认支付 ¥${payAmount}` }}
      </button>
      <view class="pay-share" @click="claimShare">
        <fj-icon name="share" :size="16" />分享给好友，再得 1 次生成
      </view>
      <view class="tiny" style="text-align:center;margin-top:9px">已有 23,481 人解锁高清 · 好评率 98.6%</view>
    </view>

    <!-- 付费成功 -->
    <view class="sheet" :class="{ show: ui.sheet === 'paid' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="paid-ico"><fj-icon name="check-circle" :size="26" color="#C95676" /></view>
      <view class="quota-title">解锁成功</view>
      <view class="quota-sub">已解锁高清无水印效果图，可保存到相册或发给理发师。</view>
      <view class="paid-thumbs">
        <image v-for="s in unlockedStyles" :key="s.styleId" :src="s.img" mode="aspectFill" class="paid-thumb" />
      </view>
      <button class="btn btn-primary btn-block" @click="saveAll">
        <fj-icon name="download" :size="16" color="#FFFFFF" />保存高清图到相册
      </button>
      <button class="btn btn-ghost btn-block" style="margin-top:9px" @click="ui.sheet = 'share'">
        <fj-icon name="share" :size="16" />分享新发型，问问闺蜜
      </button>
    </view>

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
        {{ app.quota.shareClaimed ? '分享奖励已领过（限 1 次）' : '领取分享奖励 · 得 1 次免费生成' }}
      </button>
      <view class="share-bonus">
        <fj-icon name="gift" :size="13" />每账号限 1 次 · 好友点开也能免费试
      </view>
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
import { computed, ref, watch } from 'vue'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { saveRemoteImage } from '@/utils/save'
import { track } from '@/utils/track'

const app = useAppStore()
const ui = useUiStore()

/** 服务端按运行端下发的支付方式 */
const methods = ref({ channels: [], mock: false })
const selectedChannel = ref('')
/** 下单中禁止重复点击，避免同一批次生成多笔订单 */
const paying = ref(false)

/** 只展示后台已启用且参数完整的支付方式，避免未开通渠道仍可下单 */
const channelOptions = computed(() =>
  (methods.value.channels || []).filter((item) => item.ready)
)
const payMethodHint = computed(() => {
  const first = (methods.value.channels || [])[0]
  return (first && first.reason) || '当前环境暂不支持在线支付，可通过下方分享获得免费次数'
})

function methodLogo(code) {
  const logos = { alipay: '支', wx: '微', douyin: '抖', xhs: '红' }
  return logos[code] || '付'
}

function selectChannel(code) {
  selectedChannel.value = code
  ui.payChannel = code
}

async function loadPayMethods() {
  try {
    const data = await API.payMethods(currentPlatform())
    methods.value = {
      channels: (data && data.channels) || [],
      mock: !!(data && data.mock)
    }
    const options = channelOptions.value
    const preferred = ui.payChannel || selectedChannel.value
    const hit = options.find((item) => item.code === preferred)
    const next = hit ? hit.code : options[0] ? options[0].code : ''
    selectedChannel.value = next
    if (next) ui.payChannel = next
  } catch (e) {
    methods.value = { channels: [], mock: false }
  }
}

watch(
  () => ui.sheet,
  (sheet) => {
    if (sheet === 'pay') loadPayMethods()
  }
)
loadPayMethods()

const currentStyle = computed(() => {
  const list = (app.result && app.result.styles) || []
  const id = app.selectedStyleIds[0]
  return list.find((s) => s.styleId === id) || list[0] || { name: '法式波波头', img: '', before: '' }
})
const beforeImg = computed(() => (app.result && app.result.before) || app.photoPath || '')
/** 解锁按「任务 + 款式」计算，避免旧批次解锁让新一批出图免费 */
const taskKey = computed(() => String((app.result && app.result.taskId) || app.taskId || ''))
const isUnlocked = (styleId) => !!styleId && app.unlocks.includes(`${taskKey.value}:${styleId}`)
/** 生成失败（无图）的款式不能解锁，也不应计入「全部 N 款」的套餐价格 */
const isPayable = (s) => !!s && !!s.styleId && !s.failed && !!s.img && s.unlocked !== true && !isUnlocked(s.styleId)
const payableStyles = computed(() => {
  const list = (app.result && app.result.styles) || []
  return list.filter(isPayable)
})
/** 单张解锁的目标：优先用户点击的那款未解锁内容，其次第一款未解锁内容 */
const payStyle = computed(() => {
  const hit = ui.payStyleId
    ? payableStyles.value.find((s) => s.styleId === ui.payStyleId)
    : null
  return hit || payableStyles.value[0] || null
})
const afterImg = computed(() => currentStyle.value.img || '')
const unlockedStyles = computed(() => {
  const list = (app.result && app.result.styles) || []
  return list.filter((s) => (s.unlocked === true || isUnlocked(s.styleId)) && !!s.img)
})
const lockedCount = computed(() => payableStyles.value.length)
// 只有 1 款待解锁时，"全部"与"单张"重复，直接隐藏全部套餐并按单张计价
const allPlanAvailable = computed(() => lockedCount.value > 1)
const payPlan = computed(() => (allPlanAvailable.value ? ui.payPlan : 'one'))
const payAmount = computed(() => (payPlan.value === 'one' ? 6 : lockedCount.value * 3))
const payTitle = computed(() => (payPlan.value === 'one' ? '解锁高清无水印版' : '解锁本次全部效果'))
const quotaSub = computed(() =>
  app.quota.shareClaimed
    ? '每天 1 次免费生成。分享奖励已使用（每账号限 1 次），可通过看广告再得 1 次。'
    : '每天 1 次免费生成。分享好友（限 1 次）或看广告，可再得 1 次。'
)

async function claimShare() {
  if (app.quota.shareClaimed) {
    ui.toast('分享奖励每账号限领 1 次')
    return
  }
  try {
    // 额度由服务端发放，本地只展示服务端返回值
    app.setQuota(await API.shareReward())
    ui.toast('已获得 1 次免费生成')
    ui.close()
  } catch (e) {
    // 服务端已判重（例如清过本地缓存）时锁定按钮，避免反复点击
    if (String((e && e.message) || '').indexOf('已领取') > -1) {
      app.setQuota({ shareClaimed: true })
    }
  }
}

async function watchAd() {
  if (app.quota.adUsed) {
    ui.toast('今日广告奖励已领取，明天再来')
    return
  }
  try {
    // 广告奖励同样走服务端扣减接口，避免只改本地额度
    app.setQuota(await API.consume('ad'))
    ui.toast('已获得 1 次免费生成')
    ui.close()
  } catch (e) {
    if (String((e && e.message) || '').indexOf('已领取') > -1) {
      app.setQuota({ adUsed: true })
    }
  }
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 小程序调起微信支付 */
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

/** 微信虚拟支付：由服务端签名，客户端拉起收银台 */
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

/** 抖音担保支付：tt.pay 拉起平台收银台 */
function requestDouyinPay(payload) {
  return new Promise((resolve, reject) => {
    // eslint-disable-next-line no-undef
    tt.pay({
      orderInfo: { order_id: payload.orderId, order_token: payload.orderToken },
      service: 5,
      success: (res) => {
        if (res && res.code === 0) {
          resolve(res)
        } else {
          reject(new Error('支付未完成'))
        }
      },
      fail: (err) => reject(new Error((err && err.errMsg) || '支付失败'))
    })
  })
}

async function finishPaid(plan, styleIds, channel) {
  await refreshUnlocks()
  track('pay_success', { plan, count: styleIds.length, channel })
  ui.sheet = 'paid'
}

/** 支付后轮询服务端订单，最多约 12 秒 */
async function pollPaid(orderId) {
  for (let i = 0; i < 8; i++) {
    await wait(1500)
    try {
      const data = await API.orderStatus(orderId)
      if (data && data.paid) return true
    } catch (e) {
      // 继续轮询
    }
  }
  return false
}

function payErrorMessage(err) {
  const msg = String((err && err.message) || '')
  if (msg.includes('cancel')) return '已取消支付'
  return msg && msg.length < 40 ? msg : '支付失败，请稍后重试'
}

/** 当前运行端标识：与后端订单 platform 字段对应 */
function currentPlatform() {
  let platform = 'h5'
  // #ifdef MP-WEIXIN
  platform = 'mp-weixin'
  // #endif
  // #ifdef MP-TOUTIAO
  platform = 'mp-toutiao'
  // #endif
  // #ifdef MP-XHS
  platform = 'mp-xhs'
  // #endif
  return platform
}

/** 按用户选择的支付方式下单：H5 支付宝 / 微信小程序微信支付 */
async function payNow() {
  if (paying.value) return
  // 没有待解锁内容时不发起下单，避免对已解锁/失败款式生成订单
  if (!lockedCount.value) {
    ui.toast('暂无可解锁内容')
    return
  }
  const plan = payPlan.value
  const styleIds = plan === 'one' ? (payStyle.value ? [payStyle.value.styleId] : []) : lockedUnlockedIds()
  if (!styleIds.length || !styleIds[0]) {
    ui.toast('暂无可解锁内容')
    return
  }
  const channel = selectedChannel.value
  if (!channel) {
    ui.toast('请选择支付方式')
    return
  }
  track('pay_click', { plan, count: styleIds.length, channel })
  // 从「试发记录」进入结果页时 app.taskId 为空，任务 ID 只在 result 里，必须优先取 result.taskId
  const payTaskId = Number((app.result && app.result.taskId) || app.taskId || 0)
  paying.value = true
  try {
    const res = await API.createOrder({
      platform: currentPlatform(),
      channel,
      plan,
      taskId: payTaskId,
      styleIds
    })
    if (res && res.mock) {
      await refreshUnlocks()
      track('pay_success', { plan, count: styleIds.length, channel })
      ui.sheet = 'paid'
      return
    }
    // #ifdef MP-WEIXIN
    if (res && res.virtualPay) {
      await requestVirtualPay(res.virtualPay)
      // 客户端成功回调可能丢失，以服务端查单结果为准
      const confirmed = await API.virtualConfirm(res.orderId).catch(() => null)
      if (!(confirmed && confirmed.paid)) {
        const paid = await pollPaid(res.orderId)
        if (!paid) {
          ui.toast('支付结果确认中，请稍后刷新')
          return
        }
      }
      await finishPaid(plan, styleIds, channel)
      return
    }
    // #endif
    // #ifdef MP-TOUTIAO
    if (res && res.douyinPay) {
      await requestDouyinPay(res.douyinPay)
      const paid = await pollPaid(res.orderId)
      if (!paid) {
        ui.toast('支付结果确认中，请稍后刷新')
        return
      }
      await finishPaid(plan, styleIds, channel)
      return
    }
    // #endif
    // #ifdef H5
    if (res && res.payForm) {
      // 跳转支付宝收银台，返回后由结果页 onShow 轮询订单状态
      uni.setStorageSync('fajing_pending_order', { orderId: res.orderId, plan })
      // document.write 会重载整个 H5 单页应用，内存里的结果状态会丢失，
      // 先落一份快照，回跳后结果页可恢复当前批次
      try {
        if (app.result) {
          uni.setStorageSync('fajing_pending_result', JSON.stringify({ result: app.result, taskId: app.taskId }))
        }
      } catch (e) {
        // 快照失败不影响支付跳转
      }
      document.write(res.payForm)
      document.close()
      return
    }
    // #endif
    // #ifndef H5
    if (res && res.payParams) {
      await requestWxPay(res.payParams)
      const paid = await pollPaid(res.orderId)
      if (!paid) {
        ui.toast('支付结果确认中，请稍后刷新')
        return
      }
      await refreshUnlocks()
      track('pay_success', { plan, count: styleIds.length, channel })
      ui.sheet = 'paid'
      return
    }
    // #endif
    throw new Error('未返回支付参数')
  } catch (e) {
    ui.toast(payErrorMessage(e))
  } finally {
    paying.value = false
  }
}

/** 解锁状态以服务端为准：拉取后整体覆盖本地缓存 */
async function refreshUnlocks() {
  const rows = await API.unlocks()
  const keys = (Array.isArray(rows) ? rows : [])
    .map((r) => (r && r.styleId ? `${r.taskId || 0}:${r.styleId}` : ''))
    .filter(Boolean)
  app.setUnlocks(keys)
}

function lockedUnlockedIds() {
  return payableStyles.value.map((s) => s.styleId)
}

async function saveAll() {
  const list = unlockedStyles.value
  if (!list.length) {
    ui.toast('暂无可保存的效果图')
    return
  }
  let ok = 0
  for (const s of list) {
    try {
      await saveRemoteImage(s.img)
      ok += 1
    } catch (e) {
      /* 单张失败继续 */
    }
  }
  ui.toast(ok ? `已保存 ${ok} 张到相册` : '保存失败，请检查相册权限')
}

function copyText() {
  uni.setClipboardData({
    data: '我在「发镜 AI」试了新发型，8 秒出图，帮我看看适不适合？',
    success: () => ui.toast('文案已复制')
  })
}

async function shareTo(channel) {
  // 渠道与传播看板按 share_click 统计分享行为，先上报再领奖励
  track('share_click', { channel })
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
.fee-ico {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: #fff6e8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.plan-unit {
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
}
.pay-methods {
  margin: 4px 0 10px;
}
.pay-methods-title {
  margin: 2px 2px 8px;
  font-size: 11px;
  color: var(--muted);
}
.pay-method {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  margin-bottom: 8px;
  background: #fff;
  border: 1.5px solid #f0e6ea;
  border-radius: 14px;
  transition: all 0.18s;

  &.on {
    background: #fbf4f5;
    border-color: var(--pink-500);
  }

  &.on .plan-radio {
    border-color: var(--pink-500);
  }

  &.on .plan-radio::after {
    width: 9px;
    height: 9px;
    background: var(--pink-500);
    border-radius: 50%;
    content: '';
  }

  .plan-radio {
    margin-left: auto;
  }
}
.method-logo {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: #b9aeb3;
  border-radius: 8px;

  &.is-alipay {
    background: #1677ff;
  }

  &.is-wx {
    background: #22c35e;
  }

  &.is-douyin {
    background: #161823;
  }

  &.is-xhs {
    background: #ff2442;
  }
}
.method-name {
  font-size: 13px;
  font-weight: 600;
  color: #3b3337;
}
.pay-methods-empty {
  padding: 10px 2px;
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
