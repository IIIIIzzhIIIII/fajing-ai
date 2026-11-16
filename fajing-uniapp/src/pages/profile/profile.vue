<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="18" /></view>
      <view class="topbar-title">我的</view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="profile-hero">
        <view class="avatar">
          <image v-if="me.avatar" :src="me.avatar" mode="aspectFill" style="width:100%;height:100%" />
          <fj-icon v-else name="user" :size="26" color="#C95676" />
        </view>
        <view>
          <view style="font-size:17px;font-weight:800;letter-spacing:-.3px">{{ me.nickname || '发型试镜用户' }}</view>
          <view style="font-size:11px;color:var(--muted);margin-top:3px">{{ loginLabel }} · 试发记录已同步</view>
        </view>
      </view>

      <view style="margin:0 20px 18px;display:flex;gap:8px">
        <view style="flex:1;background:#fff;border-radius:16px;padding:12px;text-align:center;box-shadow:var(--sh-sm)">
          <view style="font-size:20px;font-weight:800;color:var(--pink-600)">{{ styleCount }}</view>
          <view style="font-size:10px;color:var(--muted);margin-top:2px">试过的发型</view>
        </view>
        <view style="flex:1;background:#fff;border-radius:16px;padding:12px;text-align:center;box-shadow:var(--sh-sm)">
          <view style="font-size:20px;font-weight:800;color:var(--ink)">
            {{ app.quota.freeLeft }}<text style="font-size:11px;color:var(--muted)">次</text>
          </view>
          <view style="font-size:10px;color:var(--muted);margin-top:2px">免费生成次数</view>
        </view>
        <view style="flex:1;background:#fff;border-radius:16px;padding:12px;text-align:center;box-shadow:var(--sh-sm)">
          <view style="font-size:20px;font-weight:800;color:var(--gold)">{{ app.unlockedCount }}</view>
          <view style="font-size:10px;color:var(--muted);margin-top:2px">已解锁高清</view>
        </view>
      </view>

      <view class="menu" style="margin-top:18px">
        <view class="menu-item" @click="openHistory">
          <view class="menu-ico"><fj-icon name="clock" :size="16" color="#C95676" /></view>
          <text>我的试发记录</text>
          <text class="menu-count">{{ recordCount }}</text>
          <view class="chev"><fj-icon name="chevron-right" :size="16" color="#C9BFC3" /></view>
        </view>
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

      <view class="logout-btn" @click="logout">退出登录</view>

      <view class="tiny" style="text-align:center;padding:20px">发镜 AI v0.1</view>
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
import { clearToken } from '@/utils/auth'
import { goBack } from '@/utils/nav'

const app = useAppStore()
const ui = useUiStore()

const me = ref({ nickname: '', avatar: '' })
/** 登录方式文案：H5 为游客登录，小程序为微信登录 */
let loginLabel = '微信登录'
// #ifdef H5
loginLabel = '游客登录'
// #endif
/** 试发记录条数 / 试过的发型款数：以服务端记录为准，生成后回到本页会刷新 */
const recordCount = ref(0)
const styleCount = ref(0)

async function loadMe() {
  try {
    const data = await API.me()
    if (data) me.value = data
  } catch (e) {
    // 未登录时保持默认昵称
  }
}

async function refreshQuota() {
  try {
    const q = await API.quota()
    // 额度以服务端为唯一权威：整包覆盖本地展示值
    if (q) app.setQuota(q)
  } catch (e) {
    /* request 已统一 toast */
  }
}

async function loadStats() {
  try {
    const res = await API.records()
    const list = Array.isArray(res) ? res : (res && (res.records || res.list)) || []
    recordCount.value = list.length
    styleCount.value = list.reduce((sum, r) => {
      const items = Array.isArray(r.items) ? r.items : Array.isArray(r.styles) ? r.styles : []
      const ids = Array.isArray(r.styleIds) ? r.styleIds : []
      const n = items.length || ids.length || Number(r.count) || 0
      return sum + (n || 1)
    }, 0)
  } catch (e) {
    // 接口不可用时退回本地历史
    recordCount.value = app.history.length
    styleCount.value = app.history.length
  }
}

onShow(() => {
  refreshQuota()
  loadStats()
  loadMe()
})

function back() {
  goBack()
}

function logout() {
  clearToken()
  app.resetAll()
  ui.close()
  uni.reLaunch({ url: '/pages/login/login' })
}

function openHistory() {
  uni.navigateTo({ url: '/pages/history/history' })
}

function openPrivacy() {
  ui.openSheet('privacy')
}

function contact() {
  ui.toast('客服微信：fajing-ai')
}

function about() {
  ui.toast('发镜 AI v0.1 · 剪之前，先看见')
}
</script>

<style lang="scss" scoped>
.logout-btn {
  margin: 18px 20px 0;
  padding: 12px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #8b9295;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-sm);
}
.menu-count {
  margin-left: auto;
  font-size: 11px;
  font-weight: 700;
  color: var(--pink-700);
  background: var(--pink-50);
  padding: 2px 8px;
  border-radius: 99px;
}
.chev {
  margin-left: auto;
  display: flex;
  align-items: center;
}
.menu-count + .chev {
  margin-left: 8px;
}
</style>
