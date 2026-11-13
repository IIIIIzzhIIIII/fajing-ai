/**
 * 统一返回：
 * - 页面栈里有上一页 → 回上一页
 * - 直接通过链接/刷新打开页面（栈里只有 1 层）→ 回兜底页，避免点了没反应
 */
export function goBack(fallback = '/pages/home/home') {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages && pages.length > 1) {
    uni.navigateBack({
      fail: () => uni.reLaunch({ url: fallback })
    })
    return
  }
  uni.reLaunch({ url: fallback })
}
