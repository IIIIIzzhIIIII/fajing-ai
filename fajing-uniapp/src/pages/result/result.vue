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
          <fj-compare-slider
            ref="sliderRef"
            v-model:pct="baPct"
            :before="beforeImg"
            :after="current.img"
            :unlocked="unlocked"
            @lock="ui.openSheet('pay')"
            @dragstart="stopReplay"
          />

          <view class="ba-replay" @click="replayCompare">
            <fj-icon name="refresh" :size="13" color="#8B9295" />
            <text>拖动滑块对比前后 · 点此重播</text>
          </view>

          <view class="fit-card">
            <view class="fit-score">
              <view class="fit-ring" :style="ringStyle">
                <view class="fit-ring-in">
                  <view class="fit-score-num">
                    {{ fit.score }}<text style="font-size:9px;font-weight:700">%</text>
                  </view>
                </view>
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

        <view v-if="remaining > 0" class="unlock-bar" @click="ui.openSheet('pay')">
          <view class="ub-thumbs">
            <view v-for="item in lockedList.slice(0, 3)" :key="item.styleId" class="ub-thumb">
              <image :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
            </view>
          </view>
          <view class="ub-main">
            <view class="ub-t">还有 {{ remaining }} 款效果已生成</view>
            <view class="ub-d">限时 ¥12 全部解锁 · 高清无水印</view>
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
              <image class="thumb-img" :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
              <text class="res-cell-idx">{{ i + 1 }}</text>
              <view v-if="!isUnlocked(item)" class="res-cell-lock">
                <view class="lk">
                  <fj-icon name="lock" :size="16" color="#C95676" />
                </view>
                <text style="font-size:10.5px;font-weight:700">解锁查看</text>
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
          <image class="thumb-img" :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
          <text class="r-thumb-idx">{{ i + 1 }}</text>
          <view v-if="!isUnlocked(item)" class="r-thumb-lock">
            <fj-icon name="lock" :size="15" color="#FFFFFF" />
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
import { DEMO_BEFORE } from '@/mock/constants'

const app = useAppStore()
const ui = useUiStore()

const view = ref('compare')
const activeIndex = ref(0)
const baPct = ref(50)

const styles = computed(() => (app.result && app.result.styles) || [])
const current = computed(
  () => styles.value[activeIndex.value] || { styleId: '', name: '', len: '', face: '', cat: '', img: '' }
)
const beforeImg = computed(() => (app.result && app.result.before) || app.photoPath || DEMO_BEFORE)
const unlocked = computed(() => !!current.value.styleId && app.unlocks.includes(current.value.styleId))
const lockedList = computed(() => styles.value.filter((item) => !isUnlocked(item)))
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

const primaryTitle = computed(() =>
  remaining.value > 0 ? `解锁剩余 ${remaining.value} 款` : '已解锁全部 · 保存高清图'
)
const primarySub = computed(() => (remaining.value > 0 ? '高清无水印 · ¥12' : '高清无水印 · 可直接保存'))

const sliderRef = ref(null)

function measure() {
  if (sliderRef.value) sliderRef.value.measure()
}

function isUnlocked(item) {
  return !!item && app.unlocks.includes(item.styleId)
}

function setView(next) {
  view.value = next === 'grid' ? 'grid' : 'compare'
  if (view.value === 'compare') {
    nextTick(measure)
  }
}

function selectStyle(i) {
  if (i < 0 || i >= styles.value.length) return
  activeIndex.value = i
  stopReplay()
}

function openFromGrid(i) {
  selectStyle(i)
  setView('compare')
}

function stopReplay() {
  baPct.value = 50
  if (sliderRef.value) sliderRef.value.reset()
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
  } catch (e) {
    ui.toast('保存失败，请检查相册权限')
  }
}

function onPrimary() {
  if (remaining.value > 0) {
    ui.openSheet('pay')
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
  uni.navigateBack({
    fail: () => uni.reLaunch({ url: '/pages/home/home' })
  })
}

async function syncUnlocks() {
  try {
    const res = await API.unlocks()
    const list = Array.isArray(res) ? res : (res && (res.styleIds || res.ids)) || []
    const ids = list.map((item) => (typeof item === 'string' ? item : item && item.styleId)).filter(Boolean)
    if (ids.length) app.addUnlocks(ids)
  } catch (e) {
    // 接口已统一 toast，失败时沿用本地解锁状态
  }
}

onLoad(async () => {
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
  }
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
  position: relative;
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
.r-thumb.locked .thumb-img {
  filter: blur(6px) saturate(0.9);
  transform: scale(1.12);
}
.res-cell.locked .thumb-img {
  filter: blur(11px) saturate(0.9);
  transform: scale(1.06);
}
</style>
