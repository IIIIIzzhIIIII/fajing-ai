<template>
  <view class="page active">
    <view class="topbar">
      <view class="icon-btn" @click="back()"><fj-icon name="arrow-left" :size="16" /></view>
      <view class="topbar-title">上传照片</view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view class="pad">
        <view class="field-title">上传一张正脸照</view>
        <view class="field-sub">AI 需要看清你的脸型和发际线，才能把发型"长"在你头上。</view>

        <view class="dropzone" :class="{ 'has-img': !!photoSrc }" @click="pickPhoto('album')">
          <image v-if="photoSrc" class="dz-preview" :src="photoSrc" mode="aspectFill" style="width:100%;height:100%" />
          <view v-else class="dz-empty" style="display:flex;flex-direction:column;align-items:center;gap:8px">
            <view
              style="width:56px;height:56px;border-radius:50%;background:#fff;display:flex;align-items:center;justify-content:center;box-shadow:var(--sh-sm)"
            >
              <fj-icon name="scan" :size="24" color="#C95676" />
            </view>
            <view class="dz-title">点击上传正脸照</view>
            <view class="dz-sub">建议在自然光下拍摄</view>
          </view>
          <view v-if="photoSrc" class="dz-retake" @click.stop="pickPhoto('camera')">
            <fj-icon name="refresh" :size="13" color="#FFFFFF" />
            <text>重拍</text>
          </view>
        </view>

        <view class="upload-btns">
          <view class="btn btn-ghost" @click="pickPhoto('camera')">
            <fj-icon name="camera" :size="16" />
            <text>拍照</text>
          </view>
          <view class="btn btn-ghost" @click="pickPhoto('album')">
            <fj-icon name="image" :size="16" />
            <text>从相册选</text>
          </view>
        </view>

        <view class="tips">
          <view class="tip">
            <view class="tip-emoji">🙂</view>
            <view class="tip-t">正面朝向</view>
            <view class="tip-d">不要侧脸或低头</view>
          </view>
          <view class="tip">
            <view class="tip-emoji">☀️</view>
            <view class="tip-t">光线充足</view>
            <view class="tip-d">避免逆光与阴影</view>
          </view>
          <view class="tip">
            <view class="tip-emoji">👀</view>
            <view class="tip-t">露出额头</view>
            <view class="tip-d">别用刘海挡住</view>
          </view>
        </view>

        <view class="consent" @click="privacyAgreed = !privacyAgreed">
          <view class="cbx" :class="{ on: privacyAgreed }">
            <fj-icon name="check" :size="13" color="#FFFFFF" />
          </view>
          <view class="consent-t">
            我已阅读并同意
            <text class="hl" @click.stop="openDoc('privacy')">《隐私政策》</text>
            与
            <text class="hl" @click.stop="openDoc('terms')">《用户协议》</text>，照片仅用于本次生成，24 小时后自动删除。
          </view>
        </view>

        <view class="consent" @click="faceAgreed = !faceAgreed">
          <view class="cbx" :class="{ on: faceAgreed }">
            <fj-icon name="check" :size="13" color="#FFFFFF" />
          </view>
          <view class="consent-t">
            <text class="hl">人脸信息单独同意：</text>{{ site.faceConsentText }}
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view
        class="btn btn-primary btn-block"
        :class="{ 'is-disabled': !canNext }"
        @click="next()"
      >
        下一步
      </view>
    </view>

    <fj-sheet />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { upload, API } from '@/api/request'
import { track } from '@/utils/track'
import { goBack } from '@/utils/nav'
import { site } from '@/utils/site'

const app = useAppStore()
const ui = useUiStore()

const privacyAgreed = ref(false)
const faceAgreed = ref(false)
const busy = ref(false)

const photoSrc = computed(() => app.photoPath || '')
const canNext = computed(
  () => !!photoSrc.value && privacyAgreed.value && faceAgreed.value && !busy.value
)

function openDoc(name) {
  uni.navigateTo({ url: `/pages/agreement/${name}` })
}

function back() {
  goBack()
}

function pickPhoto(source) {
  // 人脸照片属于敏感信息：必须先单独勾选同意，才允许选择照片
  if (!privacyAgreed.value || !faceAgreed.value) {
    ui.toast('请先阅读并同意隐私政策与人脸信息单独同意')
    return
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: [source === 'camera' ? 'camera' : 'album'],
    success: (res) => {
      const path = res.tempFilePaths && res.tempFilePaths[0]
      if (!path) return
      app.setPhoto(path, '')
    }
  })
}

async function next() {
  if (busy.value) return
  if (!photoSrc.value || !privacyAgreed.value || !faceAgreed.value) return

  const localPath = app.photoPath
  busy.value = true
  // 只有已上传到服务端的图片才跳过上传：小程序 chooseImage 在开发者工具里会返回
  // http://tmp/xxx 的本地临时路径，不能按 http 前缀判断，否则会把临时路径当远程地址提交
  if (app.photoUrl && /^https?:\/\//.test(app.photoUrl)) {
    app.setPhoto(localPath, app.photoUrl)
  } else {
    try {
      const data = await upload(localPath)
      const url = (data && (data.url || data.path)) || ''
      app.setPhoto(localPath, url || localPath)
      track('upload_success', { remote: !!url })
    } catch (e) {
      // 上传失败不能带着本地临时路径继续，否则生成必然失败还白扣额度
      busy.value = false
      ui.toast('照片上传失败，请检查网络后重试')
      return
    }
  }

  // 跳转前用视觉模型审查：只放行单人正脸照，服务异常时放行不阻断
  const checkUrl = app.photoUrl || ''
  if (/^https?:\/\//.test(checkUrl)) {
    try {
      const check = await API.checkPhoto(checkUrl)
      if (check && check.ok === false) {
        busy.value = false
        ui.toast(check.reason || '请上传正面、清晰的本人照片')
        return
      }
    } catch (e) {
      // 审查失败不阻断正常流程
    }
  }

  busy.value = false
  uni.navigateTo({ url: '/pages/recommend/recommend' })
}
</script>

<style lang="scss" scoped>
.dropzone {
  height: 402px;
}
.consent-t {
  flex: 1;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--ink-2);
}
.consent-t .hl {
  color: var(--pink-700);
  font-weight: 700;
}
.btn-primary.is-disabled {
  background: #eadfe3;
  color: #b7a8af;
  box-shadow: none;
}
</style>
