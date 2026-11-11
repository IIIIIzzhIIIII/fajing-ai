const TOKEN_KEY = 'fajing_admin_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(value) {
  localStorage.setItem(TOKEN_KEY, value)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}
