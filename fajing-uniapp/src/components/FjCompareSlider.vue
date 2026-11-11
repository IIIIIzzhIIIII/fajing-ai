<template>
  <view
    class="ba-wrap"
    :class="{ locked: !unlocked, 'no-before': !before }"
    @touchstart="onStart"
    @touchmove.stop="onMove"
    @touchend="onEnd"
    @touchcancel="onEnd"
    @mousedown.stop="onStart"
    @mousemove.stop="onMove"
    @mouseup="onEnd"
    @mouseleave="onEnd"
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
import { computed, getCurrentInstance, ref, watch } from 'vue'

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
const pos = ref(clamp(props.pct))
const dragging = ref(false)
let rectLeft = 0
let rectWidth = 0

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

function measure() {
  uni
    .createSelectorQuery()
    .in(instance.proxy)
    .select('.ba-wrap')
    .boundingClientRect((res) => {
      if (res) {
        rectLeft = res.left
        rectWidth = res.width
      }
    })
    .exec()
}

function pointerX(e) {
  const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e
  return touch.clientX !== undefined ? touch.clientX : touch.pageX
}

function apply(e) {
  if (!rectWidth) return
  const x = pointerX(e) - rectLeft
  pos.value = clamp((x / rectWidth) * 100)
  emit('update:pct', Math.round(pos.value * 10) / 10)
}

function onStart(e) {
  if (!props.unlocked || !props.before) return
  dragging.value = true
  emit('dragstart')
  measure()
  apply(e)
}

function onMove(e) {
  if (!dragging.value) return
  apply(e)
}

function onEnd() {
  dragging.value = false
}

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

function reset() {
  if (replay.timer) {
    clearInterval(replay.timer)
    replay.timer = null
  }
  pos.value = 50
}

defineExpose({ measure, replay, reset })
</script>

<style lang="scss" scoped>
.ba-wrap.locked .ba-img {
  filter: blur(15px) saturate(1.05);
  transform: scale(1.04);
}
</style>
