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
      <view v-if="!iosNoPay" class="plan" :class="{ on: payPlan === 'one' }" @click="ui.payPlan = 'one'">
        <view class="plan-radio" />
        <view style="flex:1">
          <view class="plan-t">单张解锁</view>
          <view class="plan-d">仅解锁当前这 1 张</view>
        </view>
        <view class="plan-price">¥6<text class="plan-unit">/张</text></view>
      </view>
      <view v-if="allPlanAvailable && !iosNoPay" class="plan" :class="{ on: payPlan === 'all' }" @click="ui.payPlan = 'all'">
        <view class="plan-radio" />
        <view style="flex:1">
          <view class="plan-t">本次全部 {{ lockedCount }} 款 <text class="plan-tag">限时 5 折</text></view>
          <view class="plan-d">平均 ¥3.0/张 · 推荐</view>
        </view>
        <view class="plan-price"><text class="plan-old">¥{{ lockedCount * 6 }}</text>¥{{ lockedCount * 3 }}<text class="plan-unit">/全部</text></view>
      </view>
      <button v-if="!iosNoPay" class="btn btn-primary btn-block" style="margin-top:6px" @click="payNow">
        模拟支付 ¥{{ payAmount }}
      </button>
      <view v-if="iosNoPay" class="tiny" style="text-align:center;padding:14px 0">
        iOS 端暂不支持在线支付，可通过下方分享获得免费次数
      </view>
      <view class="pay-share" @click="claimShare">
        <fj-icon name="share" :size="16" />分享给好友，再得 1 次生成
      </view>
      <view class="tiny" style="text-align:center;margin-top:9px">已有 23,481 人解锁高清 · 好评率 98.6%</view>
      <view class="tiny" style="text-align:center;margin-top:5px">原型演示，不会发起真实支付</view>
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
      <view class="tiny" style="text-align:center;margin-top:12px">原型演示：模拟支付，不会真实扣款</view>
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

    <!-- 发布讨论 -->
    <view class="sheet" :class="{ show: ui.sheet === 'compose' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="sheet-title">{{ isAsk ? '还没剪？说说你的纠结' : '交作业到讨论' }}</view>
      <view class="sheet-sub">
        {{ isAsk ? '上传一张你现在的发型照，让大家帮你参谋适不适合。' : 'AI 效果图 + 剪后实拍，帮更多人少踩一次雷。' }}
      </view>

      <view v-if="isAsk" class="compose-slots single">
        <view class="cslot" :class="{ done: !!compose.self }" @click="pickCompose('self')">
          <image v-if="compose.self" class="cslot-img" :src="compose.self" mode="aspectFill" />
          <text v-if="compose.self" class="cslot-tag">现在的发型</text>
          <template v-if="!compose.self">
            <fj-icon name="camera" :size="20" />
            <text>现在的发型照</text>
            <text class="cslot-hint">从相册上传 1 张</text>
          </template>
          <view v-else class="cslot-x" @click.stop="compose.self = ''">
            <fj-icon name="x" :size="11" color="#FFFFFF" />
          </view>
        </view>
      </view>

      <view v-else class="compose-slots">
        <view class="cslot" :class="{ done: !!compose.ai }" @click="pickCompose('ai')">
          <image v-if="compose.ai" class="cslot-img" :src="compose.ai" mode="aspectFill" />
          <text v-if="compose.ai" class="cslot-tag">AI 效果</text>
          <template v-if="!compose.ai">
            <fj-icon name="sparkles" :size="20" />
            <text>AI 效果图</text>
            <text class="cslot-hint">用试过的图或上传</text>
          </template>
          <view v-else class="cslot-x" @click.stop="compose.ai = ''">
            <fj-icon name="x" :size="11" color="#FFFFFF" />
          </view>
        </view>
        <view class="cslot" :class="{ done: !!compose.after }" @click="pickCompose('after')">
          <image v-if="compose.after" class="cslot-img" :src="compose.after" mode="aspectFill" />
          <text v-if="compose.after" class="cslot-tag">剪后实拍</text>
          <template v-if="!compose.after">
            <fj-icon name="camera" :size="20" />
            <text>剪后实拍</text>
            <text class="cslot-hint">从相册上传</text>
          </template>
          <view v-else class="cslot-x" @click.stop="compose.after = ''">
            <fj-icon name="x" :size="11" color="#FFFFFF" />
          </view>
        </view>
      </view>

      <input v-model="compose.styleName" class="compose-input" maxlength="20" placeholder="发型名称，如：法式波波头" />
      <textarea
        v-model="compose.text"
        class="compose-input"
        maxlength="120"
        :placeholder="isAsk ? '说说你的发量、脸型顾虑，想让大家帮你看看适不适合' : '说说剪完的感受：还原度怎么样？托尼听懂了吗？'"
      />
      <button class="btn btn-primary btn-block" style="margin-top:14px" @click="publish">
        <fj-icon name="share" :size="16" color="#FFFFFF" />发布到讨论
      </button>
      <view class="tiny" style="text-align:center;margin-top:9px">发布内容会同步到讨论区</view>
    </view>

    <!-- 评论 -->
    <view class="sheet" :class="{ show: ui.sheet === 'comments' }">
      <view class="sheet-grip" />
      <view class="sheet-close" @click="ui.close()"><fj-icon name="x" :size="16" /></view>
      <view class="sheet-title">评论</view>
      <view class="sheet-sub">{{ ui.commentPost ? ui.commentPost.name + ' · ' + ui.commentPost.styleName : '' }}</view>
      <view class="cm-list">
        <view v-for="(c, i) in comments" :key="i" class="cm-item">
          <text class="cm-name">{{ c.userName || c.n }}</text>
          <text class="cm-text">{{ c.content || c.x }}</text>
        </view>
        <view v-if="!comments.length" class="tiny" style="text-align:center;padding:18px 0">还没有评论，来抢沙发</view>
      </view>
      <view class="cm-input">
        <input v-model="commentText" maxlength="60" placeholder="说点有用的…" />
        <view class="cm-send" @click="sendComment"><fj-icon name="arrow-right" :size="16" color="#FFFFFF" /></view>
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
          <view class="priv-t">试发记录只存在本机</view>
          <view class="priv-d">历史记录保存在当前设备，换设备即消失。</view>
        </view>
      </view>
      <button class="btn btn-danger btn-block" style="margin-top:14px" @click="clearData">
        <fj-icon name="trash" :size="16" />立即删除我的照片与记录
      </button>
      <view class="tiny" style="text-align:center;margin-top:10px">会清空本地记录、解锁状态与分享领取标记</view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API, upload } from '@/api/request'
import { saveRemoteImage } from '@/utils/save'
import { track } from '@/utils/track'

const app = useAppStore()
const ui = useUiStore()

const comments = ref([])
const commentText = ref('')
const compose = reactive({ ai: '', after: '', self: '', styleName: '', text: '' })
/** ask=还没剪（只传一张现在的发型照），real=交作业（AI 效果图 + 剪后实拍） */
const isAsk = computed(() => ui.composeMode === 'ask')

/** 微信支付未接入时，iOS 端按平台规则隐藏支付入口，只保留分享解锁 */
const WXPAY_ENABLED = false
const iosNoPay = (() => {
  // #ifdef H5
  try {
    return /iPhone|iPad|iPod/i.test(navigator.userAgent)
  } catch (e) {
    return false
  }
  // #endif
  try {
    return uni.getSystemInfoSync().platform === 'ios'
  } catch (e2) {
    return false
  }
})() && !WXPAY_ENABLED

const currentStyle = computed(() => {
  const list = (app.result && app.result.styles) || []
  const id = app.selectedStyleIds[0]
  return list.find((s) => s.styleId === id) || list[0] || { name: '法式波波头', img: '', before: '' }
})
const beforeImg = computed(() => (app.result && app.result.before) || app.photoPath || '')
/** 解锁按「任务 + 款式」计算，避免旧批次解锁让新一批出图免费 */
const taskKey = computed(() => String((app.result && app.result.taskId) || app.taskId || ''))
const isUnlocked = (styleId) => !!styleId && app.unlocks.includes(`${taskKey.value}:${styleId}`)
/** 单张解锁的目标：优先用户点击的那款未解锁内容，其次第一款未解锁内容 */
const payStyle = computed(() => {
  const list = (app.result && app.result.styles) || []
  const hit = ui.payStyleId ? list.find((s) => s.styleId === ui.payStyleId) : null
  if (hit && !isUnlocked(hit.styleId)) return hit
  return list.find((s) => !isUnlocked(s.styleId)) || currentStyle.value
})
const afterImg = computed(() => currentStyle.value.img || '')
const unlockedStyles = computed(() => {
  const list = (app.result && app.result.styles) || []
  return list.filter((s) => isUnlocked(s.styleId))
})
const lockedCount = computed(() => {
  const list = (app.result && app.result.styles) || []
  return Math.max(0, list.length - unlockedStyles.value.length)
})
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
    await API.shareReward()
    app.grantQuota('share')
    ui.toast('已获得 1 次免费生成')
    ui.close()
  } catch (e) {
    /* request 已统一 toast */
  }
}

function watchAd() {
  app.grantQuota('ad')
  ui.toast('已获得 1 次免费生成')
  ui.close()
}

async function payNow() {
  try {
    const plan = payPlan.value
    const styleIds = plan === 'one' ? [payStyle.value.styleId] : lockedUnlockedIds()
    track('pay_click', { plan, count: styleIds.length })
    await API.createOrder({ plan, taskId: app.taskId, styleIds })
    await refreshUnlocks()
    track('pay_success', { plan, count: styleIds.length })
    ui.sheet = 'paid'
  } catch (e) {
    /* 已 toast */
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
  const list = (app.result && app.result.styles) || []
  return list.map((s) => s.styleId).filter((id) => !isUnlocked(id))
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
  if (!app.quota.shareClaimed) {
    try {
      await API.shareReward()
      app.grantQuota('share')
    } catch (e) {
      /* 已 toast */
    }
  }
  ui.toast(`已模拟分享到${channel}，奖励每账号限 1 次`)
}

function pickCompose(type) {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: type === 'ai' ? ['album'] : ['album', 'camera'],
    success: (res) => {
      compose[type] = res.tempFilePaths[0]
    }
  })
}

/** 每次打开弹窗都清空上一次选的图，避免第二次还是旧照片 */
function resetCompose() {
  compose.ai = ''
  compose.after = ''
  compose.self = ''
  compose.styleName = ''
  compose.text = ''
}

/** 本地临时图（blob/临时路径）先上传成可访问的 URL，避免发布后图片失效 */
async function toRemoteUrl(path) {
  if (!path || /^https?:\/\//.test(path)) return path
  try {
    const data = await upload(path)
    return (data && (data.url || data.path)) || path
  } catch (e) {
    return path
  }
}

async function publish() {
  if (!compose.text.trim()) {
    ui.toast(isAsk.value ? '说说你的顾虑吧' : '说说剪完的感受吧')
    return
  }
  if (isAsk.value && !compose.self) {
    ui.toast('先上传一张你现在的发型照')
    return
  }
  if (!isAsk.value && !compose.ai && !compose.after) {
    ui.toast('至少上传一张 AI 效果图或剪后实拍')
    return
  }
  try {
    const aiUrl = await toRemoteUrl(isAsk.value ? compose.self : compose.ai || afterImg.value)
    const afterUrl = isAsk.value ? '' : await toRemoteUrl(compose.after)
    await API.createPost({
      badge: isAsk.value ? 'ask' : 'real',
      gender: app.gender,
      styleName: compose.styleName || currentStyle.value.name,
      content: compose.text,
      aiImage: aiUrl,
      afterImage: afterUrl,
      tags: []
    })
    ui.toast('发布成功')
    track('post_create', { badge: isAsk.value ? 'ask' : 'real' })
    resetCompose()
    ui.close()
    uni.$emit('fajing:post-created')
  } catch (e) {
    /* 已 toast */
  }
}

async function sendComment() {
  const text = commentText.value.trim()
  if (!text || !ui.commentPost) return
  const postId = ui.commentPost.id
  // mock 帖子的 id 不是数据库主键，只做本地追加
  if (!/^\d+$/.test(String(postId))) {
    comments.value.push({ userName: '我', content: text })
    commentText.value = ''
    uni.$emit('fajing:comment-created', postId)
    return
  }
  try {
    const c = await API.addComment(postId, text)
    comments.value.push(c || { userName: '我', content: text })
    commentText.value = ''
    uni.$emit('fajing:comment-created', postId)
  } catch (e) {
    /* 已 toast */
  }
}

function clearData() {
  app.resetAll()
  ui.toast('已清空本机数据')
  ui.close()
  uni.reLaunch({ url: '/pages/home/home' })
}

/* 打开弹窗时：发布弹窗清空上次内容，评论弹窗拉取最新评论 */
function loadComments(post) {
  if (!post) return
  comments.value = (post.comments || []).slice()
  if (!/^\d+$/.test(String(post.id))) return
  API.comments(post.id)
    .then((list) => {
      if (Array.isArray(list) && list.length) comments.value = list
    })
    .catch(() => {
      /* 已统一 toast */
    })
}

watch(
  () => ui.sheet,
  (sheet) => {
    if (sheet === 'compose') resetCompose()
    if (sheet === 'comments') loadComments(ui.commentPost)
  }
)
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
