import { clearToken, getToken, setToken } from '@/utils/auth'

const BASE_URL = (import.meta.env && import.meta.env.VITE_API_BASE) || ''

// 启动时打印接口地址，便于在开发者工具控制台定位连不通的问题
try {
  console.log('[fajing] api base =', BASE_URL || '(未配置)')
} catch (e) {
  // 控制台不可用时忽略
}

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

/** H5 游客账号标识：保存在浏览器本地，清缓存后会生成新账号 */
const GUEST_KEY = 'fajing_guest_key'

function guestKey() {
  try {
    const saved = uni.getStorageSync(GUEST_KEY)
    if (saved && typeof saved === 'string') return saved
  } catch (e) {
    // 读取失败按首次登录处理
  }
  let key = ''
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      key = crypto.randomUUID().replace(/-/g, '')
    }
  } catch (e) {
    key = ''
  }
  if (!key) key = 'g' + Date.now().toString(36) + Math.random().toString(36).slice(2, 12)
  try {
    uni.setStorageSync(GUEST_KEY, key)
  } catch (e) {
    // 本地存不下时本次登录仍可用，下次会生成新账号
  }
  return key
}

/** 登录：小程序用 wx.login 的 code 换 token；H5 用浏览器本地 guestKey 换游客账号 */
export function login() {
  return new Promise((resolve, reject) => {
    const base = apiBase()
    if (!base) {
      reject(new Error('no api base'))
      return
    }
    // #ifdef H5
    uni.request({
      url: base + '/api/auth/login/guest',
      method: 'POST',
      data: { guestKey: guestKey() },
      header: { 'Content-Type': 'application/json' },
      timeout: 15000,
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === 0 && body.data && body.data.token) {
          setToken(body.data.token)
          resolve(body.data)
        } else {
          fail((body && body.msg) || '登录失败')
          reject(new Error((body && body.msg) || '登录失败'))
        }
      },
      fail: (err) => {
        fail('登录失败，请检查网络后重试')
        reject(err)
      }
    })
    // #endif

    // #ifndef H5
    const doWxLogin = () => {
      uni.login({
        provider: 'weixin',
        success: (loginRes) => {
          const code = loginRes.code
          if (!code) {
            fail('微信登录失败：未获取到 code')
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
                console.error('[fajing] login rejected', res.statusCode, body)
                fail((body && body.msg) || '登录失败')
                reject(new Error((body && body.msg) || 'login failed'))
              }
            },
            fail: (err) => {
              const detail = (err && (err.errMsg || err.message)) || '请求失败'
              console.error('[fajing] login request fail', base, err)
              fail('登录失败：' + detail)
              reject(err)
            }
          })
        },
        fail: (err) => {
          const detail = (err && (err.errMsg || err.message)) || '未知错误'
          console.error('[fajing] wx.login fail', err)
          // 本地开发：开发者工具处于游客模式/无网络模拟时 wx.login 不可用，回退游客登录便于联调
          if (import.meta.env.DEV) {
            console.warn('[fajing] wx.login 不可用，改用游客登录')
            uni.request({
              url: base + '/api/auth/login/guest',
              method: 'POST',
              data: { guestKey: guestKey() },
              header: { 'Content-Type': 'application/json' },
              timeout: 15000,
              success: (res) => {
                const body = res.data || {}
                if (res.statusCode === 200 && body.code === 0 && body.data && body.data.token) {
                  setToken(body.data.token)
                  resolve(body.data)
                } else {
                  console.error('[fajing] guest login rejected', res.statusCode, body)
                  fail((body && body.msg) || '登录失败')
                  reject(new Error((body && body.msg) || 'login failed'))
                }
              },
              fail: (e2) => {
                const d2 = (e2 && (e2.errMsg || e2.message)) || '请求失败'
                console.error('[fajing] guest login fail', base, e2)
                fail('登录失败：' + d2)
                reject(e2)
              }
            })
            return
          }
          fail('微信登录失败：' + detail)
          reject(err)
        }
      })
    }

    // #ifdef MP-WEIXIN
    // T8：微信隐私授权，同意后才发 wx.login
    if (typeof wx !== 'undefined' && typeof wx.requirePrivacyAuthorize === 'function') {
      wx.requirePrivacyAuthorize({
        success: () => doWxLogin(),
        fail: () => {
          fail('需同意隐私协议后使用')
          reject(new Error('privacy denied'))
        }
      })
    } else {
      doWxLogin()
    }
    // #endif
    // #ifndef MP-WEIXIN
    doWxLogin()
    // #endif
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

export function request({ url, method = 'GET', data = {}, header = {}, retry = true, silent = false }) {
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
          if (!silent) fail(`服务异常（${res.statusCode}）`)
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }
        if (!body || body.code !== 0) {
          if (!silent) fail(body && body.msg ? body.msg : '请求失败')
          reject(new Error((body && body.msg) || 'business error'))
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        if (!silent) fail('网络异常，请检查后端是否启动')
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
  updateProfile: (payload) => request({ url: '/api/auth/profile', method: 'POST', data: payload }),
  bindPhone: (code) => request({ url: '/api/auth/phone', method: 'POST', data: { code } }),
  bindPhoneMock: (phone) => request({ url: '/api/auth/phone/mock', method: 'POST', data: { phone } }),
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
  clearRecords: () => request({ url: '/api/records', method: 'DELETE' }),
  deleteRecord: (id) => request({ url: `/api/records/${id}`, method: 'DELETE' }),
  quota: () => request({ url: '/api/quota' }),
  checkPhoto: (photoUrl) =>
    request({ url: '/api/photo/check', method: 'POST', data: { photoUrl } }),
  createOrder: (payload) => request({ url: '/api/order/create', method: 'POST', data: payload }),
  virtualConfirm: (id) => request({ url: `/api/order/${id}/virtual-confirm`, method: 'POST' }),
  syncOrder: (id) => request({ url: `/api/order/${id}/sync`, method: 'POST' }),
  orderStatus: (id) => request({ url: `/api/order/${id}` }),
  unlocks: () => request({ url: '/api/unlocks' }),
  unlock: (payload) => request({ url: '/api/unlock', method: 'POST', data: payload }),
  recommend: (payload) => request({ url: '/api/recommend', method: 'POST', data: payload }),
  recommendColors: (id) => request({ url: `/api/recommend/${id}/colors` }),
  colorState: (id, taskId) =>
    request({
      url: `/api/recommend/${id}/color-state${taskId ? '?taskId=' + encodeURIComponent(taskId) : ''}`
    }),
  unlockColor: (payload) => request({ url: '/api/recommend/color/unlock', method: 'POST', data: payload }),
  recommendColor: (payload) => request({ url: '/api/recommend/color', method: 'POST', data: payload }),
  adReward: () => request({ url: '/api/ad/reward', method: 'POST' }),
  couponTiers: (storeId) =>
    request({ url: `/api/coupons/tiers${storeId ? '?storeId=' + encodeURIComponent(storeId) : ''}` }),
  myCoupons: () => request({ url: '/api/coupons/my' }),
  refundCoupon: (id) => request({ url: `/api/coupons/${id}/refund`, method: 'POST' }),
  shareReward: () => request({ url: '/api/share/reward', method: 'POST' }),
  siteSettings: () => request({ url: '/api/site-settings', silent: true, retry: false }),
  pointsPackages: () => request({ url: '/api/points/packages', silent: true, retry: false }),
  // 门店
  nearbyStores: () => request({ url: '/api/store/nearby' }),
  storeDetail: (id) => request({ url: `/api/store/${id}` }),
  // 员工工作台
  employeeMe: () => request({ url: '/api/employee/me' }),
  employeeBind: (payload) =>
    request({ url: '/api/employee/bind', method: 'POST', data: payload }),
  employeeGenerate: (payload) =>
    request({ url: '/api/employee/generate', method: 'POST', data: payload }),
  employeeTask: (taskId) => request({ url: `/api/employee/task/${taskId}` }),
  employeeClaim: (payload) =>
    request({ url: '/api/employee/claim', method: 'POST', data: payload }),
  employeeQuota: () => request({ url: '/api/employee/quota' })
}
