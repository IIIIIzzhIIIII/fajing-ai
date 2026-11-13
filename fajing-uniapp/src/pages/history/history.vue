<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">试发记录</view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="pad">
        <view
          v-for="rec in records"
          :key="rec.key"
          class="hist-card"
          @click="openRecord(rec)"
        >
          <view class="hist-top">
            <view class="hist-title">{{ rec.title }}</view>
            <view class="hist-count">{{ rec.count }} 款</view>
            <view class="hist-del" @click.stop="removeRecord(rec)">
              <fj-icon name="trash" :size="13" color="#A39AA0" />
            </view>
          </view>
          <view class="hist-date">{{ rec.dateText }}</view>
          <view class="hist-thumbs">
            <view
              v-for="(s, i) in rec.styles"
              :key="(s.styleId || 's') + '-' + i"
              class="hist-thumb"
              :class="{ locked: !isUnlocked(rec, s, i) }"
            >
              <image v-if="s.img" :src="s.img" mode="aspectFill" class="hist-thumb-img" />
              <view v-if="!isUnlocked(rec, s, i)" class="hist-lock">
                <fj-icon name="lock" :size="14" color="#FFFFFF" />
                <text class="hist-lock-t">付费解锁</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="!records.length" class="empty" @click="goUpload" style="background:#fff;border-radius:16px;box-shadow:var(--sh-sm)">
          还没有试发记录<br />
          <text style="color:var(--pink-600);font-weight:700">去试第一款发型 →</text>
        </view>
      </view>
    </scroll-view>

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
  const raw = typeof ts === 'number' ? ts : String(ts).replace(/-/g, '/').replace('T', ' ')
  const d = new Date(raw)
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
  return {
    key: r.id || r.taskId || `rec-${i}`,
    recordId: r.id || '',
    taskId: r.taskId ? String(r.taskId) : '',
    title: r.title || r.styleName || (styles[0] && styles[0].name) || '我的试发记录',
    count: styles.length || Number(r.count) || 0,
    dateText: fmtDate(r.t || r.createdAt || r.createdTime || r.time || r.date),
    before: r.before || r.photoUrl || '',
    styles
  }
}

/** 每批生成第 1 款免费；从第 2 款起需要在该批次内付费解锁 */
function isUnlocked(rec, style, index) {
  if (index === 0) return true
  return !!style && !!style.styleId && app.unlocks.includes(`${rec.taskId}:${style.styleId}`)
}

async function loadRecords() {
  try {
    const res = await API.records()
    const list = Array.isArray(res) ? res : (res && (res.records || res.list)) || []
    records.value = list.map(normalizeRecord)
  } catch (e) {
    // 接口不可用时退回本地历史，接口返回空则视为确实没有记录
    records.value = (app.history || []).map(normalizeRecord)
  }
}

onShow(loadRecords)

function back() {
  uni.navigateBack({
    fail: () => uni.reLaunch({ url: '/pages/profile/profile' })
  })
}

function openRecord(rec) {
  if (!rec.styles.length) {
    ui.toast('这条记录已失效')
    return
  }
  app.setResult({ styles: rec.styles, before: rec.before, taskId: rec.taskId })
  uni.navigateTo({ url: '/pages/result/result?from=history' })
}

/** 删除单条试发记录（先确认，失败由 request 统一提示） */
async function removeRecord(rec) {
  if (!rec.recordId) {
    ui.toast('这条记录暂不支持删除')
    return
  }
  const confirmed = await new Promise((resolve) => {
    uni.showModal({
      title: '删除记录',
      content: '删除后不可恢复，确定删除这条试发记录？',
      confirmText: '删除',
      confirmColor: '#C95676',
      success: (res) => resolve(res.confirm)
    })
  })
  if (!confirmed) return
  try {
    await API.deleteRecord(rec.recordId)
    records.value = records.value.filter((item) => item.key !== rec.key)
    ui.toast('已删除')
  } catch (e) {
    // request 已统一 toast
  }
}

function goUpload() {
  uni.navigateTo({ url: '/pages/upload/upload' })
}
</script>

<style lang="scss" scoped>
.hist-card {
  display: block;
  background: #fff;
  border-radius: 16px;
  padding: 12px;
  box-shadow: var(--sh-sm);
  margin-bottom: 10px;
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
  padding: 2px 8px;
  border-radius: 99px;
}
.hist-del {
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #f7f3f5;
  display: flex;
  align-items: center;
  justify-content: center;
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
  position: relative;
}
.hist-thumb.locked .hist-thumb-img {
  filter: blur(16px);
  transform: scale(1.6);
}
.hist-lock {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: rgba(20, 16, 19, 0.24);
  color: #fff;
}
.hist-lock-t {
  font-size: 9px;
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.45);
}
.hist-thumb-img {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
