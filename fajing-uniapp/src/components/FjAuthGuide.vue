<template>
  <view class="auth-guide">
    <!-- 步骤指示 -->
    <view class="ag-steps">
      <view class="ag-step" :class="{ on: step >= 1 }" />
      <view class="ag-step" :class="{ on: step >= 2 }" />
    </view>

    <!-- 步骤 1：头像与昵称 -->
    <template v-if="step === 1">
      <view class="ag-title">授权头像与昵称</view>
      <view class="ag-sub">方便识别你的试发记录，可随时在个人中心修改</view>

      <view class="ag-avatar-row">
        <!-- #ifdef MP-WEIXIN -->
        <button class="ag-avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
          <image v-if="avatarUrl" :src="avatarUrl" mode="aspectFill" class="ag-avatar-img" />
          <fj-icon v-else name="user" :size="28" color="#C95676" />
        </button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <view class="ag-avatar-btn" @click="chooseAvatarManual">
          <image v-if="avatarUrl" :src="avatarUrl" mode="aspectFill" class="ag-avatar-img" />
          <fj-icon v-else name="user" :size="28" color="#C95676" />
        </view>
        <!-- #endif -->
        <text class="ag-avatar-tip">{{ avatarUrl ? '点击可更换头像' : '点击选择微信头像' }}</text>
      </view>

      <view class="ag-field">
        <text class="ag-label">昵称</text>
        <!-- #ifdef MP-WEIXIN -->
        <input
          class="ag-input"
          type="nickname"
          :value="nickname"
          placeholder="点击使用微信昵称"
          maxlength="20"
          @input="onNicknameInput"
        />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <input
          class="ag-input"
          type="text"
          :value="nickname"
          placeholder="请输入昵称"
          maxlength="20"
          @input="onNicknameInput"
        />
        <!-- #endif -->
        <text class="ag-count">{{ nickname.length }}/20</text>
      </view>

      <view class="ag-actions">
        <button class="ag-btn ghost" @click="goPhoneStep">暂不设置</button>
        <button class="ag-btn primary" :loading="saving" @click="saveProfile">保存并继续</button>
      </view>
    </template>

    <!-- 步骤 2：手机号授权 -->
    <template v-else>
      <view class="ag-title">授权手机号</view>
      <view class="ag-sub">用于账号安全和订单通知，不会在社区中展示</view>

      <view class="ag-phone-card">
        <view class="ag-phone-ico"><fj-icon name="shield" :size="18" color="#C95676" /></view>
        <view class="ag-phone-main">
          <text class="ag-phone-t">微信绑定手机号</text>
          <text class="ag-phone-d">
            {{ phoneBound ? phoneDisplay + ' 已绑定' : '一键授权，无需手动输入验证码' }}
          </text>
        </view>
        <fj-icon v-if="phoneBound" name="check-circle" :size="18" color="#3F9E6B" />
      </view>

      <!-- #ifdef MP-WEIXIN -->
      <button
        class="ag-btn primary block"
        open-type="getPhoneNumber"
        :loading="binding"
        :disabled="binding || phoneBound"
        @getphonenumber="onGetPhone"
      >
        {{ phoneBound ? '已完成手机号授权' : '微信一键授权手机号' }}
      </button>
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="ag-phone-input-wrap">
        <input
          v-model="mockPhone"
          class="ag-phone-input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
        />
      </view>
      <button class="ag-btn primary block" :loading="binding" :disabled="binding || phoneBound" @click="bindMockPhone">
        {{ phoneBound ? '已完成手机号授权' : '确认绑定手机号' }}
      </button>
      <text class="ag-mock-tip">H5 预览环境不发送短信，手机号为模拟绑定</text>
      <!-- #endif -->

      <button v-if="phoneBound" class="ag-btn primary block" @click="finish">完成</button>
      <button class="ag-skip" @click="cancel">暂不授权</button>
      <text class="ag-cancel-tip">手机号为必填项，未授权将无法上传照片和充值</text>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { upload, API } from '@/api/request'

const props = defineProps({
  initialNickname: { type: String, default: '' },
  initialAvatar: { type: String, default: '' },
  initialPhone: { type: String, default: '' }
})
const emit = defineEmits(['done', 'cancel'])

const step = ref(1)
const nickname = ref(props.initialNickname || '')
const avatarUrl = ref(props.initialAvatar || '')
const avatarChanged = ref(false)
const saving = ref(false)
const binding = ref(false)
const mockPhone = ref('')
const boundPhone = ref(props.initialPhone || '')
const phoneBound = ref(!!props.initialPhone)

const phoneDisplay = computed(() => {
  const p = boundPhone.value || ''
  return p.length >= 7 ? p.slice(0, 3) + '****' + p.slice(-4) : p
})

function onNicknameInput(e) {
  nickname.value = (e.detail && e.detail.value) || ''
}

async function onChooseAvatar(e) {
  const tempPath = e && e.detail && e.detail.avatarUrl
  if (!tempPath) return
  await uploadAvatar(tempPath)
}

function chooseAvatarManual() {
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      const path = res.tempFilePaths && res.tempFilePaths[0]
      if (path) await uploadAvatar(path)
    }
  })
}

async function uploadAvatar(tempPath) {
  try {
    uni.showLoading({ title: '上传中…' })
    const data = await upload(tempPath)
    if (data && data.url) {
      avatarUrl.value = data.url
      avatarChanged.value = true
    }
  } catch (e) {
    // upload 内部已 toast
  } finally {
    uni.hideLoading()
  }
}

/** 保存头像昵称后进入手机号授权步骤 */
async function saveProfile() {
  if (saving.value) return
  const clean = nickname.value.trim()
  const payload = {}
  if (clean && clean !== props.initialNickname) payload.nickname = clean
  if (avatarChanged.value && avatarUrl.value) payload.avatar = avatarUrl.value
  if (!Object.keys(payload).length) {
    step.value = 2
    return
  }
  saving.value = true
  try {
    await API.updateProfile(payload)
    uni.showToast({ title: '已保存', icon: 'success' })
    step.value = 2
  } catch (e) {
    // request 内部已 toast
  } finally {
    saving.value = false
  }
}

function goPhoneStep() {
  step.value = 2
}

/** 微信手机号授权回调 */
async function onGetPhone(e) {
  const detail = e && e.detail
  if (!detail || !detail.code) {
    const msg = (detail && detail.errMsg) || ''
    if (msg.indexOf('deny') > -1 || msg.indexOf('cancel') > -1) {
      uni.showToast({ title: '已取消授权，可稍后在个人中心绑定', icon: 'none' })
    } else {
      uni.showToast({ title: '手机号授权失败，请稍后重试', icon: 'none' })
    }
    return
  }
  if (binding.value || phoneBound.value) return
  binding.value = true
  try {
    const profile = await API.bindPhone(detail.code)
    boundPhone.value = (profile && profile.phone) || ''
    phoneBound.value = true
    uni.showToast({ title: '手机号授权成功', icon: 'success' })
    setTimeout(finish, 700)
  } catch (e) {
    // request 内部已 toast
  } finally {
    binding.value = false
  }
}

function finish() {
  emit('done')
}

/** 未授权手机号：只关闭弹窗，不解锁受保护操作 */
function cancel() {
  emit('cancel')
}

/** H5：手动输入手机号完成绑定（无短信通道，接口带登录态与限流） */
async function bindMockPhone() {
  if (binding.value || phoneBound.value) return
  const phone = (mockPhone.value || '').trim()
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  binding.value = true
  try {
    const profile = await API.bindPhoneManual(phone)
    boundPhone.value = (profile && profile.phone) || phone
    phoneBound.value = true
    uni.showToast({ title: '手机号绑定成功', icon: 'success' })
    setTimeout(finish, 600)
  } catch (e) {
    // request 内部已 toast
  } finally {
    binding.value = false
  }
}
</script>

<style lang="scss" scoped>
.auth-guide {
  padding: 22px 20px 20px;
}
.ag-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 16px;
}
.ag-step {
  width: 18px;
  height: 4px;
  border-radius: 99px;
  background: #f0e2e7;
  transition: background 0.18s;
}
.ag-step.on {
  background: var(--pink-600);
}
.ag-title {
  font-size: 17px;
  font-weight: 800;
  text-align: center;
}
.ag-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
  line-height: 1.6;
}
.ag-avatar-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
}
.ag-avatar-btn {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: 2px dashed #e8c5d0;
  background: #fff8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  margin: 0;
  line-height: 1;
  overflow: hidden;

  &::after { border: none; }
}
.ag-avatar-img {
  width: 100%;
  height: 100%;
}
.ag-avatar-tip {
  font-size: 11px;
  color: var(--muted);
}
.ag-field {
  margin-top: 18px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  box-shadow: var(--sh-sm);
}
.ag-label {
  font-size: 13px;
  color: var(--muted);
  flex-shrink: 0;
  margin-right: 10px;
}
.ag-input {
  flex: 1;
  font-size: 14px;
  color: var(--ink);
  background: transparent;
  padding: 0;
}
.ag-count {
  font-size: 11px;
  color: var(--muted);
  flex-shrink: 0;
  margin-left: 8px;
}
.ag-actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}
.ag-btn {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  border-radius: 12px;
  padding: 11px 0;
  line-height: 1;

  &::after { border: none; }

  &[disabled] { opacity: 0.55; }
}
.ag-btn.ghost {
  background: #f5f5f5;
  color: var(--muted);
}
.ag-btn.primary {
  background: var(--pink-600);
  color: #fff;
}
.ag-btn.block {
  display: block;
  width: 100%;
  margin-top: 20px;
}
.ag-phone-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 22px;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--sh-sm);
}
.ag-phone-ico {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--pink-50);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.ag-phone-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ag-phone-t {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink);
}
.ag-phone-d {
  font-size: 11px;
  color: var(--muted);
}
.ag-phone-input-wrap {
  margin-top: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 0 14px;
  box-shadow: var(--sh-sm);
}
.ag-phone-input {
  height: 44px;
  font-size: 14px;
  color: var(--ink);
}
.ag-mock-tip {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}
.ag-cancel-tip {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: #c9bfc3;
  text-align: center;
}
.ag-skip {
  margin-top: 14px;
  width: 100%;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
  padding: 6px 0;

  &::after { border: none; }
}
</style>
