/** 当前运行端：用于下单渠道与 H5 领取链接 */
export function getPlatform() {
  // #ifdef MP-WEIXIN
  return 'mp-weixin'
  // #endif
  // #ifndef MP-WEIXIN
  return 'h5'
  // #endif
}

/** 顾客领取结果 / 门店页使用的 H5 地址 */
export function h5BaseUrl() {
  // #ifdef H5
  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    return window.location.origin
  }
  // #endif
  return (import.meta.env && import.meta.env.VITE_H5_BASE) || 'http://localhost:5173'
}
