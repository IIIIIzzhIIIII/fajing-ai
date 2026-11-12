<template>
  <view class="page active" id="page-generating">
    <view class="gen-wrap">
      <view class="ring">
        <!-- #ifdef H5 -->
        <svg width="150" height="150" viewBox="0 0 150 150">
          <defs>
            <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#E8A0B4" />
              <stop offset="55%" stop-color="#C95676" />
              <stop offset="100%" stop-color="#D98A63" />
            </linearGradient>
          </defs>
          <circle class="ring-bg" cx="75" cy="75" r="64" fill="none" stroke-width="8" />
          <circle
            class="ring-fg"
            cx="75"
            cy="75"
            r="64"
            fill="none"
            stroke-width="8"
            stroke-dasharray="402"
            :stroke-dashoffset="dashOffset"
          />
        </svg>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <view class="ring-arc" :style="ringStyle" />
        <!-- #endif -->
        <view class="ring-core">
          <view class="ring-num">{{ progress }}<text style="font-size:13px">%</text></view>
          <view class="ring-unit">GENERATING</view>
        </view>
      </view>

      <view class="gen-title">{{ title }}</view>
      <view class="gen-count">已出图 {{ shown }} / {{ total }} 张</view>

      <view class="gen-thumbs">
        <view v-for="(t, i) in thumbs" :key="t.key" class="gen-thumb" :class="{ on: i < shown }">
          <image v-if="t.img" class="gen-thumb-img" :src="t.img" mode="aspectFill" />
          <text class="gen-thumb-n">{{ t.n }}</text>
        </view>
      </view>

      <view class="gen-steps">
        <view
          v-for="(s, i) in steps"
          :key="i"
          class="gen-step"
          :class="{ on: i === stage, done: i < stage }"
        >
          <view class="step-ico">
            <fj-icon v-if="i < stage" name="check" :size="13" color="#FFFFFF" />
            <text v-else>{{ i + 1 }}</text>
          </view>
          <view class="step-t">{{ s }}</view>
        </view>
      </view>

      <view class="gen-hint">
        <view>照片仅用于本次生成，生成结束后</view>
        <view>原始照片将在 24 小时内自动删除</view>
      </view>
    </view>

    <fj-sheet />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { STYLES, COLORS, PORTRAIT, DEMO_BEFORE } from '@/mock/constants'

const app = useAppStore()
const ui = useUiStore()

const steps = ['识别人脸 · 定位五官与发际线', '匹配发型 · 调整轮廓与发量', '渲染效果 · 融合光影与发丝']
const titles = ['正在识别人脸特征…', '匹配最适合你的发型…', '渲染效果中…']

const progress = ref(0)
const apiProgress = ref(null)
/** 服务端已出图数量，有值时进度条按真实张数展示 */
const apiDone = ref(0)
const picked = ref([])

let taskId = ''
let pollTimer = null
let simTimer = null
let simStart = 0
let localMode = false
let finished = false
let failures = 0

const total = computed(() => Math.min(4, Math.max(1, picked.value.length || (app.selectedStyleIds || []).length || 4)))

const thumbs = computed(() => {
  const list = []
  for (let i = 0; i < total.value; i++) {
    const s = picked.value[i]
    list.push({
      key: s && s.id ? s.id : `slot-${i}`,
      n: i + 1,
      img: s ? imgOf(s) : ''
    })
  }
  return list
})

const shown = computed(() => {
  if (apiProgress.value !== null) return Math.min(total.value, apiDone.value)
  return Math.min(total.value, Math.floor((progress.value / 100) * (total.value + 1)))
})

const stage = computed(() => (progress.value < 25 ? 0 : progress.value < 60 ? 1 : 2))
const title = computed(() => titles[stage.value])

const ringStyle = computed(() => {
  const p = Math.max(0, Math.min(100, progress.value))
  const deg = p * 3.6
  return {
    background: `conic-gradient(from -90deg, #E8A0B4 0deg, #C95676 ${deg * 0.55}deg, #D98A63 ${deg}deg, rgba(255,255,255,.10) ${deg}deg 360deg)`
  }
})

// 原型 SVG 圆环：周长 2πr ≈ 402
const dashOffset = computed(() => 402 - 402 * (Math.max(0, Math.min(100, progress.value)) / 100))

function imgOf(s) {
  if (!s) return ''
  if (s.img) return s.img
  const color = COLORS.find((c) => c.id === app.selectedColorId)
  const suffix = color ? `, ${color.en || color.name} hair color` : ''
  return s.en ? PORTRAIT(s.en + suffix, app.gender) : ''
}

function loadPicked() {
  let cached = []
  try {
    const raw = uni.getStorageSync('fajing_picked_styles')
    if (raw) cached = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (e) {
    cached = []
  }
  const ids = app.selectedStyleIds || []
  if (Array.isArray(cached) && cached.length) {
    const matched = cached.filter((s) => ids.indexOf(s.id) > -1)
    if (matched.length) return matched
  }
  const all = STYLES[app.gender] || []
  return ids.map((id) => all.find((s) => s.id === id)).filter(Boolean)
}

function tickSim() {
  if (finished || apiProgress.value !== null) return
  const target = localMode ? 100 : 90
  const elapsed = Date.now() - simStart
  const p = Math.round(Math.min(target, (target * elapsed) / 6000))
  if (p > progress.value) progress.value = p
  if (localMode && progress.value >= 100) finishLocal()
}

function startSimulate() {
  stopSimulate()
  simStart = Date.now()
  simTimer = setInterval(tickSim, 120)
  tickSim()
}

function stopSimulate() {
  if (simTimer) {
    clearInterval(simTimer)
    simTimer = null
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function stopAll() {
  stopSimulate()
  stopPolling()
}

function startPolling() {
  stopPolling()
  pollTask()
  pollTimer = setInterval(pollTask, 1000)
}

async function pollTask() {
  if (!taskId || finished) return
  try {
    const data = await API.task(taskId)
    failures = 0
    if (!data) return
    if (typeof data.progress === 'number') {
      apiProgress.value = data.progress
      progress.value = Math.max(progress.value, Math.min(99, Math.round(data.progress)))
    }
    if (typeof data.done === 'number') {
      apiDone.value = data.done
    }
    if (data.status === 'success') {
      finishSuccess(data.result)
    } else if (data.status === 'failed') {
      finishFail()
    }
  } catch (e) {
    failures += 1
    if (failures >= 3) fallbackLocal()
  }
}

async function submit() {
  try {
    const res = await API.generate({
      photoUrl: app.photoUrl || app.photoPath,
      gender: app.gender,
      mode: app.mode,
      styleIds: app.selectedStyleIds,
      colorId: app.selectedColorId
    })
    const id = res && (res.taskId || res.id)
    if (!id) throw new Error('missing taskId')
    taskId = id
    app.taskId = id
    startPolling()
  } catch (e) {
    fallbackLocal()
  }
}

function fallbackLocal() {
  if (finished || localMode) return
  localMode = true
  apiProgress.value = null
  taskId = ''
  app.taskId = ''
  stopPolling()
  simStart = Date.now()
  if (!simTimer) simTimer = setInterval(tickSim, 120)
}

function finishSuccess(result) {
  if (finished) return
  finished = true
  stopAll()
  progress.value = 100
  const payload = Object.assign({}, result || {})
  if (!payload.taskId) payload.taskId = taskId
  app.setResult(payload)
  setTimeout(() => {
    uni.redirectTo({ url: '/pages/result/result' })
  }, 320)
}

function finishFail() {
  if (finished) return
  finished = true
  stopAll()
  app.taskId = ''
  ui.toast('生成失败，请重试')
  setTimeout(() => {
    uni.navigateBack()
  }, 900)
}

function buildLocalResult() {
  const list = picked.value.length ? picked.value : (STYLES[app.gender] || []).slice(0, 1)
  const styles = list.map((s, i) => ({
    styleId: s.id,
    name: s.name,
    len: s.len,
    face: s.face,
    cat: s.cat,
    img: imgOf(s),
    unlocked: i === 0
  }))
  return {
    before: app.photoUrl || app.photoPath || DEMO_BEFORE,
    faceShape: (app.result && app.result.faceShape) || '',
    score: 92,
    styles
  }
}

function finishLocal() {
  if (finished) return
  finished = true
  stopAll()
  progress.value = 100
  app.taskId = ''
  app.setResult(buildLocalResult())
  setTimeout(() => {
    uni.redirectTo({ url: '/pages/result/result' })
  }, 320)
}

onLoad(() => {
  picked.value = loadPicked()
  startSimulate()
  if (app.taskId) {
    taskId = app.taskId
    startPolling()
    return
  }
  submit()
})

onShow(() => {
  if (finished) return
  if (app.taskId && !pollTimer) {
    taskId = app.taskId
    startPolling()
  }
})

onUnload(() => {
  stopAll()
})
</script>

<style lang="scss" scoped>
.ring {
  position: relative;
  width: 150px;
  height: 150px;
}
.ring-arc {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 8px), #000 calc(100% - 7px));
  mask: radial-gradient(farthest-side, transparent calc(100% - 8px), #000 calc(100% - 7px));
  transition: background 0.18s linear;
}
.ring-core {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.gen-thumb-img {
  width: 100%;
  height: 100%;
  opacity: 0;
  transform: scale(1.16);
  filter: blur(10px) saturate(0.5);
  transition: opacity 0.7s ease, filter 0.9s ease, transform 1s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.gen-thumb.on .gen-thumb-img {
  opacity: 1;
  transform: none;
  filter: none;
}
</style>
