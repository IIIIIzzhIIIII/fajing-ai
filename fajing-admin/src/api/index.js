import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getToken } from '@/utils/auth'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8081',
  timeout: 15000
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.msg || '请求失败')
        return Promise.reject(new Error(body.msg || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      clearToken()
      ElMessage.error('登录已过期，请重新登录')
      const redirect = encodeURIComponent(window.location.hash.replace(/^#/, '') || '/')
      window.location.hash = `#/login?redirect=${redirect}`
      return Promise.reject(error)
    }
    let message = '网络异常，请稍后重试'
    if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    } else if (error.response) {
      message = error.response.data?.msg || `请求失败（${error.response.status}）`
    } else if (error.request) {
      message = '无法连接服务器，请确认后端服务已启动'
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export function loginAdmin(data) {
  return service.post('/api/admin/login', data)
}

export function generateModelShot(imageUrl) {
  // AI 出图通常 20~60 秒，单独放宽超时，避免走默认 15 秒被中断
  return service.post('/api/admin/ai/model-shot', { imageUrl }, { timeout: 240000 })
}

export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return service.post('/api/admin/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function fetchPayConfig() {
  return service.get('/api/admin/pay-config')
}

export function savePayConfig(data) {
  return service.put('/api/admin/pay-config', data)
}

export function verifyPayChannel(channel) {
  return service.post('/api/admin/pay-config/verify', { channel }, { timeout: 60000 })
}

export function uploadPayCert(file) {
  const form = new FormData()
  form.append('file', file)
  return service.post('/api/admin/pay-config/cert', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export function refundOrder(id, data) {
  return service.post(`/api/admin/orders/${id}/refund`, data, { timeout: 60000 })
}

export function fetchTodayStats() {
  return service.get('/api/admin/stats/today')
}

export function fetchOverview(params) {
  return service.get('/api/admin/stats/overview', { params })
}

export function fetchChannelsStats(days) {
  return service.get('/api/admin/stats/channels', {
    params: days ? { days } : {}
  })
}

export function fetchTradeRule() {
  return service.get('/api/admin/trade-rule')
}

export function saveTradeRule(data) {
  return service.put('/api/admin/trade-rule', data)
}

export function fetchAdminLogs(limit) {
  return service.get('/api/admin/logs', { params: limit ? { limit } : {} })
}

export function fetchHairstyles(gender) {
  return service.get('/api/admin/hairstyles', {
    params: gender ? { gender } : {}
  })
}

export function createHairstyle(data) {
  return service.post('/api/admin/hairstyles', data)
}

export function updateHairstyle(id, data) {
  return service.put(`/api/admin/hairstyles/${id}`, data)
}

export function updateHairstyleStatus(id, status) {
  return service.patch(`/api/admin/hairstyles/${id}/status`, null, {
    params: { status }
  })
}

export function fetchOrders() {
  return service.get('/api/admin/orders')
}

export function fetchUsers(params) {
  return service.get('/api/admin/users', { params })
}

export function updateUserStatus(id, status) {
  return service.patch(`/api/admin/users/${id}/status`, null, {
    params: { status }
  })
}

export function fetchCoupons(status) {
  return service.get('/api/admin/coupons', { params: status ? { status } : {} })
}

export function redeemCoupon(code) {
  return service.post('/api/admin/coupons/redeem', { code })
}

export function fetchAiConfig() {
  return service.get('/api/admin/ai-config')
}

export function saveAiConfig(data) {
  return service.put('/api/admin/ai-config', data)
}

export function fetchSiteSetting() {
  return service.get('/api/admin/site-setting')
}

export function saveSiteSetting(data) {
  return service.put('/api/admin/site-setting', data)
}

export function fetchPointsPackages() {
  return service.get('/api/admin/points-packages')
}

export function createPointsPackage(data) {
  return service.post('/api/admin/points-packages', data)
}

export function updatePointsPackage(data) {
  return service.put('/api/admin/points-packages', data)
}

export function disablePointsPackage(id) {
  return service.delete(`/api/admin/points-packages/${id}`)
}

export function fetchCouponTiers() {
  return service.get('/api/admin/coupon-tiers')
}

export function createCouponTier(data) {
  return service.post('/api/admin/coupon-tiers', data)
}

export function updateCouponTier(data) {
  return service.put('/api/admin/coupon-tiers', data)
}

export function disableCouponTier(id) {
  return service.delete(`/api/admin/coupon-tiers/${id}`)
}

// ---------- 店长后台 ----------
export function fetchStoreProfile() {
  return service.get('/api/admin/store/profile')
}

export function saveStoreProfile(data) {
  return service.put('/api/admin/store/profile', data)
}

export function fetchStoreDashboard(days) {
  return service.get('/api/admin/store/dashboard', { params: days ? { days } : {} })
}

export function fetchStoreCouponTiers() {
  return service.get('/api/admin/store/coupons')
}

export function createStoreCouponTier(data) {
  return service.post('/api/admin/store/coupons', data)
}

export function updateStoreCouponTier(data) {
  return service.put('/api/admin/store/coupons', data)
}

export function disableStoreCouponTier(id) {
  return service.delete(`/api/admin/store/coupons/${id}`)
}

export function fetchStoreStaff() {
  return service.get('/api/admin/store/staff')
}

export function createStoreStaff(data) {
  return service.post('/api/admin/store/staff', data)
}

export function updateStoreStaff(id, data) {
  return service.put(`/api/admin/store/staff/${id}`, data)
}

export function deleteStoreStaff(id) {
  return service.delete(`/api/admin/store/staff/${id}`)
}

export function fetchStoreOrders(status) {
  return service.get('/api/admin/store/orders', { params: status ? { status } : {} })
}

export function fetchStoreCouponInstances(status) {
  return service.get('/api/admin/store/coupon-instances', { params: status ? { status } : {} })
}

export function verifyStoreCoupon(code, staffId) {
  return service.post('/api/admin/store/verify', { code, staffId })
}

// ---------- 平台总后台 ----------
export function fetchPlatformDashboard() {
  return service.get('/api/admin/platform/dashboard')
}

export function fetchTenants() {
  return service.get('/api/admin/platform/tenants')
}

export function createTenant(data) {
  return service.post('/api/admin/platform/tenants', data)
}

export function updateTenantStatus(id, status) {
  return service.put(`/api/admin/platform/tenants/${id}/status`, null, { params: { status } })
}

export function fetchPlatformStores() {
  return service.get('/api/admin/platform/stores')
}

export function updateStoreKyc(id, status) {
  return service.put(`/api/admin/platform/stores/${id}/kyc`, null, { params: { status } })
}

export function updateStoreCommission(id, rate) {
  return service.put(`/api/admin/platform/stores/${id}/commission`, null, { params: { rate } })
}

export function fetchCommissions(status) {
  return service.get('/api/admin/platform/commissions', { params: status ? { status } : {} })
}

export function fetchPlatformOrders() {
  return service.get('/api/admin/platform/orders')
}

export default service
