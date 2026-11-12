<template>
  <view
    ref="wrapRef"
    class="ba-wrap"
    :class="{ locked: !unlocked, 'no-before': !before }"
    @touchstart="onStart"
    @touchmove="onMove"
    @touchend="onEnd"
    @touchcancel="onEnd"
    @mousedown.stop="onMouseDown"
  >
    <image class="ba-img" :src="after" mode="aspectFill" style="width:100%;height:100%" />
    <view class="ba-clip" :style="clipStyle">
      <image class="ba-img" :src="before" mode="aspectFill" style="width:100%;height:100%" />
    </view>

    <fj-watermark-mask v-if="!unlocked" :show="true" />

    <text class="ba-tag l">{{ beforeLabel }}</text>
    <text class="ba-tag r">{{ afterLabel }}</text>

    <view class="ba-handle" :style="{ left: pos + '%' }">
      <view class="knob">
        <fj-icon name="arrow-left" :size="16" />
        <fj-icon name="arrow-right" :size="16" style="margin-left:-4px" />
      </view>
    </view>

    <view v-if="!unlocked" class="ba-lock">
      <view class="lk">
        <fj-icon name="lock" :size="20" color="#C95676" />
      </view>
      <view class="ba-lock-t">这款效果已经生成好了</view>
      <view class="ba-lock-d">解锁后可查看高清大图，并保存无水印版本</view>
      <view class="btn btn-primary" @click.stop="$emit('lock')">解锁查看</view>
    </view>
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  before: { type: String, default: '' },
  after: { type: String, default: '' },
  unlocked: { type: Boolean, default: false },
  pct: { type: Number, default: 50 },
  beforeLabel: { type: String, default: '原图' },
  afterLabel: { type: String, default: 'AI 效果' }
})
const emit = defineEmits(['update:pct', 'lock', 'dragstart'])

const instance = getCurrentInstance()
const wrapRef = ref(null)
const pos = ref(clamp(props.pct))
const dragging = ref(false)
let rectLeft = 0
let rectWidth = 0
let pendingEvent = null
let startX = 0
let startY = 0
let axis = '' // '' 未判定 | 'x' 横向拖动 | 'y' 纵向滚动

function clamp(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return 50
  return Math.max(3, Math.min(97, n))
}

watch(
  () => props.pct,
  (next) => {
    if (!dragging.value) pos.value = clamp(next)
  }
)

const clipStyle = computed(() => `clip-path: inset(0 ${100 - pos.value}% 0 0)`)

/** H5 下直接读 DOM，比 selectorQuery 可靠；小程序仍走 selectorQuery */
function syncRect() {
  const raw = wrapRef.value
  const el = raw && (raw.$el || raw)
  if (el && typeof el.getBoundingClientRect === 'function') {
    const r = el.getBoundingClientRect()
    if (r && r.width) {
      rectLeft = r.left
      rectWidth = r.width
      return true
    }
  }
  return false
}

function measure() {
  if (syncRect()) {
    if (pendingEvent) {
      apply(pendingEvent)
      pendingEvent = null
    }
    return
  }
  uni
    .createSelectorQuery()
    .in(instance.proxy)
    .select('.ba-wrap')
    .boundingClientRect((res) => {
      if (res) {
        rectLeft = res.left
        rectWidth = res.width
        // 首次测量完成后立刻跟随触点，避免第一下拖动滞后
        if (pendingEvent) {
          apply(pendingEvent)
          pendingEvent = null
        }
      }
    })
    .exec()
}

function pointOf(e) {
  const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e
  const x = touch.clientX !== undefined ? touch.clientX : touch.pageX
  const y = touch.clientY !== undefined ? touch.clientY : touch.pageY
  return { x, y }
}

function apply(e) {
  // 兜底：measure 没取到尺寸时，拖动中再量一次
  if (!rectWidth) {
    syncRect()
  }
  if (!rectWidth) return
  const x = pointOf(e).x - rectLeft
  pos.value = clamp((x / rectWidth) * 100)
  emit('update:pct', Math.round(pos.value * 10) / 10)
}

function onStart(e) {
  if (!props.unlocked || !props.before) return
  const p = pointOf(e)
  startX = p.x
  startY = p.y
  axis = ''
  dragging.value = false
  pendingEvent = e
  measure()
}

function onMove(e) {
  const p = pointOf(e)
  if (!axis) {
    // 方向判定：横向才接管拖动，纵向交给页面滚动
    if (Math.abs(p.x - startX) < 3 && Math.abs(p.y - startY) < 3) return
    axis = Math.abs(p.x - startX) >= Math.abs(p.y - startY) ? 'x' : 'y'
    if (axis === 'x') {
      dragging.value = true
      emit('dragstart')
    }
  }
  if (axis !== 'x') return
  if (e.cancelable) e.preventDefault()
  apply(e)
}

function onEnd() {
  dragging.value = false
  axis = ''
  pendingEvent = null
}

/* ---------- H5 鼠标拖动：监听 window，拖出图片也不断 ---------- */
let mouseBound = false
function bindMouse() {
  // #ifdef H5
  if (mouseBound || typeof document === 'undefined') return
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  mouseBound = true
  // #endif
}
function unbindMouse() {
  // #ifdef H5
  if (!mouseBound || typeof document === 'undefined') return
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  mouseBound = false
  // #endif
}
function onMouseDown(e) {
  if (!props.unlocked || !props.before) return
  dragging.value = true
  axis = 'x'
  emit('dragstart')
  pendingEvent = e
  measure()
  apply(e)
  bindMouse()
}
function onMouseMove(e) {
  if (!dragging.value) return
  apply(e)
}
function onMouseUp() {
  dragging.value = false
  axis = ''
  unbindMouse()
}
onBeforeUnmount(unbindMouse)

function replay() {
  if (!props.unlocked) return
  const start = Date.now()
  if (replay.timer) clearInterval(replay.timer)
  replay.timer = setInterval(() => {
    const dt = Date.now() - start
    if (dt < 820) {
      pos.value = 12 + (88 - 12) * (1 - Math.pow(1 - dt / 820, 3))
    } else if (dt < 1300) {
      const k = (dt - 820) / 480
      pos.value = 88 + (50 - 88) * (k * k * (3 - 2 * k))
    } else {
      pos.value = 50
      clearInterval(replay.timer)
      replay.timer = null
    }
  }, 16)
}

/** 只停止自动播放动画，不改变当前滑块位置（拖动开始时调用） */
function stop() {
  if (replay.timer) {
    clearInterval(replay.timer)
    replay.timer = null
  }
}

function reset() {
  stop()
  pos.value = 50
}

defineExpose({ measure, replay, reset, stop })
</script>
