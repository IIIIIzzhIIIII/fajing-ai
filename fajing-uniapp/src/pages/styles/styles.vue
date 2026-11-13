<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back()"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">{{ browse ? '全部发型' : '选一款想试的' }}</view>
    </view>

    <view class="pad" style="padding-top:2px">
      <view v-if="!browse" class="seg">
        <view class="seg-item" :class="{ on: mode === 'hair' }" @click="setMode('hair')">只换发型</view>
        <view class="seg-item" :class="{ on: mode === 'color' }" @click="setMode('color')">发型 + 发色</view>
      </view>
      <view v-if="!browse" class="tiny" style="margin:8px 2px 14px">{{ modeHint }}</view>

      <view class="gender-tabs">
        <view class="gender-tab" :class="{ on: gender === 'female' }" data-g="female" @click="setGender('female')">
          <fj-icon name="user" :size="16" />
          <text>女款</text>
        </view>
        <view class="gender-tab" :class="{ on: gender === 'male' }" data-g="male" @click="setGender('male')">
          <fj-icon name="user" :size="16" />
          <text>男款</text>
        </view>
      </view>

      <view v-if="faceShape && !browse" class="rec-card">
        <view class="rec-ico"><fj-icon name="sparkles" :size="16" color="#C95676" /></view>
        <view class="rec-main">
          <view class="rec-t">AI 识别你的脸型：<text class="rec-face">{{ faceShape }}</text></view>
          <view class="rec-d">{{ recDesc }}</view>
        </view>
        <view class="rec-btn" @click="tryRecommended()">一键试</view>
      </view>
    </view>

    <scroll-view scroll-x :show-scrollbar="false" class="chip-row" style="padding-top:14px">
      <view
        v-for="c in cats"
        :key="c"
        class="chip"
        :class="{ on: cat === c }"
        @click="setCat(c)"
      >
        {{ c === '推荐' ? '为你推荐' : c }}
      </view>
    </scroll-view>

    <view v-if="mode === 'color' && !browse">
      <view style="display:flex;align-items:center;justify-content:space-between;padding:10px 20px 2px">
        <text style="font-size:11.5px;font-weight:700;color:var(--ink-2)">发色（可选 1 种）</text>
        <text style="font-size:10px;color:var(--muted)">{{ colorName }}</text>
      </view>
      <scroll-view scroll-x :show-scrollbar="false" class="color-row">
        <view
          v-for="c in colorList"
          :key="c.id"
          class="color-dot"
          :class="{ on: colorId === c.id }"
          @click="pickColor(c.id)"
        >
          <view class="color-swatch" :style="{ background: c.bg }">
            <fj-icon v-if="colorId === c.id" name="check" :size="13" color="#FFFFFF" />
          </view>
          <view class="color-name">{{ c.name }}</view>
        </view>
      </scroll-view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-top:12px">
      <view class="grid3">
        <view
          v-for="(s, i) in list"
          :key="s.id"
          class="style-card"
          :class="{ on: !browse && isSelected(s.id) }"
          @click="openStyle(s)"
        >
          <view class="style-thumb">
            <image v-if="styleImg(s)" class="style-img" :src="styleImg(s)" mode="aspectFill" />
            <view v-if="!browse && faceShape && matchesFace(s)" class="style-rec">适你</view>
            <view v-if="!browse && i === 0" class="style-free">免费</view>
            <view v-if="!browse && isSelected(s.id)" class="style-badge">{{ orderOf(s.id) }}</view>
          </view>
          <view class="style-name">{{ s.name }}</view>
          <view class="style-tagline">{{ tagline(s) }}</view>
        </view>
        <view v-if="!list.length" class="empty" style="grid-column:1/-1">这个分类还没有发型，换一个看看？</view>
      </view>
      <view class="tiny" style="text-align:center;padding:6px 20px 20px">{{ footHint }}</view>
    </scroll-view>

    <view v-if="!browse" class="bottom-bar">
      <view class="btn btn-primary btn-block" :class="{ 'is-disabled': !canGenerate }" @click="startGenerate()">
        <text>{{ btnLabel }}</text>
        <fj-icon name="arrow-right" :size="16" color="#FFFFFF" />
      </view>
    </view>

    <fj-sheet />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { STYLES, CATS, COLORS, PORTRAIT } from '@/mock/constants'

const app = useAppStore()
const ui = useUiStore()

const gender = ref(app.gender === 'male' ? 'male' : 'female')
const mode = ref(app.mode === 'color' ? 'color' : 'hair')
/** 浏览模式：从首页「全部发型」进入，只能看图鉴，不能选款生成 */
const browse = ref(false)
const faceShape = computed(() => (app.result && app.result.faceShape) || '')
const cat = ref(faceShape.value ? '推荐' : '全部')
const selected = ref(Array.isArray(app.selectedStyleIds) ? app.selectedStyleIds.slice(0, 4) : [])
const colorId = ref(app.selectedColorId || '')

const bank = ref({
  female: STYLES.female.slice(),
  male: STYLES.male.slice()
})
const colorList = ref(COLORS.slice())

const allStyles = computed(() => bank.value[gender.value] || [])

const modeHint = computed(() =>
  mode.value === 'color'
    ? '同时更换发型与发色，染发效果也能提前看到。'
    : '保留你原本的发色，只换剪裁和造型。'
)

const cats = computed(() => {
  const base = (CATS[gender.value] || ['全部']).slice()
  const remote = allStyles.value.map((s) => s.cat).filter(Boolean)
  const merged = Array.from(new Set(base.concat(remote)))
  return faceShape.value ? ['推荐'].concat(merged) : merged
})

function matchesFace(s) {
  const f = String((s && s.face) || '')
  return !!faceShape.value && (f.indexOf(faceShape.value) > -1 || f.indexOf('所有脸型') > -1)
}

const recList = computed(() => {
  const all = allStyles.value
  if (!faceShape.value) return all.slice(0, 4)
  const hit = all.filter(matchesFace)
  const rest = all.filter((s) => !matchesFace(s))
  return hit.concat(rest).slice(0, 4)
})

const recDesc = computed(() => {
  const matched = allStyles.value.filter(matchesFace).length
  return matched
    ? `这 ${Math.min(4, matched)} 款最修饰你的脸型，先看这几款`
    : '你的脸型很百搭，这 4 款最近很火'
})

const list = computed(() => {
  if (cat.value === '推荐') return recList.value
  if (cat.value === '全部') return allStyles.value
  return allStyles.value.filter((s) => s.cat === cat.value)
})

const colorName = computed(() => {
  const c = colorList.value.find((x) => x.id === colorId.value)
  return c ? c.name : '未选择'
})

const canGenerate = computed(() => selected.value.length > 0)
const btnLabel = computed(() =>
  selected.value.length ? `生成 ${selected.value.length} 款效果` : '请先选择发型'
)
const footHint = computed(() =>
  browse.value
    ? '点开可查看大图 · 想试这款发型，请先上传自己的照片'
    : '最多勾选 4 款 · 第 1 款免费出图，其余可解锁高清'
)

function styleImg(s) {
  if (!s) return ''
  if (s.img) return s.img
  if (s.en) return PORTRAIT(s.en, gender.value)
  return ''
}

function tagline(s) {
  return [s.len, s.face].filter(Boolean).join(' · ')
}

function isSelected(id) {
  return selected.value.indexOf(id) > -1
}

function orderOf(id) {
  return selected.value.indexOf(id) + 1
}

function back() {
  uni.navigateBack()
}

function setMode(m) {
  mode.value = m
}

function setGender(g) {
  if (gender.value === g) return
  gender.value = g
  cat.value = faceShape.value ? '推荐' : '全部'
  selected.value = []
  loadStyles()
}

function setCat(c) {
  cat.value = c
}

function pickColor(id) {
  colorId.value = colorId.value === id ? '' : id
}

function toggleStyle(id) {
  const i = selected.value.indexOf(id)
  if (i > -1) {
    selected.value.splice(i, 1)
  } else if (selected.value.length >= 4) {
    ui.toast('最多选 4 款，先取消一款吧')
  } else {
    selected.value.push(id)
  }
}

/** 浏览模式只看大图，选款模式才做勾选 */
function openStyle(s) {
  if (!browse.value) {
    toggleStyle(s.id)
    return
  }
  const query = [
    'id=' + encodeURIComponent(s.id || ''),
    'name=' + encodeURIComponent(s.name || ''),
    'len=' + encodeURIComponent(s.len || ''),
    'face=' + encodeURIComponent(s.face || ''),
    'cat=' + encodeURIComponent(s.cat || ''),
    'img=' + encodeURIComponent(styleImg(s) || '')
  ].join('&')
  uni.navigateTo({ url: '/pages/style-detail/style-detail?' + query })
}

function tryRecommended() {
  cat.value = '推荐'
  selected.value = recList.value.map((s) => s.id).slice(0, 4)
}

function normalizeStyle(s) {
  return {
    id: s.id || s.styleId,
    name: s.name || s.styleName || '发型',
    cat: s.cat || s.category || '',
    len: s.len || s.length || s.lenDesc || '',
    face: s.face || s.faceShape || '',
    en: s.en || s.enPrompt || '',
    img: s.img || s.image || s.imgUrl || s.imageUrl || ''
  }
}

function normalizeList(res) {
  const arr = Array.isArray(res) ? res : (res && (res.list || res.items || res.styles)) || []
  return arr.map(normalizeStyle).filter((s) => s.id)
}

async function loadStyles() {
  const g = gender.value
  try {
    const res = await API.hairstyles(g, '')
    const arr = normalizeList(res)
    if (arr.length) bank.value[g] = arr
  } catch (e) {
    // 后端未启动时保留 mock 数据，页面不空白
  }
}

async function loadColors() {
  try {
    const res = await API.colors()
    const arr = Array.isArray(res) ? res : (res && (res.list || res.items || res.colors)) || []
    if (arr.length) {
      colorList.value = arr.map((c) => ({
        id: c.id,
        name: c.name || '发色',
        en: c.en || c.enPrompt || c.name || '',
        bg: c.bg || c.hex || c.color || '#2B2622'
      }))
    }
  } catch (e) {
    // 保留 mock 发色
  }
}

function startGenerate() {
  if (!selected.value.length) return
  const ids = selected.value.slice()
  app.setSelection({
    gender: gender.value,
    mode: mode.value,
    styleIds: ids,
    colorId: colorId.value
  })
  try {
    const picked = allStyles.value.filter((s) => ids.indexOf(s.id) > -1)
    uni.setStorageSync('fajing_picked_styles', JSON.stringify(picked))
  } catch (e) {
    // 本地缓存失败不影响生成
  }
  if (app.quota.freeLeft <= 0) {
    ui.openSheet('quota')
    return
  }
  app.useFreeQuota()
  uni.navigateTo({ url: '/pages/generating/generating' })
}

onLoad((options) => {
  browse.value = !!(options && (options.view === '1' || options.mode === 'view'))
  loadStyles()
  loadColors()
})
</script>

<style lang="scss" scoped>
.rec-face {
  color: var(--pink-700);
}
.style-img {
  width: 100%;
  height: 100%;
}
.style-free {
  position: absolute;
  left: 5px;
  top: 5px;
  z-index: 2;
  font-size: 8.5px;
  font-weight: 700;
  padding: 1.5px 5px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.93);
  color: var(--pink-700);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
.btn-primary.is-disabled {
  background: #eadfe3;
  color: #b7a8af;
  box-shadow: none;
}
</style>
