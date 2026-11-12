<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="goBack">
        <fj-icon name="arrow-left" :size="16" />
      </view>
      <view class="topbar-title">你的新发型</view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="result-meta" style="padding-bottom:10px">
        <view class="meta-main">
          <view class="meta-name">{{ current.name }}</view>
          <view class="meta-desc">长度：{{ current.len }} · 适合{{ current.face }}</view>
        </view>
        <view class="pill" @click="shuffleBatch">
          <fj-icon name="refresh" :size="13" color="#4E575A" />
          <text>换一批</text>
        </view>
      </view>

      <view class="pad">
        <view class="seg res-view-seg">
          <view class="seg-item" :class="{ on: view === 'compare' }" @click="setView('compare')">滑动对比</view>
          <view class="seg-item" :class="{ on: view === 'grid' }" @click="setView('grid')">四宫格</view>
        </view>

        <view v-if="view === 'compare'">
          <view v-if="isFailed(current)" class="res-fail" @click="retryCurrent">
            <fj-icon name="refresh" :size="24" color="#8B9295" />
            <text class="res-fail-t">{{ retrying ? '正在重新生成…' : '生成失败，点击重试' }}</text>
            <text class="res-fail-d">{{ current.failReason || '生成超时/服务异常' }}</text>
          </view>
          <fj-compare-slider
            v-else
            ref="sliderRef"
            v-model:pct="baPct"
            :before="beforeImg"
            :after="current.img"
            :unlocked="unlocked"
            @lock="openPay()"
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
                {{ fit.score }}<text style="font-size:9px;font-weight:700">%</text>
              </view>
            </view>
            <view class="fit-main">
              <view class="fit-t">
                <text>脸型适配度</text>
                <text class="fit-lv">{{ fit.lv }}</text>
              </view>
              <view class="fit-d">{{ fit.desc }}</view>
              <view class="fit-tags">
                <text v-for="tag in fit.tags" :key="tag" class="fit-tag">{{ tag }}</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="remaining > 0" class="unlock-bar" @click="openPay()">
          <view class="ub-thumbs">
            <view v-for="item in lockedList.slice(0, 3)" :key="item.styleId" class="ub-thumb">
              <image class="thumb-img" :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
            </view>
          </view>
          <view class="ub-main">
            <view class="ub-t">还有 {{ remaining }} 款效果已生成</view>
            <view class="ub-d">{{ unlockSub }}</view>
          </view>
          <text class="ub-btn">解锁</text>
        </view>

        <view v-if="view === 'grid'">
          <view class="res-grid" :class="{ single: styles.length === 1 }">
            <view
              v-for="(item, i) in styles"
              :key="item.styleId"
              class="res-cell"
              :class="{ on: i === activeIndex, locked: !isUnlocked(item) }"
              @click="openFromGrid(i)"
            >
              <image v-if="!isFailed(item)" class="thumb-img" :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
              <view v-else class="fail-thumb"><text>生成失败</text></view>
              <text class="res-cell-idx">{{ i + 1 }}</text>
              <view v-if="!isUnlocked(item)" class="res-cell-lock">
                <view class="lk">
                  <fj-icon name="lock" :size="16" color="#C95676" />
                </view>
                <text class="rc-lt">付费解锁</text>
                <text class="rc-ls">¥6/张 · 高清无水印</text>
              </view>
              <view class="res-cell-name">{{ item.name }}</view>
            </view>
          </view>
          <view class="res-grid-tip">点任意一张，进入滑动对比</view>
        </view>

        <view v-if="view === 'compare'" class="barber-entry" @click="openCard">
          <view class="be-ico">
            <fj-icon name="scissors" :size="16" color="#F0B7C7" />
          </view>
          <view style="flex:1;min-width:0">
            <view class="be-t">生成理发师沟通卡</view>
            <view class="be-d">长度、层次、推剪卡尺写清楚，直接发给托尼</view>
          </view>
          <fj-icon name="chevron-right" :size="16" color="rgba(255,255,255,.55)" />
        </view>
      </view>

      <view v-if="view === 'compare'" class="result-strip">
        <view
          v-for="(item, i) in styles"
          :key="item.styleId"
          class="r-thumb"
          :class="{ on: i === activeIndex, locked: !isUnlocked(item) }"
          @click="selectStyle(i)"
        >
          <image v-if="!isFailed(item)" class="thumb-img" :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
          <view v-else class="fail-thumb"><text>失败</text></view>
          <text class="r-thumb-idx">{{ i + 1 }}</text>
          <view v-if="!isUnlocked(item)" class="r-thumb-lock">
            <fj-icon name="lock" :size="14" color="#FFFFFF" />
            <text class="rtl-t">付费</text>
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
      <view class="rb-primary" :class="{ done: remaining === 0 }" @click="onPrimary">
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
import { computed, nextTick, ref } from 'vue'
import { onLoad, onReady, onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { saveRemoteImage } from '@/utils/save'
import { track } from '@/utils/track'
import { DEMO_BEFORE } from '@/mock/constants'

const app = useAppStore()
const ui = useUiStore()

const view = ref('compare')
const activeIndex = ref(0)
const baPct = ref(50)
/** 失败款重试中，避免重复提交 */
const retrying = ref(false)
/** 是否从试发记录进入（决定返回目标） */
const fromHistory = ref(false)

const styles = computed(() => (app.result && app.result.styles) || [])
/** 当前这批生成的任务 ID：解锁按「任务 + 款式」计算，第 2 张起需要重新付费 */
const taskId = computed(() => String((app.result && app.result.taskId) || app.taskId || ''))
const unlockedKeys = computed(() => app.unlocks)
const current = computed(
  () => styles.value[activeIndex.value] || { styleId: '', name: '', len: '', face: '', cat: '', img: '' }
)
const beforeImg = computed(() => (app.result && app.result.before) || app.photoPath || DEMO_BEFORE)
const unlocked = computed(() => {
  const item = current.value
  if (!item || !item.styleId) return false
  // 优先用服务端在 result 里给出的 unlocked，本地缓存只兜底
  if (item.unlocked === true) return true
  return isUnlocked(item)
})
const lockedList = computed(() => styles.value.filter((item) => !isFailed(item) && !isUnlocked(item)))
const failedList = computed(() => styles.value.filter(isFailed))
const remaining = computed(() => lockedList.value.length)

const score = computed(() => {
  const value = Number(app.result && app.result.score)
  return Number.isFinite(value) && value > 0 ? Math.min(100, Math.round(value)) : 90
})
const faceShape = computed(() => (app.result && app.result.faceShape) || '鹅蛋脸')

const FACE_BASIS = {
  圆脸: ['下颌线柔和', '发长修饰脸宽'],
  方脸: ['下颌角明显', '侧分柔化轮廓'],
  长脸: ['脸部偏长', '刘海平衡比例'],
  鹅蛋脸: ['脸型均衡', '发际线高度适中'],
  心形脸: ['额头偏宽', '两侧留长收窄'],
  小脸: ['脸型紧凑', '发量提升存在感'],
  所有脸型: ['脸型百搭', '不挑五官']
}

const fit = computed(() => {
  const basis = FACE_BASIS[faceShape.value] || ['轮廓协调', '发流自然']
  const lv = score.value >= 90 ? '极高' : score.value >= 80 ? '高' : '中'
  return {
    score: score.value,
    lv,
    desc: `${faceShape.value} · ${basis.join(' · ')}`,
    tags: [current.value.cat || '热门款', '与脸型很搭', '好打理']
  }
})

const ringStyle = computed(() => {
  const p = Math.max(0, Math.min(100, fit.value.score))
  return `background: conic-gradient(from -90deg, #C95676 0% ${p}%, #F1DDE2 ${p}% 100%)`
})

// 原型 SVG 适配环：r=23，周长 2πr ≈ 144.5
const fitDash = computed(() => 144.5 - 144.5 * (Math.max(0, Math.min(100, fit.value.score)) / 100))

const primaryTitle = computed(() => {
  if (remaining.value > 0) return `解锁剩余 ${remaining.value} 款`
  if (failedList.value.length > 0) return '部分生成失败 · 点击重试'
  return '已解锁全部 · 保存高清图'
})
/** 只剩 1 款时按单张 ¥6 计价，多款按 ¥3/款 打包 */
const unlockPrice = computed(() => (remaining.value > 1 ? remaining.value * 3 : 6))
const primarySub = computed(() =>
  remaining.value > 0 ? `高清无水印 · ¥${unlockPrice.value}` : '高清无水印 · 可直接保存'
)
const unlockSub = computed(() =>
  remaining.value > 1
    ? `限时 ¥${unlockPrice.value} 全部解锁 · 高清无水印`
    : `¥${unlockPrice.value} 解锁高清无水印版`
)

const sliderRef = ref(null)

function measure() {
  if (sliderRef.value) sliderRef.value.measure()
}

function isUnlocked(item) {
  return !!item && !!item.styleId && unlockedKeys.value.includes(`${taskId.value}:${item.styleId}`)
}

/** 服务端标记失败的款式：无图即视为失败 */
function isFailed(item) {
  return !!item && (item.failed === true || !item.img)
}

/** 重试当前这款（对比位或失败缩略图点击） */
function retryCurrent() {
  retryOne(current.value)
}

async function retryOne(item) {
  if (!item || !item.styleId || retrying.value) return
  retrying.value = true
  try {
    await API.retryStyle(taskId.value, item.styleId)
    ui.toast('已重新提交生成')
    await pollRetry(item.styleId)
  } catch (e) {
    // request 已统一提示
  } finally {
    retrying.value = false
  }
}

/** 轮询直到这款出图（最多约 60 秒） */
async function pollRetry(styleId) {
  for (let i = 0; i < 40; i++) {
    await new Promise((resolve) => setTimeout(resolve, 1500))
    try {
      const res = await API.task(taskId.value)
      const data = (res && res.result) || res
      const list = (data && data.styles) || []
      const target = list.find((s) => s.styleId === styleId)
      if (target && target.img && !target.failed) {
        app.setResult({ ...data, taskId: taskId.value })
        ui.toast('重试成功')
        return
      }
    } catch (e) {
      // 继续轮询
    }
  }
  ui.toast('仍在生成中，稍后可回到试发记录查看')
}

/** 解锁当前任务下的款式（第 1 款免费 / 付费解锁） */
function unlockStyles(styleIds) {
  app.addUnlocks(styleIds.map((id) => `${taskId.value}:${id}`))
}

/** 打开付费墙：带上具体款式时默认按「单张解锁」计价 */
function openPay(styleId) {
  ui.payStyleId = styleId || ''
  if (styleId) ui.payPlan = 'one'
  ui.openSheet('pay')
}

function setView(next) {
  view.value = next === 'grid' ? 'grid' : 'compare'
  if (view.value === 'compare') {
    nextTick(measure)
  }
}

function selectStyle(i) {
  if (i < 0 || i >= styles.value.length) return
  if (isFailed(styles.value[i])) {
    retryOne(styles.value[i])
    return
  }
  // 未解锁内容只给付费提示，不切换、不展示
  if (!isUnlocked(styles.value[i])) {
    openPay(styles.value[i].styleId)
    return
  }
  activeIndex.value = i
  stopReplay()
}

function openFromGrid(i) {
  if (i < 0 || i >= styles.value.length) return
  if (isFailed(styles.value[i])) {
    retryOne(styles.value[i])
    return
  }
  if (!isUnlocked(styles.value[i])) {
    openPay(styles.value[i].styleId)
    return
  }
  selectStyle(i)
  setView('compare')
}

function stopReplay() {
  baPct.value = 50
  if (sliderRef.value) sliderRef.value.reset()
}

/** 开始拖动：只停掉自动播放动画，不能重置位置，否则会一直被拉回中间 */
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
    await saveRemoteImage(item.img, !unlocked.value)
    ui.toast(unlocked.value ? '已保存高清无水印图' : '已保存带水印效果图')
    track('save_image', { taskId: taskId.value, styleId: item.styleId, unlocked: unlocked.value })
  } catch (e) {
    ui.toast('保存失败，请检查相册权限')
  }
}

function onPrimary() {
  if (remaining.value > 0) {
    openPay()
    return
  }
  if (failedList.value.length > 0) {
    retryOne(failedList.value[0])
    return
  }
  saveImage()
}

function shuffleBatch() {
  ui.toast('已重新生成，去挑一批新款')
  uni.redirectTo({
    url: '/pages/styles/styles',
    fail: () => ui.toast('选款页暂未开放')
  })
}

function openCard() {
  uni.navigateTo({ url: '/pages/card/card' })
}

function goBack() {
  if (fromHistory.value) {
    uni.navigateBack({
      fail: () => uni.reLaunch({ url: '/pages/home/home' })
    })
    return
  }
  // 生成流程终点：不回到选发型/发色页，直接回首页
  uni.reLaunch({ url: '/pages/home/home' })
}

async function syncUnlocks() {
  try {
    const res = await API.unlocks()
    const list = Array.isArray(res) ? res : (res && (res.styleIds || res.ids)) || []
    // 服务端按「任务 + 款式」返回，转成 taskId:styleId 的键
    const keys = list
      .map((item) => (item && item.styleId ? `${item.taskId || 0}:${item.styleId}` : ''))
      .filter(Boolean)
    app.setUnlocks(keys)
  } catch (e) {
    // 接口已统一 toast，失败时沿用本地解锁状态
  }
}

onLoad(async (options) => {
  // 从「试发记录」进来的，返回时回到记录页；生成流程进来的，返回直接回首页
  fromHistory.value = !!(options && options.from === 'history')
  const hasStyles = !!(app.result && app.result.styles && app.result.styles.length)
  if (!hasStyles && app.taskId) {
    try {
      const res = await API.task(app.taskId)
      const data = res && (res.result || res)
      if (data && data.styles) {
        app.setResult({ ...data, taskId: data.taskId || app.taskId })
      }
    } catch (e) {
      // 接口已统一 toast
    }
  }
  if (!app.result || !app.result.styles || !app.result.styles.length) {
    ui.toast('效果已过期，请重新上传生成')
    uni.reLaunch({ url: '/pages/home/home' })
    return
  }

  // 初始仅免费开放 1 款，其余按付费解锁处理
  if (!styles.value.some(isUnlocked)) {
    const first = styles.value[0]
    if (first && first.styleId) unlockStyles([first.styleId])
  }
  track('result_view', { taskId: taskId.value, styleCount: styles.value.length })
})

onReady(() => {
  measure()
})

onShow(() => {
  syncUnlocks()
})
</script>

<style lang="scss" scoped>
/* 小程序水印合成画布：移出可视区，尺寸与 utils/save.js 中 750×1000 一致 */
.wm-canvas {
  position: fixed;
  left: -2000px;
  top: 0;
  width: 750px;
  height: 1000px;
  pointer-events: none;
}
.meta-main {
  flex: 1;
  min-width: 0;
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
  background: #F1DDE2;
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
/* 未解锁内容：整张卡片最大强度模糊，内容完全不可辨识 */
.r-thumb.locked .thumb-img {
  filter: blur(50px);
  transform: scale(2.4);
}
.res-cell.locked .thumb-img {
  filter: blur(70px);
  transform: scale(2.4);
}
.ub-thumb .thumb-img {
  filter: blur(30px);
  transform: scale(2.4);
}
.r-thumb-lock {
  flex-direction: column;
  gap: 2px;
}
.rtl-t {
  font-size: 9px;
  font-weight: 700;
  line-height: 1.1;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
}
.rc-lt {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.2px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
}
.rc-ls {
  font-size: 9px;
  line-height: 1.3;
  opacity: 0.92;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}
/* 生成失败：灰块占位，点击重试 */
.fail-thumb {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ececee;
  color: #8b9295;
  font-size: 11px;
  font-weight: 600;
}
.res-fail {
  height: 380px;
  border-radius: 18px;
  background: #ececee;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.res-fail-t {
  font-size: 13px;
  font-weight: 700;
  color: #6f7679;
}
.res-fail-d {
  font-size: 11px;
  color: #a3a9ac;
}
</style>
