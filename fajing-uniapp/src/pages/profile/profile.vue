<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="18" /></view>
      <view class="topbar-title">我的</view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="profile-hero">
        <view class="avatar"><fj-icon name="user" :size="26" color="#C95676" /></view>
        <view>
          <view style="font-size:17px;font-weight:800;letter-spacing:-.3px">未登录</view>
          <view style="font-size:11px;color:var(--muted);margin-top:3px">登录后可跨设备同步试发记录</view>
        </view>
        <view class="pill" style="margin-left:auto" @click="login">登录</view>
      </view>

      <view style="margin:0 20px 18px;display:flex;gap:8px">
        <view style="flex:1;background:#fff;border-radius:16px;padding:12px;text-align:center;box-shadow:var(--sh-sm)">
          <view style="font-size:20px;font-weight:800;color:var(--pink-600)">{{ app.totalTried }}</view>
          <view style="font-size:10px;color:var(--muted);margin-top:2px">试过的发型</view>
        </view>
        <view style="flex:1;background:#fff;border-radius:16px;padding:12px;text-align:center;box-shadow:var(--sh-sm)">
          <view style="font-size:20px;font-weight:800;color:var(--ink)">
            {{ app.quota.freeLeft }}<text style="font-size:11px;color:var(--muted)">次</text>
          </view>
          <view style="font-size:10px;color:var(--muted);margin-top:2px">今日免费次数</view>
        </view>
        <view style="flex:1;background:#fff;border-radius:16px;padding:12px;text-align:center;box-shadow:var(--sh-sm)">
          <view style="font-size:20px;font-weight:800;color:var(--gold)">{{ app.unlockedCount }}</view>
          <view style="font-size:10px;color:var(--muted);margin-top:2px">已解锁高清</view>
        </view>
      </view>

      <view style="padding:0 20px 10px;font-size:13px;font-weight:800">我的试发记录</view>
      <view style="padding:0 20px">
        <view
          v-for="rec in records"
          :key="rec.key"
          class="hist-item hist-card"
          @click="openRecord(rec)"
        >
          <view class="hist-top">
            <view class="hist-title">{{ rec.title }}</view>
            <view class="hist-count">{{ rec.count }} 款</view>
          </view>
          <view class="hist-date">{{ rec.dateText }}</view>
          <view class="hist-thumbs">
            <view v-for="i in 4" :key="i" class="hist-thumb">
              <image v-if="rec.thumbs[i - 1]" :src="rec.thumbs[i - 1]" mode="aspectFill" class="hist-thumb-img" />
              <fj-icon v-else name="image" :size="15" color="#C9BFC3" />
            </view>
          </view>
        </view>
        <view v-if="!records.length" class="empty" @click="goUpload" style="background:#fff;border-radius:16px;box-shadow:var(--sh-sm)">
          还没有试发记录<br />
          <text style="color:var(--pink-600);font-weight:700">去试第一款发型 →</text>
        </view>
      </view>

      <view class="menu" style="margin-top:18px">
        <view class="menu-item" @click="openPrivacy">
          <view class="menu-ico"><fj-icon name="shield" :size="16" color="#C95676" /></view>
          <text>隐私与数据</text>
          <view class="chev"><fj-icon name="chevron-right" :size="16" color="#C9BFC3" /></view>
        </view>
        <view class="menu-item" @click="contact">
          <view class="menu-ico"><fj-icon name="message" :size="16" color="#C95676" /></view>
          <text>联系客服</text>
          <view class="chev"><fj-icon name="chevron-right" :size="16" color="#C9BFC3" /></view>
        </view>
        <view class="menu-item" @click="about">
          <view class="menu-ico"><fj-icon name="info" :size="16" color="#C95676" /></view>
          <text>关于我们</text>
          <view class="chev"><fj-icon name="chevron-right" :size="16" color="#C9BFC3" /></view>
        </view>
      </view>

      <view class="tiny" style="text-align:center;padding:20px">发镜 AI v0.1 · 原型演示版本</view>
    </scroll-view>

    <fj-tabbar active="profile" />
    <fj-sheet />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'

const app = useAppStore()
const ui = useUiStore()

const records = ref([])

function fmtDate(ts) {
  if (!ts) return ''
  const d = new Date(typeof ts === 'number' ? ts : String(ts).replace(/-/g, '/'))
  if (Number.isNaN(d.getTime())) return String(ts)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}月${d.getDate()}日 ${p(d.getHours())}:${p(d.getMinutes())}`
}

function normalizeRecord(r, i) {
  const items = Array.isArray(r.items)
    ? r.items
    : Array.isArray(r.styles)
      ? r.styles
      : Array.isArray(r.results)
        ? r.results
        : []
  const styles = items
    .map((it) => ({
      styleId: it.styleId || it.id || '',
      name: it.name || it.styleName || '',
      img: it.img || it.image || it.url || ''
    }))
    .filter((it) => it.styleId || it.img)
  const thumbs = styles.map((it) => it.img).filter(Boolean).slice(0, 4)
  return {
    key: r.id || r.taskId || `rec-${i}`,
    title: r.title || r.styleName || (styles[0] && styles[0].name) || '我的试发记录',
    count: styles.length || Number(r.count) || 0,
    dateText: fmtDate(r.t || r.createdAt || r.createdTime || r.time || r.date),
    thumbs,
    styles
  }
}

async function loadRecords() {
  let list = []
  try {
    const res = await API.records()
    list = Array.isArray(res) ? res : (res && (res.records || res.list)) || []
  } catch (e) {
    list = []
  }
  if (!Array.isArray(list) || !list.length) list = app.history || []
  records.value = list.map(normalizeRecord)
}

async function refreshQuota() {
  try {
    const q = await API.quota()
    if (q && typeof q.freeLeft === 'number') app.quota = { ...app.quota, ...q }
  } catch (e) {
    /* request 已统一 toast */
  }
}

onShow(() => {
  loadRecords()
  refreshQuota()
})

function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/home/home' })
}

function login() {
  ui.toast('原型演示：微信一键登录')
}

function openRecord(rec) {
  if (!rec.styles.length) {
    ui.toast('这条记录已失效')
    return
  }
  app.setResult({ styles: rec.styles })
  uni.navigateTo({ url: '/pages/result/result' })
}

function goUpload() {
  uni.navigateTo({ url: '/pages/upload/upload' })
}

function openPrivacy() {
  ui.openSheet('privacy')
}

function contact() {
  ui.toast('客服微信：fajing-ai（原型演示）')
}

function about() {
  ui.toast('发镜 AI v0.1 · 剪之前，先看见')
}
</script>

<style lang="scss" scoped>
.hist-card {
  display: block;
}
.hist-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hist-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hist-count {
  flex: none;
  font-size: 10.5px;
  font-weight: 700;
  color: var(--pink-700);
  background: var(--pink-50);
  padding: 2px 7px;
  border-radius: 99px;
}
.hist-date {
  font-size: 10.5px;
  color: var(--muted);
  margin-top: 3px;
}
.hist-thumbs {
  display: flex;
  gap: 6px;
  margin-top: 9px;
}
.hist-thumb {
  flex: 1;
  min-width: 0;
  aspect-ratio: 3 / 3.8;
  border-radius: 9px;
  overflow: hidden;
  background: #eee6ea;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hist-thumb-img {
  width: 100%;
  height: 100%;
  display: block;
}
.chev {
  margin-left: auto;
  display: flex;
  align-items: center;
}
</style>
