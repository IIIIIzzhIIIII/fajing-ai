<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back"><fj-icon name="arrow-left" :size="18" /></view>
      <view class="topbar-title">员工代客试发</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <block v-if="!taskId">
          <view class="card">
            <view class="card-title">1. 顾客授权</view>
            <view class="consent" @click="consent = !consent">
              <view class="check-box" :class="{ on: consent }">
                <fj-icon v-if="consent" name="check" :size="12" color="#FFFFFF" />
              </view>
              <view class="consent-text">
                顾客已同意使用其照片进行本次 AI 发型预览；照片仅用于本次生成，生成结束 24 小时内删除，不建人脸库、不用于训练。
              </view>
            </view>
          </view>

          <view class="card">
            <view class="card-title">2. 上传顾客照片</view>
            <view class="upload-box" :class="{ disabled: !consent }" @click="choosePhoto">
              <image v-if="photoUrl" :src="photoUrl" class="photo" mode="aspectFill" />
              <view v-else class="upload-empty">
                <fj-icon name="camera" :size="28" color="#C95676" />
                <text class="upload-text">拍摄或选择正脸照</text>
                <text class="upload-sub">需先勾选顾客授权</text>
              </view>
            </view>
            <view class="gender-row">
              <view class="gender-item" :class="{ on: gender === 'female' }" @click="setGender('female')">女款</view>
              <view class="gender-item" :class="{ on: gender === 'male' }" @click="setGender('male')">男款</view>
            </view>
          </view>

          <view class="card">
            <view class="card-title">3. 选择发型（最多 8 款）</view>
            <view class="style-grid">
              <view
                v-for="style in styles"
                :key="style.id"
                class="style-chip"
                :class="{ on: selected.includes(style.id) }"
                @click="toggleStyle(style.id)"
              >
                {{ style.name }}
              </view>
            </view>
            <view v-if="!styles.length" class="empty">发型库加载中…</view>
          </view>

          <view class="primary-btn" :class="{ disabled: !canGenerate || generating }" @click="generate">
            {{ generating ? '生成中…' : '开始免费试发' }}
          </view>
          <view class="tip">员工试发不扣顾客魅力值，也不展示充值入口；请用于真实到店顾客体验。</view>
        </block>

        <block v-else>
          <view class="result-head">
            <view>
              <view class="result-title">{{ done ? '试发完成' : '正在生成' }}</view>
              <view class="result-sub">{{ done ? '让顾客扫描下方二维码领取结果' : '通常需要 8-20 秒，请稍等' }}</view>
            </view>
            <view class="progress-pill">{{ progress }}%</view>
          </view>

          <view class="result-grid">
            <view v-for="item in resultStyles" :key="item.styleId" class="result-item">
              <image v-if="item.img" :src="item.img" class="result-img" mode="aspectFill" />
              <view v-else class="result-placeholder">
                <fj-icon name="sparkles" :size="22" color="#D8A7B6" />
                <text>{{ item.failed ? '生成失败' : '生成中' }}</text>
              </view>
              <view class="result-name">{{ item.name }}</view>
            </view>
          </view>

          <view v-if="done" class="claim-card">
            <view class="claim-title">顾客扫码领取</view>
            <image v-if="claimQr" :src="claimQr" class="claim-qr" mode="aspectFit" />
            <view class="claim-code">领取码 {{ taskId }}</view>
            <view class="claim-link" @click="copyClaimLink">复制领取链接</view>
            <view class="claim-tip">
              顾客扫码后登录即可查看本次试发结果，并可以进入门店页购买专享券。
            </view>
          </view>

          <view class="ghost-btn" @click="reset">再试一位顾客</view>
        </block>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { API } from '@/api/request'
import { goBack } from '@/utils/nav'
import { h5BaseUrl } from '@/utils/employee'

const consent = ref(false)
const gender = ref('female')
const photoUrl = ref('')
const styles = ref([])
const selected = ref([])
const generating = ref(false)
const taskId = ref('')
const claimToken = ref('')
const task = ref(null)
const progress = ref(0)
let timer = null

const canGenerate = computed(() =>
  consent.value && photoUrl.value && selected.value.length > 0
)

const resultStyles = computed(() => {
  const result = task.value && task.value.result
  return (result && result.styles) || []
})

const done = computed(() => task.value && (task.value.status === 'success' || task.value.status === 'failed'))

const claimUrl = computed(() => {
  if (!taskId.value || !claimToken.value) return ''
  return `${h5BaseUrl()}/#/pages/claim/claim?taskId=${taskId.value}&token=${encodeURIComponent(claimToken.value)}`
})

const claimQr = computed(() => {
  if (!claimUrl.value) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=320x320&data=${encodeURIComponent(claimUrl.value)}`
})

function back() {
  goBack()
}

async function loadStyles() {
  try {
    const list = await API.hairstyles(gender.value)
    styles.value = (list || []).filter((s) => s.status !== 0).slice(0, 12)
    selected.value = styles.value.slice(0, 4).map((s) => s.id)
  } catch (e) { /* 已统一提示 */ }
}

function setGender(value) {
  if (gender.value === value) return
  gender.value = value
  selected.value = []
  loadStyles()
}

function toggleStyle(id) {
  if (selected.value.includes(id)) {
    selected.value = selected.value.filter((x) => x !== id)
  } else if (selected.value.length < 8) {
    selected.value.push(id)
  }
}

async function choosePhoto() {
  if (!consent.value) {
    uni.showToast({ title: '请先确认顾客授权', icon: 'none' })
    return
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const path = res.tempFilePaths && res.tempFilePaths[0]
      if (!path) return
      uni.showLoading({ title: '上传中' })
      try {
        const data = await API.upload(path)
        photoUrl.value = data && (data.url || data.path || data.photoUrl)
        if (!photoUrl.value) {
          uni.showToast({ title: '上传返回异常', icon: 'none' })
        }
      } catch (e) {
        // upload 已统一提示
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function generate() {
  if (!canGenerate.value || generating.value) return
  generating.value = true
  try {
    const data = await API.employeeGenerate({
      photoUrl: photoUrl.value,
      gender: gender.value,
      styleIds: selected.value,
      consent: true
    })
    taskId.value = data.taskId
    claimToken.value = data.claimToken
    progress.value = 0
    poll()
  } catch (e) {
    // request 已统一提示
  } finally {
    generating.value = false
  }
}

async function poll() {
  stopPoll()
  const tick = async () => {
    try {
      const data = await API.employeeTask(taskId.value)
      task.value = data
      progress.value = data.progress || 0
      if (data.status === 'success' || data.status === 'failed') {
        stopPoll()
      }
    } catch (e) {
      // 单次失败不打断，继续轮询
    }
  }
  await tick()
  timer = setInterval(tick, 1600)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function copyClaimLink() {
  if (!claimUrl.value) return
  uni.setClipboardData({
    data: claimUrl.value,
    success: () => uni.showToast({ title: '领取链接已复制', icon: 'none' })
  })
}

function reset() {
  stopPoll()
  consent.value = false
  photoUrl.value = ''
  task.value = null
  taskId.value = ''
  claimToken.value = ''
  progress.value = 0
  loadStyles()
}

onMounted(loadStyles)
onUnmounted(stopPoll)
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
.card {
  padding: 18px;
  margin-bottom: 14px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--sh-sm);
}
.card-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 800;
}
.consent {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.check-box {
  width: 20px;
  height: 20px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid #e6cbd4;
  border-radius: 6px;
}
.check-box.on {
  background: #c95676;
  border-color: #c95676;
}
.consent-text {
  font-size: 11px;
  line-height: 1.7;
  color: var(--muted);
}
.upload-box {
  height: 190px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #faf5f7;
  border: 1px dashed #eccfd8;
  border-radius: 16px;
}
.upload-box.disabled {
  opacity: 0.55;
}
.photo {
  width: 100%;
  height: 100%;
}
.upload-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.upload-text {
  font-size: 13px;
  font-weight: 700;
}
.upload-sub {
  font-size: 10px;
  color: var(--muted);
}
.gender-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.gender-item {
  flex: 1;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  background: #fff;
  border: 1.5px solid #f1dde2;
  border-radius: 12px;
}
.gender-item.on {
  color: #a83f5e;
  background: #fff4f7;
  border-color: #c95676;
  font-weight: 800;
}
.style-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.style-chip {
  padding: 8px 12px;
  font-size: 12px;
  color: #6f666b;
  background: #faf5f7;
  border: 1px solid #f0e0e5;
  border-radius: 999px;
}
.style-chip.on {
  color: #a83f5e;
  background: #fff4f7;
  border-color: #c95676;
  font-weight: 700;
}
.primary-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d46a88, #b14a6b);
  border-radius: 999px;
}
.primary-btn.disabled {
  opacity: 0.5;
}
.ghost-btn {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #a83f5e;
  background: #fff;
  border: 1px solid #f0d9e0;
  border-radius: 999px;
}
.tip {
  margin-top: 12px;
  font-size: 10.5px;
  line-height: 1.7;
  color: var(--muted);
  text-align: center;
}
.result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.result-title {
  font-size: 20px;
  font-weight: 800;
}
.result-sub {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}
.progress-pill {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #a83f5e;
  background: #fff4f7;
  border-radius: 999px;
}
.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.result-item {
  overflow: hidden;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
}
.result-img {
  width: 100%;
  height: 200px;
}
.result-placeholder {
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 10px;
  color: var(--muted);
  background: #faf5f7;
}
.result-name {
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}
.claim-card {
  margin-top: 18px;
  padding: 18px;
  text-align: center;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--sh-sm);
}
.claim-title {
  font-size: 15px;
  font-weight: 800;
}
.claim-qr {
  width: 180px;
  height: 180px;
  margin: 12px auto 6px;
}
.claim-code {
  font-size: 11px;
  color: var(--muted);
}
.claim-link {
  margin-top: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #c95676;
}
.claim-tip {
  margin-top: 10px;
  font-size: 10.5px;
  line-height: 1.7;
  color: var(--muted);
}
.empty {
  padding: 20px 0;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}
</style>
