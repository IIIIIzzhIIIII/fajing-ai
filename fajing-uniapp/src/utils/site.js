import { reactive } from 'vue'
import { API } from '@/api/request'

const CACHE_KEY = 'fajing_site_settings'

/** 接口不可用时的兜底文案，保证页面不出现空白 */
const DEFAULTS = {
  heroTitleMain: '换个发型',
  heroTitleAccent: '换张脸',
  contactWechat: 'fajing-ai',
  contactPhone: '',
  aboutContent: '',
  termsContent: '',
  privacyContent: '',
  faceConsentText:
    '我同意收集并处理我上传的人脸照片（敏感个人信息），仅用于本次 AI 换发预览，不建人脸库、不用于训练，生成结束 24 小时内删除原图。'
}

export const site = reactive({ ...DEFAULTS })

function apply(data) {
  if (!data) return
  Object.keys(DEFAULTS).forEach((key) => {
    if (data[key] !== null && data[key] !== undefined && data[key] !== '') {
      site[key] = data[key]
    }
  })
}

/** 启动时拉取一次：先用本地缓存渲染，再用接口结果覆盖 */
export async function loadSiteSettings() {
  const cached = uni.getStorageSync(CACHE_KEY)
  if (cached && typeof cached === 'object') {
    apply(cached)
  }
  try {
    const data = await API.siteSettings()
    if (data) {
      apply(data)
      uni.setStorageSync(CACHE_KEY, data)
    }
  } catch (e) {
    // 接口失败沿用缓存与内置默认值，不阻断页面
  }
}
