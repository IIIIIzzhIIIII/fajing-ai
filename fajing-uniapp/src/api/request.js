const BASE_URL = (import.meta.env && import.meta.env.VITE_API_BASE) || 'http://localhost:8081'

/** 统一 toast（避免各处重复 import 组件） */
function fail(msg) {
  uni.showToast({ title: msg || '网络异常，请稍后重试', icon: 'none', duration: 2200 })
}

export function request({ url, method = 'GET', data = {}, header = {} }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: { 'Content-Type': 'application/json', ...header },
      timeout: 15000,
      success: (res) => {
        const body = res.data
        if (res.statusCode !== 200) {
          fail(`服务异常（${res.statusCode}）`)
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }
        if (!body || body.code !== 0) {
          fail(body && body.msg ? body.msg : '请求失败')
          reject(new Error((body && body.msg) || 'business error'))
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        fail('网络异常，请检查后端是否启动')
        reject(err)
      }
    })
  })
}

export function upload(filePath) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/api/upload',
      filePath,
      name: 'file',
      success: (res) => {
        let body = null
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          fail('上传返回异常')
          reject(new Error('bad json'))
          return
        }
        if (body.code !== 0) {
          fail(body.msg || '上传失败')
          reject(new Error(body.msg || 'upload failed'))
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        fail('上传失败，请重试')
        reject(err)
      }
    })
  })
}

export const API = {
  hairstyles: (gender, cat) =>
    request({
      url: `/api/hairstyles?gender=${encodeURIComponent(gender || '')}&cat=${encodeURIComponent(cat || '')}`
    }),
  colors: () => request({ url: '/api/colors' }),
  generate: (payload) => request({ url: '/api/generate', method: 'POST', data: payload }),
  task: (taskId) => request({ url: `/api/generate/${taskId}` }),
  records: () => request({ url: '/api/records' }),
  quota: () => request({ url: '/api/quota' }),
  consume: (type) => request({ url: '/api/quota/consume', method: 'POST', data: { type } }),
  posts: (filter) => request({ url: `/api/posts?filter=${encodeURIComponent(filter || '')}` }),
  createPost: (payload) => request({ url: '/api/posts', method: 'POST', data: payload }),
  likePost: (id) => request({ url: `/api/posts/${id}/like`, method: 'POST' }),
  comments: (id) => request({ url: `/api/posts/${id}/comments` }),
  addComment: (id, content) =>
    request({ url: `/api/posts/${id}/comments`, method: 'POST', data: { content } }),
  reportPost: (id, reason) =>
    request({ url: `/api/posts/${id}/report`, method: 'POST', data: { reason } }),
  mockOrder: (payload) => request({ url: '/api/order/mock', method: 'POST', data: payload }),
  unlocks: () => request({ url: '/api/unlocks' }),
  shareReward: () => request({ url: '/api/share/reward', method: 'POST' })
}
