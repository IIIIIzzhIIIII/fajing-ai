<template>
  <view class="page active">
    <scroll-view scroll-y class="scroll" style="padding-bottom:10px">
      <view class="home-head">
        <view style="display:flex;align-items:center;gap:8px">
          <view class="logo-mark"><fj-icon name="scissors" :size="16" color="#C95676" /></view>
          <text style="font-weight:800;letter-spacing:-.3px">发镜 AI</text>
        </view>
        <view class="pill" @click="goProfile"><fj-icon name="user" :size="16" />我的</view>
      </view>

      <view class="hero">
        <view class="hero-eyebrow">AI HAIR TRY-ON</view>
        <view class="hero-title">{{ site.heroTitleMain }}<view><text class="grad-text">{{ site.heroTitleAccent }}</text></view></view>
        <view class="hero-sub">AI 脸型分析 · 按适配度排名 · 前 3 款免费</view>
        <view class="cta-wrap">
          <view class="cta" @click="goUpload">
            <fj-icon name="camera" :size="26" color="#FFFFFF" />
            <text class="cta-main">上传我的照片</text>
            <text v-if="ctaSub" class="cta-sub">{{ ctaSub }}</text>
          </view>
        </view>
        <view class="gender-seg">
          <view class="gender-item" :class="{ on: app.gender === 'male' }" @click="setGender('male')">男款</view>
          <view class="gender-item" :class="{ on: app.gender === 'female' }" @click="setGender('female')">女款</view>
        </view>
        <view class="sell-row">
          <view class="sell">
            <view class="sell-ico"><fj-icon name="timer" :size="16" color="#C95676" /></view>
            <view class="sell-t">脸型匹配</view>
            <view class="sell-d">按适配度排名</view>
          </view>
          <view class="sell">
            <view class="sell-ico"><fj-icon name="lock" :size="16" color="#C95676" /></view>
            <view class="sell-t">用完即删</view>
            <view class="sell-d">24h 自动清空</view>
          </view>
          <view class="sell">
            <view class="sell-ico"><fj-icon name="users" :size="16" color="#C95676" /></view>
            <view class="sell-t">男女都有</view>
            <view class="sell-d">20+ 热门款</view>
          </view>
        </view>
      </view>

      <view class="sec-head">
        <view class="sec-title">本周热门</view>
        <view class="sec-more" @click="goStyles">全部发型<fj-icon name="chevron-right" :size="13" /></view>
      </view>
      <scroll-view scroll-x :show-scrollbar="false" class="hot-strip">
        <view v-for="item in hotList" :key="item.id" class="hot-card" @click="goStyles">
          <view class="hot-thumb">
            <image :src="item.img" mode="aspectFill" style="width:100%;height:100%" />
            <text class="hot-tag">{{ item.tag }}</text>
          </view>
          <view class="hot-name">{{ item.name }}</view>
        </view>
      </scroll-view>

      <view v-if="stores.length" class="sec-head">
        <view class="sec-title">合作门店</view>
        <view class="sec-more" @click="goStores">全部门店<fj-icon name="chevron-right" :size="13" /></view>
      </view>
      <scroll-view v-if="stores.length" scroll-x :show-scrollbar="false" class="store-strip">
        <view v-for="item in stores" :key="item.id" class="store-card" @click="goStore(item.id)">
          <view class="store-name">{{ item.name }}</view>
          <view class="store-addr">{{ item.address || '到店咨询' }}</view>
          <view class="store-price">
            {{ item.minPayAmount ? '¥' + item.minPayAmount + ' 起' : '到店咨询' }}
          </view>
        </view>
      </scroll-view>

      <view class="privacy-note">
        <fj-icon name="shield" :size="20" color="#E8A9BF" />
        <view style="flex:1">
          <view style="font-size:12.5px;font-weight:700">人脸照片不留存</view>
          <view style="font-size:10.5px;color:rgba(255,255,255,.6);margin-top:2px">仅用于本次生成，24 小时后自动删除</view>
        </view>
      </view>
    </scroll-view>
    <fj-tabbar active="home" />

    <!-- 登录授权弹窗：上传/充值等需要登录的操作触发 -->
    <FjLoginModal v-if="loginVisible" @done="onLoginDone" @cancel="onLoginCancel" />

    <!-- 隐私授权自绘弹层：官方判定需要授权时由 wx.onNeedPrivacyAuthorization 触发 -->
    <FjPrivacyDialog />
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { API } from '@/api/request'
import { getToken } from '@/utils/auth'
import FjLoginModal from '@/components/FjLoginModal.vue'
import { useLoginGate } from '@/composables/useLoginGate'
import FjPrivacyDialog from '@/components/FjPrivacyDialog.vue'
import { site } from '@/utils/site'
import { PORTRAIT, STYLES, STYLE_IMG } from '@/mock/constants'

const app = useAppStore()
const stores = ref([])
const hotStyles = ref([])

/** 热门发型优先用后台发型库，接口失败时回退本地静态数据 */
async function loadHotStyles() {
  try {
    const rows = await API.hairstyles(app.gender)
    hotStyles.value = Array.isArray(rows) ? rows : []
  } catch (e) {
    hotStyles.value = []
  }
}

watch(() => app.gender, loadHotStyles)

const { visible: loginVisible, ensureLogin, finishLogin, closeLogin } = useLoginGate()

function onLoginDone() {
  finishLogin()
}

function onLoginCancel() {
  closeLogin()
}

// 从充值/看广告页返回时刷新魅力值，免费次数用完后的文案才能实时更新
onShow(async () => {
  loadStores()
  loadHotStyles()
  if (!getToken()) return
  try {
    const quota = await API.quota()
    app.setQuota(quota)
  } catch (e) { /* quota 拉不到不阻断 */ }
})

async function loadStores() {
  try {
    stores.value = (await API.nearbyStores()) || []
  } catch (e) { /* 门店列表拉不到不阻断首页 */ }
}

const ctaSub = computed(() => {
  if (app.quota.unlimited) return 'VIP 不限量 · 想换就换'
  if (app.quota.freeBatches > 0) return `前 3 款免费 · 还剩 ${app.quota.freeBatches} 次免费生成`
  // 只有看广告获得的免费解锁次数才展示，充值获得的魅力值不在此显示
  const adCredits = Number(app.quota.adCredits || 0)
  if (adCredits > 0) return `可以免费解锁 ${adCredits} 款`
  return ''
})

const hotList = computed(() => {
  const tags = ['热门', '新上', '圆脸友好', '显脸小', '学生感', '好打理']
  if (hotStyles.value.length) {
    return hotStyles.value.slice(0, 6).map((s, i) => ({
      id: s.id,
      name: s.name,
      tag: tags[i] || s.cat || '热门',
      img: s.img || PORTRAIT(s.en, app.gender)
    }))
  }
  const list = STYLES[app.gender] || STYLES.female
  return list.slice(0, 6).map((s, i) => ({
    id: s.id,
    name: s.name,
    tag: tags[i],
    img: STYLE_IMG(s) || PORTRAIT(s.en, app.gender)
  }))
})

function setGender(gender) {
  app.gender = gender
}

function goUpload() {
  ensureLogin(() => uni.navigateTo({ url: '/pages/upload/upload' }))
}
function goStyles() {
  // 首页「全部发型 / 热门款」只做图鉴浏览，不能直接发起生成
  uni.navigateTo({ url: '/pages/styles/styles?view=1' })
}
function goProfile() {
  uni.reLaunch({ url: '/pages/profile/profile' })
}
function goStores() {
  uni.navigateTo({ url: '/pages/store/store' })
}
function goStore(id) {
  uni.navigateTo({ url: `/pages/store/store?id=${id}` })
}
</script>

<style lang="scss" scoped>
.gender-seg {
  display: flex;
  gap: 8px;
  margin: 10px auto 0;
  padding: 0 28px;
}
.gender-item {
  flex: 1;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #fff;
  border: 1.5px solid #f1dde2;
  box-shadow: var(--sh-sm);
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
}
.gender-item.on {
  background: var(--pink-50);
  border-color: var(--pink-500);
  color: var(--pink-700);
  font-weight: 800;
}
.privacy-note {
  margin: 18px 20px 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #2d3436, #3f4a4d);
  color: #fff;
  display: flex;
  gap: 12px;
  align-items: center;
}
.hot-strip {
  white-space: nowrap;
}
.store-strip {
  white-space: nowrap;
}
.store-card {
  display: inline-block;
  width: 190px;
  padding: 14px;
  margin-right: 10px;
  vertical-align: top;
  white-space: normal;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--sh-sm);
}
.store-name {
  font-size: 14px;
  font-weight: 800;
}
.store-addr {
  margin-top: 4px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--muted);
}
.store-price {
  margin-top: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #c95676;
}
</style>
