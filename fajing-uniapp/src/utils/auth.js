const TOKEN_KEY = 'fajing_token'
const PROFILE_DONE_KEY = 'fajing_logged_in'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY)
}

/** 是否已完成登录授权（头像昵称/手机号流程走完即视为已登录，本地记住状态） */
export function isProfileComplete() {
  return uni.getStorageSync(PROFILE_DONE_KEY) === '1'
}

export function markProfileComplete() {
  uni.setStorageSync(PROFILE_DONE_KEY, '1')
}

export function clearProfileComplete() {
  uni.removeStorageSync(PROFILE_DONE_KEY)
}
