const TOKEN_KEYS = {
  PLATFORM_ADMIN: 'fajing_admin_token_platform',
  STORE_MANAGER: 'fajing_admin_token_store'
}
const INFO_KEYS = {
  PLATFORM_ADMIN: 'fajing_admin_info_platform',
  STORE_MANAGER: 'fajing_admin_info_store'
}

/** 按当前路由判断登录态归属：/store/** 属于店长后台，其余属于平台总后台 */
function currentScope() {
  const hash = typeof window !== 'undefined' ? window.location.hash.replace(/^#/, '') : ''
  return hash.startsWith('/store') ? 'STORE_MANAGER' : 'PLATFORM_ADMIN'
}

export function getToken(scope) {
  return localStorage.getItem(TOKEN_KEYS[scope || currentScope()]) || ''
}

export function setToken(value, scope) {
  if (!value) return
  localStorage.setItem(TOKEN_KEYS[scope || currentScope()], value)
}

export function clearToken(scope) {
  localStorage.removeItem(TOKEN_KEYS[scope || currentScope()])
}

export function getAdminInfo(scope) {
  try {
    const raw = localStorage.getItem(INFO_KEYS[scope || currentScope()])
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export function setAdminInfo(info) {
  if (!info || !info.role) return
  localStorage.setItem(INFO_KEYS[info.role], JSON.stringify(info))
}

export function clearAdminInfo(scope) {
  localStorage.removeItem(INFO_KEYS[scope || currentScope()])
}

export function getRole(scope) {
  const info = getAdminInfo(scope)
  return info && info.role ? info.role : 'PLATFORM_ADMIN'
}

export function homePath(role) {
  return role === 'STORE_MANAGER' ? '/store/dashboard' : '/dashboard'
}
