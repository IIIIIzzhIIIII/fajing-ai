import { ref } from 'vue'
import { isProfileComplete, markProfileComplete } from '@/utils/auth'

/** 全局共用的登录拦截弹窗状态（各页面挂载同一个 FjLoginModal） */
const visible = ref(false)
let pendingAction = null

/**
 * 登录拦截：已完成登录授权则直接执行；否则弹登录弹窗，记住待执行动作。
 */
export function ensureLogin(action) {
  if (isProfileComplete()) {
    if (action) action()
    return
  }
  pendingAction = action || null
  visible.value = true
}

/** 登录弹窗完成（保存或跳过）后：记住状态、关闭弹窗、继续原操作 */
export function finishLogin() {
  markProfileComplete()
  visible.value = false
  const action = pendingAction
  pendingAction = null
  if (action) action()
}

export function closeLogin() {
  visible.value = false
  pendingAction = null
}

export function useLoginGate() {
  return {
    visible,
    ensureLogin,
    finishLogin,
    closeLogin
  }
}
