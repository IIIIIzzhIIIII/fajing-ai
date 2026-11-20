<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="18" /></view>
      <view class="topbar-title">员工工作台</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <block v-if="!me.bound">
          <view v-if="!profileReady" class="auth-card">
            <view class="auth-t">尚未完成昵称与手机号授权</view>
            <view class="auth-d">店长需要按手机号添加员工，请先完成授权后再绑定员工身份。</view>
            <view class="auth-btn" @click="openAuth">去授权</view>
          </view>

          <view class="bind-card">
            <view class="bind-title">绑定员工身份</view>
            <view class="bind-desc">请向店长索取员工邀请码，输入邀请码和店长录入的手机号完成绑定。</view>
            <view class="field">
              <text class="label">邀请码</text>
              <input v-model="bindForm.inviteCode" class="input" maxlength="16" placeholder="例如 EMP001" />
            </view>
            <view class="field">
              <text class="label">手机号</text>
              <input v-model="bindForm.phone" class="input" type="number" maxlength="11" placeholder="店长录入的手机号" />
            </view>
            <view class="primary-btn" :class="{ disabled: binding }" @click="bind">
              {{ binding ? '绑定中…' : '确认绑定' }}
            </view>
          </view>
          <view class="tip-card">
            员工身份仅用于门店代客试发和顾客归因，不会改变顾客端的正常使用。
          </view>
        </block>

        <block v-else>
          <view class="hero-card">
            <view class="hero-top">
              <view>
                <view class="hero-store">{{ me.storeName }}</view>
                <view class="hero-name">{{ me.staffName }} · {{ me.role === 'EMPLOYEE' ? '发型师' : me.role }}</view>
              </view>
              <view class="hero-badge">{{ me.remaining }} 次</view>
            </view>
            <view class="hero-meta">今日已用 {{ me.todayUsed }} / {{ me.dailyLimit }} 次 · 已领取 {{ me.claimedCount }} 份</view>
            <view class="hero-actions">
              <view class="hero-btn primary" @click="startDemo">开始代客试发</view>
              <view class="hero-btn ghost" @click="refresh">刷新额度</view>
            </view>
          </view>

          <view class="section-title">最近试发</view>
          <view v-for="task in me.recentTasks || []" :key="task.id" class="task-item">
            <view class="task-main">
              <view class="task-title">试发 #{{ task.id }}</view>
              <view class="task-time">{{ formatTime(task.createdAt) }}</view>
            </view>
            <view class="task-status" :class="{ done: task.status === 'success' }">
              {{ task.status === 'success' ? '已完成' : '生成中' }}
            </view>
          </view>
          <view v-if="!(me.recentTasks || []).length" class="empty">还没有试发记录</view>

          <view class="tip-card">
            员工试发由平台承担 AI 成本，请用于真实顾客体验；恶意刷量会被限流。
          </view>
        </block>
      </view>
    </scroll-view>

    <FjLoginModal v-if="authVisible" @done="onAuthDone" @cancel="onAuthCancel" />
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { API } from '@/api/request'
import { goBack } from '@/utils/nav'
import FjLoginModal from '@/components/FjLoginModal.vue'

const me = ref({ bound: false })
const binding = ref(false)
const bindForm = reactive({ inviteCode: '', phone: '' })
const profile = ref({ nickname: '', phone: '' })
const authVisible = ref(false)

const profileReady = computed(() => Boolean(profile.value.nickname && profile.value.phone))

async function loadProfile() {
  try {
    profile.value = (await API.me()) || {}
    if (!bindForm.phone && profile.value.phone) {
      bindForm.phone = profile.value.phone
    }
  } catch (e) {
    // 未登录时保持空资料
  }
}

function openAuth() {
  authVisible.value = true
}

function onAuthDone() {
  authVisible.value = false
  loadProfile()
}

function onAuthCancel() {
  authVisible.value = false
}

function back() {
  goBack()
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function refresh() {
  try {
    me.value = (await API.employeeMe()) || { bound: false }
  } catch (e) { /* 已统一提示 */ }
}

async function bind() {
  if (binding.value) return
  if (!profileReady.value) {
    uni.showToast({ title: '请先完成昵称与手机号授权', icon: 'none' })
    openAuth()
    return
  }
  if (!bindForm.inviteCode.trim()) {
    uni.showToast({ title: '请输入邀请码', icon: 'none' })
    return
  }
  if (!/^1[3-9]\d{9}$/.test(bindForm.phone.trim())) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  binding.value = true
  try {
    me.value = await API.employeeBind({
      inviteCode: bindForm.inviteCode.trim(),
      phone: bindForm.phone.trim()
    })
    uni.showToast({ title: '绑定成功', icon: 'success' })
  } catch (e) {
    // request 已统一提示
  } finally {
    binding.value = false
  }
}

function startDemo() {
  uni.navigateTo({ url: '/pages/employee/demo' })
}

onShow(() => {
  refresh()
  loadProfile()
})
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px 8px;
}
.icon-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 50%;
  box-shadow: var(--sh-sm);
}
.topbar-title {
  font-size: 16px;
  font-weight: 700;
}
.scroll {
  height: calc(100vh - 56px);
}
.pad {
  padding: 8px 20px 32px;
}
.auth-card {
  padding: 18px;
  margin-bottom: 12px;
  background: #fff8fa;
  border: 1px solid #f4d6df;
  border-radius: 16px;
}
.auth-t {
  font-size: 14px;
  font-weight: 700;
  color: #8a4b5e;
}
.auth-d {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.7;
  color: #9a7f88;
}
.auth-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 18px;
  margin-top: 12px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d46a88, #b14a6b);
  border-radius: 999px;
}
.bind-card {
  padding: 22px;
  background: #fff;
  border-radius: 20px;
  box-shadow: var(--sh-sm);
}
.bind-title {
  font-size: 18px;
  font-weight: 800;
}
.bind-desc {
  margin-top: 6px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--muted);
}
.field {
  margin-top: 16px;
}
.label {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--muted);
}
.input {
  height: 44px;
  padding: 0 14px;
  font-size: 14px;
  background: #faf5f7;
  border: 1px solid #f0e0e5;
  border-radius: 12px;
}
.primary-btn {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 22px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d46a88, #b14a6b);
  border-radius: 999px;
}
.primary-btn.disabled {
  opacity: 0.6;
}
.hero-card {
  padding: 20px;
  color: #fff;
  background: linear-gradient(135deg, #3a2f34, #57414a 60%, #6d4a52);
  border-radius: 22px;
  box-shadow: 0 10px 26px rgba(58, 47, 52, 0.2);
}
.hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.hero-store {
  font-size: 11px;
  opacity: 0.72;
}
.hero-name {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 800;
}
.hero-badge {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #3a2f34;
  background: #f7dce4;
  border-radius: 999px;
}
.hero-meta {
  margin-top: 10px;
  font-size: 11px;
  opacity: 0.78;
}
.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.hero-btn {
  flex: 1;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  border-radius: 999px;
}
.hero-btn.primary {
  color: #3a2f34;
  background: #f7dce4;
}
.hero-btn.ghost {
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.35);
}
.section-title {
  margin: 22px 0 10px;
  font-size: 14px;
  font-weight: 800;
}
.task-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-sm);
}
.task-main {
  flex: 1;
}
.task-title {
  font-size: 13px;
  font-weight: 600;
}
.task-time {
  margin-top: 2px;
  font-size: 10px;
  color: var(--muted);
}
.task-status {
  padding: 4px 10px;
  font-size: 10px;
  color: #8a7a80;
  background: #f4eef0;
  border-radius: 999px;
}
.task-status.done {
  color: #2e7d5b;
  background: #e9f7f0;
}
.empty {
  padding: 30px 0;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}
.tip-card {
  margin-top: 16px;
  padding: 12px 14px;
  font-size: 10.5px;
  line-height: 1.7;
  color: #8a7a80;
  background: #fdf7f9;
  border-radius: 12px;
}
</style>
