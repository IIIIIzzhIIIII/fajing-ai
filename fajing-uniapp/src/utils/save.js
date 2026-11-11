/**
 * 保存图片到相册
 * - H5：Canvas 绘制（可选水印）后下载
 * - 小程序：downloadFile + saveImageToPhotosAlbum（授权失败时引导设置）
 */

function ensureAlbumAuth() {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.getSetting({
      success: (res) => {
        if (res.authSetting['scope.writePhotosAlbum'] === false) {
          uni.showModal({
            title: '需要相册权限',
            content: '请在设置中开启「保存到相册」权限',
            confirmText: '去设置',
            success: (m) => {
              if (m.confirm) {
                uni.openSetting({
                  success: (s) => {
                    if (s.authSetting['scope.writePhotosAlbum']) resolve()
                    else reject(new Error('auth denied'))
                  }
                })
              } else {
                reject(new Error('auth denied'))
              }
            }
          })
        } else {
          resolve()
        }
      },
      fail: reject
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve()
    // #endif
  })
}

function saveAlbum(filePath) {
  return new Promise((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath,
      success: resolve,
      fail: reject
    })
  })
}

/**
 * 小程序端 Canvas 水印：固定 750×1000 画布等比铺满，右下角叠加品牌水印。
 * 画布节点由结果页提供（canvas-id="fajing-wm"），其它页面找不到时回退原图。
 */
function drawMpWatermark(src) {
  return new Promise((resolve, reject) => {
    const W = 750
    const H = 1000
    let ctx = null
    try {
      ctx = uni.createCanvasContext('fajing-wm')
    } catch (e) {
      reject(e)
      return
    }
    if (!ctx) {
      reject(new Error('canvas not found'))
      return
    }
    uni.getImageInfo({
      src,
      success: (info) => {
        const scale = Math.min(W / info.width, H / info.height)
        const dw = Math.round(info.width * scale)
        const dh = Math.round(info.height * scale)
        ctx.setFillStyle('#FFFFFF')
        ctx.fillRect(0, 0, W, H)
        ctx.drawImage(src, (W - dw) / 2, (H - dh) / 2, dw, dh)

        const text = 'AI 生成 · 发镜 AI'
        const fontSize = 26
        const pad = 16
        ctx.setFontSize(fontSize)
        ctx.setTextBaseline('normal')
        const tw = ctx.measureText(text).width
        ctx.setFillStyle('rgba(0,0,0,0.32)')
        ctx.fillRect(W - tw - pad * 2 - 12, H - fontSize - pad * 2.4, tw + pad * 2, fontSize + pad * 1.4)
        ctx.setFillStyle('rgba(255,255,255,0.92)')
        ctx.fillText(text, W - tw - pad * 1.4 - 12, H - fontSize - pad * 1.6)

        ctx.draw(false, () => {
          uni.canvasToTempFilePath({
            canvasId: 'fajing-wm',
            width: W,
            height: H,
            destWidth: W,
            destHeight: H,
            success: (res) => resolve(res.tempFilePath),
            fail: reject
          })
        })
      },
      fail: reject
    })
  })
}

function saveMp(url, watermark) {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error('download failed'))
          return
        }
        if (!watermark) {
          saveAlbum(res.tempFilePath).then(resolve, reject)
          return
        }
        drawMpWatermark(res.tempFilePath)
          .then((out) => saveAlbum(out || res.tempFilePath))
          .then(resolve)
          .catch(() => saveAlbum(res.tempFilePath).then(resolve, reject))
      },
      fail: reject
    })
  })
}

function saveH5(url, watermark) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = img.naturalWidth || img.width
      canvas.height = img.naturalHeight || img.height
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      if (watermark) {
        const fontSize = Math.max(14, Math.round(canvas.width * 0.032))
        ctx.font = `${fontSize}px "PingFang SC", "Microsoft YaHei", sans-serif`
        const text = 'AI 生成 · 发镜 AI'
        const padding = Math.round(fontSize * 0.7)
        const tw = ctx.measureText(text).width
        ctx.fillStyle = 'rgba(0,0,0,0.32)'
        ctx.fillRect(
          canvas.width - tw - padding * 2,
          canvas.height - fontSize - padding * 2.2,
          tw + padding * 2,
          fontSize + padding * 1.4
        )
        ctx.fillStyle = 'rgba(255,255,255,0.92)'
        ctx.fillText(text, canvas.width - tw - padding * 1.4, canvas.height - padding * 1.5)
      }
      try {
        const dataUrl = canvas.toDataURL('image/jpeg', 0.95)
        const a = document.createElement('a')
        a.href = dataUrl
        a.download = `fajing-${Date.now()}.jpg`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        resolve()
      } catch (e) {
        reject(e)
      }
    }
    img.onerror = () => reject(new Error('image load failed'))
    img.src = url
  })
}

export async function saveRemoteImage(url, watermark = false) {
  if (!url) throw new Error('empty url')
  await ensureAlbumAuth()
  // #ifdef H5
  return saveH5(url, watermark)
  // #endif
  // #ifndef H5
  return saveMp(url, watermark)
  // #endif
}
