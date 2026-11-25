/** 是否在微信内置浏览器中打开（公众号支付只在微信内可用） */
export function isWechatBrowser() {
  // #ifdef H5
  return typeof navigator !== 'undefined' && /MicroMessenger/i.test(navigator.userAgent || '')
  // #endif
  // #ifndef H5
  return false
  // #endif
}

/** 是否 iOS 小程序：虚拟商品在 iOS 端不展示购买入口，避免违反平台规则 */
export function isIOSMiniProgram() {
  // #ifdef MP-WEIXIN
  try {
    const info = uni.getSystemInfoSync()
    return String((info && info.platform) || '').toLowerCase() === 'ios'
  } catch (e) {
    return false
  }
  // #endif
  // #ifndef MP-WEIXIN
  return false
  // #endif
}

/**
 * 调起微信支付：
 * - H5（公众号）：WeixinJSBridge.getBrandWCPayRequest，参数需带 appId
 * - 小程序：uni.requestPayment
 */
export function requestWxPay(payParams) {
  // #ifdef H5
  return new Promise((resolve, reject) => {
    const invoke = () => {
      if (!window.WeixinJSBridge || typeof window.WeixinJSBridge.invoke !== 'function') {
        reject(new Error('请在微信中打开后再支付'))
        return
      }
      window.WeixinJSBridge.invoke(
        'getBrandWCPayRequest',
        {
          appId: payParams.appId,
          timeStamp: String(payParams.timeStamp || ''),
          nonceStr: payParams.nonceStr,
          package: payParams.package,
          signType: payParams.signType || 'RSA',
          paySign: payParams.paySign
        },
        (res) => {
          const msg = (res && res.err_msg) || ''
          if (msg === 'get_brand_wcpay_request:ok') {
            resolve(res)
          } else if (msg === 'get_brand_wcpay_request:cancel') {
            reject(new Error('支付已取消'))
          } else {
            reject(new Error('支付失败，请重试'))
          }
        }
      )
    }
    if (window.WeixinJSBridge) {
      invoke()
    } else if (document.addEventListener) {
      document.addEventListener('WeixinJSBridgeReady', invoke, { once: true })
    } else {
      reject(new Error('请在微信中打开后再支付'))
    }
  })
  // #endif
  // #ifndef H5
  return new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      ...payParams,
      success: resolve,
      fail: (err) => reject(new Error((err && err.errMsg) || '支付失败'))
    })
  })
  // #endif
}
