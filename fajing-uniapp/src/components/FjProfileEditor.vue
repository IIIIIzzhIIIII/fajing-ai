<template>
  <view class="profile-editor">
    <view class="pe-title">完善个人资料</view>
    <view class="pe-sub">让发型师更了解你（可随时修改）</view>

    <view class="pe-avatar-row">
      <view class="pe-avatar-label">头像</view>
      <!-- #ifdef MP-WEIXIN -->
      <button class="pe-avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
        <image v-if="avatarUrl" :src="avatarUrl" mode="aspectFill" class="pe-avatar-img" />
        <fj-icon v-else name="user" :size="28" color="#C95676" />
      </button>
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="pe-avatar-btn" @click="chooseAvatarManual">
        <image v-if="avatarUrl" :src="avatarUrl" mode="aspectFill" class="pe-avatar-img" />
        <fj-icon v-else name="user" :size="28" color="#C95676" />
      </view>
      <!-- #endif -->
    </view>

    <view class="pe-field">
      <view class="pe-label">昵称</view>
      <!-- #ifdef MP-WEIXIN -->
      <input
        class="pe-input"
        type="nickname"
        :value="nickname"
        placeholder="请输入昵称"
        @input="onNicknameInput"
        maxlength="20"
      />
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <input
        class="pe-input"
        type="text"
        :value="nickname"
        placeholder="请输入昵称"
        @input="onNicknameInput"
        maxlength="20"
      />
      <!-- #endif -->
      <view class="pe-count">{{ nickname.length }}/20</view>
    </view>

    <view class="pe-actions">
      <button class="pe-btn pe-btn-skip" @click="emit('skip')">跳过</button>
      <button class="pe-btn pe-btn-save" :loading="saving" :disabled="!canSave" @click="doSave">保存</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { upload, API } from '@/api/request'

const props = defineProps({
  initialNickname: { type: String, default: '' },
  initialAvatar: { type: String, default: '' }
})
const emit = defineEmits(['saved', 'skip'])

const nickname = ref(props.initialNickname || '')
const avatarUrl = ref(props.initialAvatar || '')
const avatarChanged = ref(false)
const saving = ref(false)

const canSave = computed(() => {
  const clean = nickname.value.trim()
  return clean.length >= 1 || avatarChanged.value
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

async function doSave() {
  if (!canSave.value || saving.value) return
  const clean = nickname.value.trim()
  const payload = {}
  if (clean.length >= 1 && clean !== props.initialNickname) {
    payload.nickname = clean
  }
  if (avatarChanged.value && avatarUrl.value) {
    payload.avatar = avatarUrl.value
  }
  if (Object.keys(payload).length === 0) {
    emit('saved')
    return
  }
  saving.value = true
  try {
    const profile = await API.updateProfile(payload)
    // 同步更新本地 initial 标记，避免重复保存
    if (payload.nickname) props.initialNickname = payload.nickname
    if (payload.avatar) props.initialAvatar = payload.avatar
    avatarChanged.value = false
    uni.showToast({ title: '已保存', icon: 'success' })
    emit('saved', profile)
  } catch (e) {
    // request 内部已 toast
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-editor {
  padding: 24px 20px 20px;
}
.pe-title {
  font-size: 17px;
  font-weight: 800;
  text-align: center;
}
.pe-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
}
.pe-avatar-row {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 24px;
  gap: 16px;
}
.pe-avatar-btn {
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
.pe-avatar-img {
  width: 100%;
  height: 100%;
}
.pe-field {
  margin-top: 20px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  box-shadow: var(--sh-sm);
}
.pe-label {
  font-size: 13px;
  color: var(--muted);
  flex-shrink: 0;
  margin-right: 10px;
}
.pe-input {
  flex: 1;
  font-size: 14px;
  color: var(--ink);
  background: transparent;
  padding: 0;
}
.pe-count {
  font-size: 11px;
  color: var(--muted);
  flex-shrink: 0;
  margin-left: 8px;
}
.pe-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}
.pe-btn {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  border-radius: 12px;
  padding: 11px 0;
  line-height: 1;

  &::after { border: none; }
}
.pe-btn-skip {
  background: #f5f5f5;
  color: var(--muted);
}
.pe-btn-save {
  background: var(--pink-600);
  color: #fff;

  &[disabled] {
    opacity: 0.5;
  }
}
</style>
