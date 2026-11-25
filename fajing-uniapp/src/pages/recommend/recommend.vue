<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">为你推荐</view>
      <view class="pts-pill" @click="goPoints">
        <fj-icon name="crown" :size="13" color="#C68A25" />
        <text>{{ app.quota.unlimited ? '不限量' : (app.quota.points || 0) }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view v-if="loading" class="rec-loading">
          <view class="rec-ring" />
          <view class="rec-loading-t">AI 正在分析你的脸型与五官…</view>
          <view class="rec-loading-d">结合脸型、五官比例与肤色，生成按适配度排序的发型与发色</view>
        </view>

        <template v-else>
          <view class="face-card">
            <view class="face-ico"><fj-icon name="scan" :size="18" color="#C95676" /></view>
            <view style="flex:1">
              <view class="face-t">
                识别结果：{{ rec.faceShape || '鹅蛋脸' }} · {{ rec.skinTone || '中性皮' }}
              </view>
              <view class="face-d">已按适配度排序，前 3 款可直接体验</view>
            </view>
          </view>

          <view class="seg rec-seg">
            <view class="seg-item" :class="{ on: tab === 'hair' }" @click="tab = 'hair'">发型推荐</view>
            <view class="seg-item" :class="{ on: tab === 'color' }" @click="tab = 'color'">发色推荐</view>
          </view>

          <!-- 发型推荐 -->
          <template v-if="tab === 'hair'">
            <view v-for="s in topStyles" :key="s.styleId" class="rec-card" @click="pickStyle(s)">
              <view class="rec-left">
                <view class="badge-row">
                  <text class="rank-badge">TOP {{ s.rank }}</text>
                  <text class="score-badge">{{ s.score }}分</text>
                </view>
                <view class="rec-name-row">
                  <text class="rec-name">{{ s.name }}</text>
                  <text class="rec-action" :class="{ free: isOpen(s) }">{{ actionText(s) }}</text>
                </view>
                <view class="rec-reason">{{ s.reason }}</view>
                <view class="rec-tag-line">{{ s.len }} · 适合{{ s.face }}</view>

                <!-- 生成中：左下角圆形进度环，完成后图片直接落在右侧框内 -->
                <view v-if="isGenerating(s)" class="rec-progress">
                  <view class="rp-ring-wrap">
                    <!-- #ifdef H5 -->
                    <svg width="66" height="66" viewBox="0 0 66 66">
                      <circle class="rp-bg" cx="33" cy="33" r="27" fill="none" stroke-width="5" />
                      <circle
                        class="rp-fg"
                        cx="33"
                        cy="33"
                        r="27"
                        fill="none"
                        stroke-width="5"
                        stroke-linecap="round"
                        stroke-dasharray="169.6"
                        :stroke-dashoffset="ringDashOf(s)"
                      />
                    </svg>
                    <!-- #endif -->
                    <!-- #ifndef H5 -->
                    <view class="rp-ring" :style="ringStyleOf(s)">
                      <view class="rp-ring-in" />
                    </view>
                    <!-- #endif -->
                    <text class="rp-num">{{ progressOf(s) }}%</text>
                  </view>
                  <text class="rp-label">AI 生成中，<br />完成后显示在右侧</text>
                </view>
              </view>

              <view class="rec-media" @click.stop="onMediaTap(s)">
                <image
                  v-if="imgOf(s)"
                  :src="imgOf(s)"
                  mode="aspectFill"
                  class="rec-media-img"
                  :class="{ blur: !isOpen(s) }"
                />
                <view v-else class="rec-media-empty">
                  <fj-icon :name="mediaIcon(s)" :size="20" color="#B7A8AF" />
                  <text class="rec-media-tip">{{ mediaTip(s) }}</text>
                </view>
                <view v-if="imgOf(s) && !isOpen(s)" class="rec-media-mask">
                  <fj-icon name="lock" :size="15" color="#FFFFFF" />
                  <text>10 魅力值</text>
                </view>
              </view>
            </view>

            <view v-if="restStyles.length" class="sec-line">
              <text>更多适配发型</text>
              <text class="sec-hint">解锁单款 10 魅力值</text>
            </view>
            <view class="style-grid">
              <view v-for="s in restStyles" :key="s.styleId" class="grid-cell" @click="pickStyle(s)">
                <view class="grid-thumb">
                  <image v-if="imgOf(s)" :src="imgOf(s)" mode="aspectFill" class="grid-img" :class="{ blur: !isOpen(s) }" />
                  <view v-else-if="isGenerating(s)" class="grid-progress">
                    <view class="gp-track small"><view class="gp-fill" :style="{ width: progressOf(s) + '%' }" /></view>
                    <text>{{ progressOf(s) }}%</text>
                  </view>
                  <view v-else class="grid-empty" />
                  <view v-if="!isOpen(s) && !isGenerating(s)" class="grid-lock">
                    <fj-icon name="lock" :size="13" color="#FFFFFF" />
                    <text>10 魅力值</text>
                  </view>
                  <text class="grid-rank">TOP {{ s.rank }}</text>
                  <text class="grid-score">{{ s.score }}分</text>
                </view>
                <view class="grid-name">{{ s.name }}</view>
                <view class="grid-reason">{{ s.reason }}</view>
              </view>
            </view>
          </template>

          <!-- 发色推荐：全部发色 + 解锁门槛 + 已生成的发型×发色组合 -->
          <template v-else>
            <view class="color-tip-top">
              {{
                colorFreeAvailable
                  ? '前 3 种基础发色免费试色，点小图查看本人发型效果'
                  : '免费生成次数已用完，解锁发色后即可永久查看'
              }}
            </view>
            <view v-for="c in displayColors" :key="c.colorId" class="color-row">
              <view class="color-head">
                <view class="color-dot-big" :style="{ background: c.bg || '#2b2b2b' }" />
                <view class="color-main">
                  <view class="color-name-row">
                    <text v-if="Number(c.rank) <= 3" class="color-rank">TOP {{ c.rank }}</text>
                    <text class="color-name">{{ c.name }}</text>
                    <text class="color-score">{{ c.score }}分</text>
                    <text class="color-state" :class="colorStateClass(c)">{{ colorStateText(c) }}</text>
                  </view>
                  <view class="color-reason">{{ c.reason }}</view>
                </view>
              </view>
              <view class="color-thumbs">
                <view v-for="s in unlockedStyleList" :key="s.styleId" class="ct-item" @click="onColorThumb(c, s)">
                  <view class="ct-thumb">
                    <image
                      v-if="variantOf(c, s.styleId)"
                      :src="variantOf(c, s.styleId)"
                      mode="aspectFill"
                      class="ct-img"
                    />
                    <view v-else-if="comboBusy[c.colorId + ':' + s.styleId]" class="ct-loading">
                      <view class="ct-spin" />
                    </view>
                    <view v-else class="ct-lock">
                      <fj-icon :name="isColorOpen(c) ? 'sparkles' : 'lock'" :size="15" color="#FFFFFF" />
                      <text>{{ isColorOpen(c) ? '生成预览' : '解锁发色' }}</text>
                    </view>
                  </view>
                  <text class="ct-name">{{ s.name }}</text>
                </view>
                <view v-if="!unlockedStyleList.length" class="ct-placeholder">
                  {{ unlockedStyles.length ? '发型图生成中…' : '先解锁发型后即可试色' }}
                </view>
              </view>
            </view>
            <view class="color-tip">发色依据你的肤色冷暖匹配，预览图均为本人发型效果</view>
          </template>

          <view class="rec-bottom-tip">AI 分析仅用于本次推荐 · 原图将在 24 小时内删除</view>
        </template>
      </view>
    </scroll-view>

    <!-- 自定义解锁弹窗：与整体粉色风格一致 -->
    <view v-if="dlg.show" class="dlg-mask" @click="closeDlg">
      <view class="dlg" @click.stop>
        <view class="dlg-ico">
          <fj-icon
            :name="dlg.type === 'recharge' ? 'crown' : 'lock'"
            :size="22"
            :color="dlg.type === 'recharge' ? '#C68A25' : '#C95676'"
          />
        </view>
        <view class="dlg-title">{{ dlg.title }}</view>
        <view class="dlg-desc">{{ dlg.desc }}</view>
        <view class="dlg-balance">
          当前魅力值 <text class="dlg-balance-num">{{ app.quota.unlimited ? '不限量' : (app.quota.points || 0) }}</text>
          <text v-if="!app.quota.unlimited"> 点</text>
          <text v-if="adCredits > 0"> · 免费次数 {{ adCredits }}</text>
        </view>
        <template v-if="dlg.type === 'color'">
          <view class="dlg-actions">
            <view class="dlg-btn ghost" @click="closeDlg">再想想</view>
            <view class="dlg-btn primary" @click="doUnlockColor('points')">10 点解锁</view>
          </view>
          <view v-if="adCredits > 0" class="dlg-btn credit" @click="doUnlockColor('credit')">
            用免费解锁次数（剩 {{ adCredits }} 次）
          </view>
        </template>
        <view v-else class="dlg-actions">
          <view class="dlg-btn ghost" @click="closeDlg">再想想</view>
          <view v-if="!(dlg.type === 'recharge' && payHidden)" class="dlg-btn primary" @click="confirmDlg">
            {{ dlg.confirmText }}
          </view>
        </view>
        <view v-if="dlg.type === 'recharge' && payHidden" class="dlg-ios-tip">
          iOS 端暂不支持购买魅力值，可继续通过看广告获取免费解锁次数
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { track } from '@/utils/track'
import { isIOSMiniProgram } from '@/utils/pay'
import { goBack } from '@/utils/nav'

const app = useAppStore()
const ui = useUiStore()

/** iOS 小程序不展示虚拟商品购买入口 */
const payHidden = isIOSMiniProgram()

const loading = ref(true)
const tab = ref('hair')
const rec = ref({})
const styles = ref([])
const colors = ref([])
/** styleId -> 生成结果项（含 img / unlocked / pending / failed） */
const taskItems = ref({})
/** 免费批次的整体生成进度（0-100） */
const batchProgress = ref(0)
const batchDone = ref(false)
/** 本次任务是否占用了免费批次（决定前 3 款是否会预生成） */
const batchFree = ref(false)
/** 单款付费解锁后的生成进度：styleId -> 0-100 */
const paidProgress = ref({})
/** 自定义解锁/充值弹窗 */
const dlg = ref({ show: false, type: 'unlock', title: '', desc: '', confirmText: '立即解锁', action: null })

let taskId = ''
let timer = null
let progressTimer = null
let colorTimer = null
let colorPolls = 0
let stopped = false
let resultPayload = null

const topStyles = computed(() => styles.value.filter((s) => Number(s.rank) <= 3))
const restStyles = computed(() => styles.value.filter((s) => Number(s.rank) > 3))
const unlockedStyles = computed(() => styles.value.filter((s) => isOpen(s)))

/** 发色页实时状态：免费次数 / 魅力值 / 已解锁发色与发型，均以服务端返回为准 */
const colorState = ref({ freeAvailable: true, freeTop: 3, unlimited: false, colors: [], unlockedStyleIds: [] })
/** 正在生成的「发色:发型」组合 */
const comboBusy = ref({})
/** 发色页展示：免费次数可用时默认前 3 种基础发色；免费次数用完展示全部发色（均带解锁门槛）。
 *  已解锁或已生成过组合的发色始终保留展示，满足「已解锁内容永久可见」。 */
const displayColors = computed(() => {
  const list = colorState.value.colors
  const all = Array.isArray(list) && list.length ? list : colors.value
  if (colorState.value.freeAvailable === false) return all
  const freeTop = Number(colorState.value.freeTop || 3)
  return all.filter(
    (c) => Number(c.rank) <= freeTop || c.unlocked || hasVariants(c)
  )
})

function hasVariants(c) {
  return !!(c && c.variants && Object.keys(c.variants).length)
}
const colorFreeAvailable = computed(() => colorState.value.freeAvailable !== false)
const adCredits = computed(() => Number(app.quota.adCredits || 0))
/** 已解锁发型：本地任务结果 + 服务端解锁列表合并，避免两端不同步 */
const unlockedStyleList = computed(() => {
  const ids = new Set(colorState.value.unlockedStyleIds || [])
  return styles.value.filter((s) => isOpen(s) || ids.has(s.styleId))
})

function itemOf(s) {
  return s && taskItems.value[s.styleId]
}

function imgOf(s) {
  const item = itemOf(s)
  return item && item.img ? item.img : ''
}

function isOpen(s) {
  const item = itemOf(s)
  return !!(item && item.unlocked && item.img)
}

/** 本批次内排名前 3 款仍属于免费名额（只预生成第 1 款，其余点开再生成） */
function isFreeSlot(s) {
  return batchFree.value && Number(s.rank) <= 3
}

/** 正在调用 AI 生成：只预生成第 1 款，或刚解锁的单款 */
function isGenerating(s) {
  const item = itemOf(s)
  if (item && (item.img || item.failed)) return false
  if (paidProgress.value[s.styleId] != null) return true
  if (item && item.pending) return false
  if (Number(s.rank) <= 3) return batchFree.value && !batchDone.value && Number(s.rank) === 1
  return false
}

function progressOf(s) {
  const paid = paidProgress.value[s.styleId]
  if (paid != null) return Math.min(100, Math.round(paid))
  return Math.min(99, Math.round(batchProgress.value || 0))
}

/** 圆形进度环：H5 用 SVG dashoffset（r=27，周长约 169.6），小程序用 conic-gradient */
function ringDashOf(s) {
  const p = Math.max(0, Math.min(100, progressOf(s)))
  return 169.6 - (169.6 * p) / 100
}

function ringStyleOf(s) {
  const p = Math.max(0, Math.min(100, progressOf(s)))
  return `background: conic-gradient(from -90deg, #C95676 0% ${p}%, #F1DDE2 ${p}% 100%)`
}

function mediaIcon(s) {
  const item = itemOf(s)
  if (item && item.failed) return 'refresh'
  if (isGenerating(s)) return 'sparkles'
  if ((item && item.freePending) || (!item && isFreeSlot(s))) return 'gift'
  return 'lock'
}

function mediaTip(s) {
  const item = itemOf(s)
  if (item && item.failed) return '生成失败 · 点击重试'
  if (isGenerating(s)) return 'AI 生成中'
  if ((item && item.freePending) || (!item && isFreeSlot(s))) return '免费生成'
  return '10 魅力值解锁'
}

/** 点右侧图片：已解锁直接进对比页，否则走解锁/重试 */
function onMediaTap(s) {
  if (isOpen(s)) {
    openResult(s.styleId)
    return
  }
  pickStyle(s)
}

function actionText(s) {
  const item = itemOf(s)
  if (item && item.img && item.unlocked) return '查看效果 ›'
  if (item && item.failed) return '生成失败 · 点击重试'
  if (isGenerating(s)) return 'AI 生成中…'
  if (item && item.freePending) return '免费生成 ›'
  if (item && item.pending) return '10 魅力值解锁'
  if (!item && isFreeSlot(s)) return '免费生成 ›'
  return '10 魅力值解锁'
}

function variantOf(c, styleId) {
  if (!c || !c.variants) return ''
  const img = c.variants[styleId]
  return img ? img : ''
}

function back() {
  goBack()
}

function goPoints() {
  uni.navigateTo({ url: '/pages/points/points' })
}

async function syncQuota() {
  try {
    app.setQuota(await API.quota())
  } catch (e) {
    /* 同步失败不阻断推荐展示 */
  }
}

async function start() {
  loading.value = true
  try {
    const data = await API.recommend({ photoUrl: app.photoUrl, gender: app.gender })
    rec.value = data || {}
    styles.value = (data && data.styles) || []
    colors.value = (data && data.colors) || []
    track('recommend_view', { faceShape: rec.value.faceShape, styleCount: styles.value.length })
    await createBatch()
  } catch (e) {
    loading.value = false
    ui.toast('推荐生成失败，请返回重试')
  }
}

/** 一次任务包含全部推荐款式：免费批次只预生成前 3 款，第 4 款起解锁后再生成 */
async function createBatch() {
  if (!styles.value.length) {
    loading.value = false
    ui.toast('暂无可推荐发型，请稍后再试')
    return
  }
  // 先用服务端额度校准，避免本地缓存的免费批次与实际不一致
  await syncQuota()
  const ids = styles.value.map((s) => s.styleId)
  batchFree.value = Number(app.quota.freeBatches || 0) > 0
  batchDone.value = false
  batchProgress.value = 0
  const res = await API.generate({
    photoUrl: app.photoUrl,
    gender: app.gender,
    mode: 'hair',
    styleIds: ids,
    recommendId: rec.value.recommendId
  })
  taskId = String((res && res.taskId) || '')
  app.taskId = taskId
  app.selectedStyleIds = ids.slice(0, 3)
  await syncQuota()
  // 发色页依赖服务端最新状态：免费次数、魅力值与已解锁发型
  await refreshColorState()
  loading.value = false
  startPoll()
}

function startPoll() {
  stopPoll()
  startFakeProgress()
  poll()
  timer = setInterval(poll, 1200)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  stopFakeProgress()
}

/** 服务端只在关键节点更新进度，这里补一个平滑推进的进度条，避免长时间停在 10% */
function startFakeProgress() {
  stopFakeProgress()
  progressTimer = setInterval(() => {
    if (batchDone.value) {
      stopFakeProgress()
      return
    }
    if (batchProgress.value < 88) {
      batchProgress.value = Math.min(88, batchProgress.value + (batchProgress.value < 40 ? 2 : 1))
    }
  }, 900)
}

function stopFakeProgress() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

async function poll() {
  if (!taskId || stopped) return
  try {
    const data = await API.task(taskId)
    if (!data) return
    if (typeof data.progress === 'number') {
      batchProgress.value = Math.max(batchProgress.value, data.progress)
    }
    applyItems(data.result)
    if (data.status === 'success') {
      applyItems(data.result)
      batchProgress.value = 100
      batchDone.value = true
      stopPoll()
      loading.value = false
      // 主图完成后，后端开始补发色预览，这里同步轮询小图
      startColorPoll()
    } else if (data.status === 'failed') {
      batchDone.value = true
      stopPoll()
      loading.value = false
      ui.toast('生成失败，请返回重试')
    }
  } catch (e) {
    /* 单次失败继续轮询 */
  }
}

function applyItems(result) {
  if (!result || !Array.isArray(result.styles)) return
  const map = {}
  result.styles.forEach((item) => {
    map[item.styleId] = item
  })
  taskItems.value = map
  resultPayload = { ...result, taskId }
  app.setResult(resultPayload)
}

async function refreshTask() {
  const data = await API.task(taskId)
  applyItems(data && data.result)
  return data
}

async function waitImage(styleId) {
  for (let i = 0; i < 50; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1500))
    try {
      const data = await refreshTask()
      const item = taskItems.value[styleId]
      if (item && item.img) return true
      if (data && data.status === 'success' && (!item || item.failed)) return false
    } catch (e) {
      /* 继续轮询 */
    }
  }
  return false
}

function closeDlg() {
  dlg.value = { ...dlg.value, show: false, action: null }
}

function askRecharge() {
  dlg.value = {
    show: true,
    type: 'recharge',
    title: '魅力值不足',
    desc: payHidden
      ? 'iOS 端暂不支持购买魅力值，可继续通过看广告获取免费解锁次数，或使用安卓设备 / 微信 H5 充值'
      : '解锁 1 款发型需要 10 点魅力值，可通过充值或看广告获取',
    confirmText: '去充值',
    action: () => goPoints()
  }
}

function confirmDlg() {
  const fn = dlg.value.action
  closeDlg()
  if (fn) fn()
}

async function pickStyle(s) {
  const item = itemOf(s)
  if (item && item.img && item.unlocked) {
    openResult(s.styleId)
    return
  }
  if (item && item.failed) {
    retryStyle(s)
    return
  }
  if (item && item.pending) {
    unlockStyle(s)
    return
  }
  // 只预生成第 1 款：其余免费名额等第 1 款完成后再生成，避免任务结果互相覆盖
  if (batchFree.value && Number(s.rank) > 1 && Number(s.rank) <= 3 && !batchDone.value) {
    ui.toast('第 1 款生成中，请稍候…')
    return
  }
  if (isGenerating(s)) {
    ui.toast('高清图生成中，请稍候…')
    return
  }
  unlockStyle(s)
}

async function retryStyle(s) {
  try {
    await API.retryStyle(taskId, s.styleId)
    ui.toast('已重新提交生成')
    const ok = await waitImage(s.styleId)
    if (ok) openResult(s.styleId)
  } catch (e) {
    /* request 已提示 */
  }
}

/** 解锁前先弹自定义风格弹窗，确认后再扣免费次数或魅力值 */
function unlockStyle(s) {
  if (!taskId) return
  const item = itemOf(s)
  const free = isFreeSlot(s) || !!(item && item.freePending)
  const adCredits = Number(app.quota.adCredits || 0)
  if (!free && !app.quota.unlimited && adCredits <= 0 && Number(app.quota.points || 0) < 10) {
    askRecharge()
    return
  }
  dlg.value = {
    show: true,
    type: 'unlock',
    title: free ? '免费生成这款发型' : '解锁这款发型',
    desc: free
      ? `「${s.name}」属于本批次免费名额，点击后立即生成`
      : adCredits > 0
        ? `「${s.name}」将消耗 1 次广告获得的免费解锁次数`
        : `「${s.name}」解锁后可保存高清效果图，消耗 10 点魅力值`,
    confirmText: free ? '立即生成' : '立即解锁',
    action: () => performUnlock(s)
  }
}

async function performUnlock(s) {
  uni.showLoading({ title: '解锁中…', mask: true })
  try {
    const data = await API.unlock({ taskId, styleId: s.styleId })
    app.setQuota(data)
    uni.hideLoading()
    track('style_unlock', { taskId, styleId: s.styleId, points: data && data.points })
    // 解锁成功后直接在这张卡片上展示 AI 生成进度，不再遮挡整页
    paidProgress.value = { ...paidProgress.value, [s.styleId]: 6 }
    const ok = await waitPaidImage(s.styleId)
    if (ok) {
      await new Promise((resolve) => setTimeout(resolve, 320))
      clearPaidProgress(s.styleId)
      // 新发型出图后，后端会补它的发色预览，这里开始轮询刷新
      startColorPoll()
      openResult(s.styleId)
    } else {
      clearPaidProgress(s.styleId)
      ui.toast('已解锁，图片仍在生成，可稍后在记录中查看')
    }
  } catch (e) {
    uni.hideLoading()
    if (String((e && e.message) || '').indexOf('魅力值不足') > -1) askRecharge()
  }
}

/** 轮询单款付费生成结果，同时推进卡片内的生成进度条 */
async function waitPaidImage(styleId) {
  for (let i = 0; i < 50; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1500))
    paidProgress.value = { ...paidProgress.value, [styleId]: Math.min(95, 8 + i * 3) }
    try {
      const data = await refreshTask()
      const item = taskItems.value[styleId]
      if (item && item.img) {
        paidProgress.value = { ...paidProgress.value, [styleId]: 100 }
        return true
      }
      if (data && data.status === 'success' && item && item.failed) return false
    } catch (e) {
      /* 继续轮询 */
    }
  }
  return false
}

function clearPaidProgress(styleId) {
  const next = { ...paidProgress.value }
  delete next[styleId]
  paidProgress.value = next
}

function openResult(styleId) {
  if (!resultPayload) return
  // 从发色流程返回后 app.result 可能已被发色结果覆盖，这里以本批推荐结果为准
  app.setResult({ ...resultPayload, taskId })
  uni.navigateTo({
    url: '/pages/result/result?styleId=' + encodeURIComponent(styleId || '')
  })
}

/** 点开发色小图：预览后端已生成，直接进入结果页，不再调用 AI */
function openColor(c, s) {
  const img = variantOf(c, s.styleId)
  if (!img) {
    ui.toast('这组发色预览还在生成，请稍候')
    startColorPoll()
    return
  }
  app.taskId = taskId
  app.selectedStyleIds = [s.styleId]
  app.setResult({
    before: app.photoUrl,
    faceShape: rec.value.faceShape,
    skinTone: rec.value.skinTone,
    taskId,
    styles: [
      {
        styleId: s.styleId,
        name: s.name,
        len: s.len,
        face: s.face,
        cat: s.cat,
        rank: s.rank,
        score: s.score,
        reason: s.reason,
        img,
        unlocked: true,
        colorId: c.colorId,
        colorName: c.name,
        colorScore: c.score,
        colorReason: c.reason
      }
    ]
  })
  track('color_preview_open', { colorId: c.colorId, styleId: s.styleId })
  uni.navigateTo({
    url: '/pages/result/result?styleId=' + encodeURIComponent(s.styleId)
  })
}

function previewsReady() {
  const color = displayColors.value[0]
  const first = styles.value[0]
  return !!(color && first && variantOf(color, first.styleId))
}

function hasBusyCombo() {
  return Object.keys(comboBusy.value || {}).length > 0
}

/** 发色当前是否可直接使用（免费 / 已永久解锁 / 会员不限量） */
function isColorOpen(c) {
  return !!c.unlocked || !!c.free || !!colorState.value.unlimited
}

function colorStateText(c) {
  if (c.unlocked) return '已解锁'
  if (colorState.value.unlimited) return '会员免费'
  if (c.free) return '免费'
  return '10 魅力值'
}

function colorStateClass(c) {
  return {
    on: !!c.unlocked || !!colorState.value.unlimited,
    free: !!c.free && !c.unlocked && !colorState.value.unlimited
  }
}

function applyColorState(state) {
  if (!state) return
  colorState.value = state
  if (Array.isArray(state.colors) && state.colors.length) colors.value = state.colors
  if (state.quota) app.setQuota(state.quota)
}

/** 拉取发色页实时状态：免费次数、魅力值、已解锁发色与发型 */
async function refreshColorState() {
  if (!rec.value.recommendId || stopped) return
  try {
    applyColorState(await API.colorState(rec.value.recommendId, taskId))
  } catch (e) {
    /* 同步失败不阻断展示 */
  }
}

/** 解锁弹窗：同时提供「魅力值」「免费解锁次数」两种方式 */
function askColorUnlock(c, s) {
  dlg.value = {
    show: true,
    type: 'color',
    title: `解锁「${c.name}」发色`,
    desc: '解锁后该发色永久保留，可应用到任意已解锁发型',
    confirmText: '10 点解锁',
    colorId: c.colorId,
    styleId: (s && s.styleId) || ''
  }
}

async function doUnlockColor(method) {
  if (dlg.value.type !== 'color' || !dlg.value.colorId) return
  const colorId = dlg.value.colorId
  const styleId = dlg.value.styleId
  closeDlg()
  uni.showLoading({ title: '解锁中…', mask: true })
  try {
    const data = await API.unlockColor({
      recommendId: rec.value.recommendId,
      colorId,
      payMethod: method
    })
    app.setQuota(data)
    uni.hideLoading()
    uni.showToast({ title: data && data.already ? '该发色已解锁' : '解锁成功', icon: 'success' })
    await refreshColorState()
    // 解锁后自动为刚才选中的发型生成预览
    const color = displayColors.value.find((x) => x.colorId === colorId)
    const style = styles.value.find((x) => x.styleId === styleId)
    if (color && style && !variantOf(color, style.styleId)) {
      await generateCombo(color, style)
    }
  } catch (e) {
    uni.hideLoading()
    const msg = String((e && e.message) || '')
    if (msg.indexOf('魅力值不足') > -1) askRecharge()
    else ui.toast(msg || '解锁失败，请稍后重试')
  }
}

/** 点击发色小图：已有图直接看；缺图时先判断解锁，再生成 */
async function onColorThumb(c, s) {
  const img = variantOf(c, s.styleId)
  if (img) {
    openColor(c, s)
    return
  }
  if (!isColorOpen(c)) {
    askColorUnlock(c, s)
    return
  }
  await generateCombo(c, s)
}

/** 生成「发型 × 发色」预览：后端已缓存则直接返回图片 */
async function generateCombo(c, s) {
  const key = c.colorId + ':' + s.styleId
  if (comboBusy.value[key]) return
  comboBusy.value = { ...comboBusy.value, [key]: true }
  try {
    const res = await API.recommendColor({
      recommendId: rec.value.recommendId,
      colorId: c.colorId,
      styleId: s.styleId,
      taskId
    })
    if (res && res.img) {
      await refreshColorState()
      return
    }
    if (res && res.taskId) {
      const ok = await waitComboVariant(c.colorId, s.styleId)
      if (!ok) ui.toast('预览生成中，可稍后再看')
      await refreshColorState()
    }
  } catch (e) {
    const msg = String((e && e.message) || '')
    if (msg.indexOf('解锁') > -1) askColorUnlock(c, s)
    else if (msg.indexOf('魅力值不足') > -1) askRecharge()
  } finally {
    const next = { ...comboBusy.value }
    delete next[key]
    comboBusy.value = next
  }
}

/** 轮询单个「发色 + 发型」预览生成结果 */
async function waitComboVariant(colorId, styleId) {
  for (let i = 0; i < 40; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1500))
    if (stopped) return false
    try {
      const state = await API.colorState(rec.value.recommendId, taskId)
      if (state) {
        applyColorState(state)
        const color = (state.colors || []).find((x) => x.colorId === colorId)
        if (color && variantOf(color, styleId)) return true
      }
    } catch (e) {
      /* 继续轮询 */
    }
  }
  return false
}

function startColorPoll() {
  if (!rec.value.recommendId || colorTimer) return
  colorPolls = 0
  refreshColorState()
  colorTimer = setInterval(async () => {
    colorPolls += 1
    await refreshColorState()
    if (colorPolls > 240 || (!hasBusyCombo() && previewsReady())) stopColorPoll()
  }, 2500)
}

function stopColorPoll() {
  if (colorTimer) {
    clearInterval(colorTimer)
    colorTimer = null
  }
}

watch(tab, (value) => {
  if (value === 'color') {
    refreshColorState()
    startColorPoll()
  }
})

onLoad(async () => {
  if (!app.photoUrl) {
    ui.toast('请先上传照片')
    setTimeout(() => uni.reLaunch({ url: '/pages/home/home' }), 800)
    return
  }
  await start()
})

onUnload(() => {
  stopped = true
  stopPoll()
  stopColorPoll()
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
.rec-loading {
  padding: 90px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.rec-ring {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  border: 4px solid #f1dde2;
  border-top-color: #c95676;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.rec-loading-t {
  font-size: 14px;
  font-weight: 700;
}
.rec-loading-d {
  font-size: 11px;
  color: var(--muted);
  text-align: center;
  line-height: 1.6;
}
.face-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--sh-sm);
  margin-bottom: 12px;
}
.face-ico {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: var(--pink-50);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.face-t {
  font-size: 13px;
  font-weight: 700;
}
.face-d {
  font-size: 10.5px;
  color: var(--muted);
  margin-top: 2px;
}
.rec-seg {
  margin: 4px 0 14px;
}
.rec-card {
  display: flex;
  align-items: stretch;
  gap: 12px;
  padding: 14px;
  background: #fff;
  border-radius: 20px;
  box-shadow: var(--sh-sm);
  margin-bottom: 16px;
}
.rec-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.badge-row {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
}
.rec-progress {
  margin-top: auto;
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.rp-ring-wrap {
  position: relative;
  width: 66px;
  height: 66px;
  flex: none;
}
.rp-bg {
  stroke: #f1dde2;
}
.rp-fg {
  stroke: #c95676;
  transform: rotate(-90deg);
  transform-origin: 50% 50%;
  transition: stroke-dashoffset 0.4s ease;
}
.rp-ring {
  width: 66px;
  height: 66px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rp-ring-in {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: #fff;
}
.rp-num {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13.5px;
  font-weight: 800;
  color: #b54263;
}
.rp-label {
  font-size: 10px;
  line-height: 1.55;
  color: var(--muted);
}
.rec-media {
  width: 124px;
  height: 158px;
  flex: none;
  align-self: center;
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(145deg, #f5e7ea, #eee9f2);
}
.rec-media-img {
  width: 100%;
  height: 100%;
}
.rec-media-img.blur {
  filter: blur(14px);
  transform: scale(1.15);
}
.rec-media-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 10px;
}
.rec-media-tip {
  font-size: 10px;
  line-height: 1.4;
  color: #9c8f95;
  text-align: center;
}
.rec-media-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(35, 27, 31, 0.42);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}
.gp-track {
  width: 100%;
  height: 7px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.75);
  overflow: hidden;
}
.gp-track.small {
  height: 4px;
  background: rgba(255, 255, 255, 0.55);
}
.gp-fill {
  height: 100%;
  border-radius: 99px;
  background: linear-gradient(90deg, #e8a0b4, #c95676);
  transition: width 0.45s ease;
}
.gp-t {
  font-size: 10.5px;
  color: #8f7f86;
  letter-spacing: 0.2px;
}
.badge-row {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 7px;
}
.rank-badge {
  padding: 4px 10px;
  border-radius: 99px;
  background: rgba(201, 86, 118, 0.92);
  color: #fff;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.4px;
  white-space: nowrap;
}
.score-badge {
  padding: 4px 10px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.92);
  color: #b54263;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}
.rec-name-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 8px;
}
.rec-name {
  font-size: 15px;
  font-weight: 800;
}
.rec-reason {
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 4px;
  line-height: 1.55;
}
.rec-tag-line {
  margin-top: 6px;
  font-size: 10.5px;
  color: #9b8f95;
}
.rec-foot {
  display: none;
}
.rec-tag {
  font-size: 10.5px;
  color: #9b8f95;
}
.rec-action {
  font-size: 11.5px;
  font-weight: 700;
  color: #b54263;
  flex: none;
}
.rec-action.free {
  color: #3f9e6b;
}
.sec-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 2px 10px;
  font-size: 12.5px;
  font-weight: 700;
}
.sec-hint {
  font-size: 10.5px;
  color: var(--muted);
  font-weight: 500;
}
.style-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
}
.grid-cell {
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--sh-sm);
}
.grid-thumb {
  position: relative;
  height: 106px;
  background: linear-gradient(145deg, #f5e7ea, #eee9f2);
}
.grid-img {
  width: 100%;
  height: 100%;
}
.grid-img.blur {
  filter: blur(18px);
  transform: scale(1.6);
}
.grid-empty {
  width: 100%;
  height: 100%;
}
.grid-progress {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 12px;
  font-size: 9.5px;
  font-weight: 700;
  color: #8f7f86;
}
.grid-lock {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: rgba(35, 27, 31, 0.42);
  color: #fff;
  font-size: 9.5px;
  font-weight: 700;
  z-index: 2;
}
.grid-rank {
  position: absolute;
  top: 5px;
  left: 5px;
  padding: 1px 6px;
  border-radius: 99px;
  background: rgba(201, 86, 118, 0.9);
  color: #fff;
  font-size: 8.5px;
  font-weight: 800;
  z-index: 3;
}
.grid-score {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 2px 6px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.9);
  color: #b54263;
  font-size: 9.5px;
  font-weight: 800;
  z-index: 3;
}
.grid-name {
  padding: 7px 8px 0;
  font-size: 11px;
  font-weight: 700;
}
.grid-reason {
  padding: 2px 8px 8px;
  font-size: 9px;
  color: var(--muted);
  line-height: 1.45;
  height: 30px;
  overflow: hidden;
}
.color-tip-top {
  margin: 2px 2px 12px;
  font-size: 10.5px;
  color: var(--muted);
  line-height: 1.5;
}
.color-row {
  padding: 12px 14px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
  margin-bottom: 10px;
}
.color-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.color-dot-big {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  flex: none;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.7), 0 2px 8px rgba(0, 0, 0, 0.12);
}
.color-main {
  flex: 1;
  min-width: 0;
}
.color-name-row {
  display: flex;
  align-items: center;
  gap: 7px;
}
.color-rank {
  font-size: 9px;
  font-weight: 800;
  color: #fff;
  background: rgba(201, 86, 118, 0.9);
  border-radius: 99px;
  padding: 1px 6px;
  flex: none;
}
.color-name {
  font-size: 13.5px;
  font-weight: 800;
}
.color-score {
  font-size: 10px;
  color: #b54263;
  background: var(--pink-50);
  border-radius: 99px;
  padding: 1px 7px;
  flex: none;
}
.color-state {
  margin-left: auto;
  font-size: 9.5px;
  font-weight: 700;
  color: #a9701b;
  background: #fff6e8;
  border-radius: 99px;
  padding: 1px 7px;
  flex: none;
  white-space: nowrap;
}
.color-state.free {
  color: #3f9e6b;
  background: #e9f7ef;
}
.color-state.on {
  color: #b54263;
  background: var(--pink-50);
}
.color-reason {
  font-size: 10.5px;
  color: var(--muted);
  margin-top: 3px;
  line-height: 1.5;
}
.color-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #f3e6ea;
}
.ct-item {
  width: 60px;
}
.ct-thumb {
  width: 60px;
  height: 76px;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(145deg, #f5e7ea, #eee9f2);
}
.ct-img {
  width: 100%;
  height: 100%;
}
.ct-loading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ct-spin {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid #f1dde2;
  border-top-color: #c95676;
  animation: spin 0.9s linear infinite;
}
.ct-name {
  display: block;
  margin-top: 4px;
  font-size: 9px;
  color: var(--muted);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ct-placeholder {
  font-size: 10px;
  color: var(--muted);
  padding: 18px 0;
}
.ct-lock {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 0 4px;
  background: rgba(35, 27, 31, 0.5);
  color: #fff;
  font-size: 8.5px;
  font-weight: 700;
  text-align: center;
}
.color-tip {
  margin: 16px 4px 0;
  font-size: 10px;
  color: var(--muted);
  line-height: 1.6;
  text-align: center;
}
.rec-bottom-tip {
  margin: 22px 0 14px;
  text-align: center;
  font-size: 9.5px;
  color: #b7a8af;
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
.dlg-balance {
  margin: 12px auto 0;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 5px 12px;
  border-radius: 99px;
  background: var(--pink-50);
  font-size: 10.5px;
  color: var(--pink-700);
}
.dlg-balance-num {
  font-size: 13px;
  font-weight: 800;
}
.dlg-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.dlg-ios-tip {
  margin-top: 12px;
  font-size: 10.5px;
  line-height: 1.55;
  color: #a9701b;
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
.dlg-btn.credit {
  width: 100%;
  margin-top: 8px;
  background: #fff6e8;
  color: #a9701b;
}
</style>
