import { clearToken, getToken, setToken } from '@/utils/auth'

const BASE_URL = (import.meta.env && import.meta.env.VITE_API_BASE) || ''

/** 统一 toast（避免各处重复 import 组件） */
function fail(msg) {
  uni.showToast({ title: msg || '网络异常，请稍后重试', icon: 'none', duration: 2200 })
}

/** 接口地址必须由环境变量提供，缺配置时直接给出可读错误 */
function apiBase() {
  if (!BASE_URL) {
    fail('接口地址未配置（VITE_API_BASE）')
    return ''
  }
  return BASE_URL
}

/** 登录：小程序用 wx.login 的 code 换 token；H5 走演示登录 */
export function login() {
  return new Promise((resolve, reject) => {
    const base = apiBase()
    if (!base) {
      reject(new Error('no api base'))
      return
    }
    // #ifdef H5
    uni.request({
      url: base + '/api/auth/login/mock',
      method: 'POST',
      timeout: 15000,
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === 0 && body.data && body.data.token) {
          setToken(body.data.token)
          resolve(body.data)
        } else {
          reject(new Error((body && body.msg) || '登录失败'))
        }
      },
      fail: reject
    })
    // #endif

    // #ifndef H5
    uni.login({
      provider: 'weixin',
      success: (loginRes) => {
        const code = loginRes.code
        if (!code) {
          reject(new Error('微信登录失败'))
          return
        }
        uni.request({
          url: base + '/api/auth/login',
          method: 'POST',
          data: { code },
          header: { 'Content-Type': 'application/json' },
          timeout: 15000,
          success: (res) => {
            const body = res.data || {}
            if (res.statusCode === 200 && body.code === 0 && body.data && body.data.token) {
              setToken(body.data.token)
              resolve(body.data)
            } else {
              fail((body && body.msg) || '登录失败')
              reject(new Error((body && body.msg) || 'login failed'))
            }
          },
          fail: reject
        })
      },
      fail: reject
    })
    // #endif
  })
}

let loginPromise = null
function ensureLogin() {
  if (!loginPromise) {
    loginPromise = login().finally(() => {
      loginPromise = null
    })
  }
  return loginPromise
}

export function request({ url, method = 'GET', data = {}, header = {}, retry = true }) {
  return new Promise((resolve, reject) => {
    const base = apiBase()
    if (!base) {
      reject(new Error('no api base'))
      return
    }
    const token = getToken()
    uni.request({
      url: base + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...header
      },
      timeout: 15000,
      success: async (res) => {
        if (res.statusCode === 401) {
          clearToken()
          if (!retry) {
            fail('登录已过期，请重新登录')
            reject(new Error('unauthorized'))
            return
          }
          // 只自动重登并重试一次，避免死循环
          try {
            await ensureLogin()
            const result = await request({ url, method, data, header, retry: false })
            resolve(result)
          } catch (e) {
            fail('登录失败，请稍后重试')
            reject(e)
          }
          return
        }
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
    const base = apiBase()
    if (!base) {
      reject(new Error('no api base'))
      return
    }
    const token = getToken()
    uni.uploadFile({
      url: base + '/api/upload',
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
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
  me: () => request({ url: '/api/auth/me' }),
  logout: () => request({ url: '/api/auth/logout', method: 'POST' }),
  hairstyles: (gender, cat) =>
    request({
      url: `/api/hairstyles?gender=${encodeURIComponent(gender || '')}&cat=${encodeURIComponent(cat || '')}`
    }),
  colors: () => request({ url: '/api/colors' }),
  generate: (payload) => request({ url: '/api/generate', method: 'POST', data: payload }),
  task: (taskId) => request({ url: `/api/generate/${taskId}` }),
  retryStyle: (taskId, styleId) =>
    request({ url: `/api/generate/${taskId}/retry`, method: 'POST', data: { styleId } }),
  records: () => request({ url: '/api/records' }),
  deleteRecord: (id) => request({ url: `/api/records/${id}`, method: 'DELETE' }),
  quota: () => request({ url: '/api/quota' }),
  checkPhoto: (photoUrl) =>
    request({ url: '/api/photo/check', method: 'POST', data: { photoUrl } }),
  consume: (type) => request({ url: '/api/quota/consume', method: 'POST', data: { type } }),
  mockOrder: (payload) => request({ url: '/api/order/mock', method: 'POST', data: payload }),
  createOrder: (payload) => request({ url: '/api/order/create', method: 'POST', data: payload }),
  orderStatus: (id) => request({ url: `/api/order/${id}` }),
  unlocks: () => request({ url: '/api/unlocks' }),
  shareReward: () => request({ url: '/api/share/reward', method: 'POST' })
}
