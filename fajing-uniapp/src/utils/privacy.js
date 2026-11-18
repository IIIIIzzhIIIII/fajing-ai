import { reactive } from 'vue'

/** 本地是否已同意隐私提示：微信未开启隐私检查时用它来驱动弹层演示 */
const AGREED_KEY = 'fajing_privacy_agreed'

export const privacy = reactive({
  visible: false,
  contractName: ''
})

/** 微信隐私授权回调：同意/拒绝后必须调用，否则 wx.login 会一直挂起 */
let resolver = null
let inited = false

/**
 * 启动时注册一次：
 * 1. 官方判定需要授权时（正式号已配置隐私指引）打开自绘弹层
 * 2. 未开启隐私检查时（测试号）按本地标记演示一次弹层
 */
export function initPrivacyGuard() {
  if (inited) return
  inited = true
  // #ifdef MP-WEIXIN
  if (typeof wx !== 'undefined' && typeof wx.onNeedPrivacyAuthorization === 'function') {
    wx.onNeedPrivacyAuthorization((resolve) => {
      resolver = resolve
      privacy.visible = true
    })
  }
  // 没有本地同意记录时先亮出弹层，不依赖 getPrivacySetting 回调时序
  if (!uni.getStorageSync(AGREED_KEY)) {
    privacy.visible = true
  }
  if (typeof wx !== 'undefined' && typeof wx.getPrivacySetting === 'function') {
    wx.getPrivacySetting({
      success: (res) => {
        privacy.contractName = (res && res.privacyContractName) || ''
        if (res && res.needAuthorization) {
          privacy.visible = true
        }
      },
      fail: () => {
        if (!uni.getStorageSync(AGREED_KEY)) privacy.visible = true
      }
    })
  }
  // #endif
}

export function hasPendingAuth() {
  return !!resolver
}

/** 同意：存在官方授权回调时必须带上按钮 id 回传 */
export function agreePrivacy(buttonId) {
  if (resolver) {
    resolver({ buttonId, event: 'agree' })
    resolver = null
  }
  uni.setStorageSync(AGREED_KEY, '1')
  privacy.visible = false
}

export function rejectPrivacy() {
  if (resolver) {
    resolver({ event: 'disagree' })
    resolver = null
  }
  privacy.visible = false
}

export function openPrivacyContract() {
  // #ifdef MP-WEIXIN
  if (typeof wx !== 'undefined' && typeof wx.openPrivacyContract === 'function') {
    wx.openPrivacyContract({})
    return
  }
  // #endif
  uni.navigateTo({ url: '/pages/agreement/privacy' })
}
