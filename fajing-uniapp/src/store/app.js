import { defineStore } from 'pinia'

const KEY = 'fajing_state_v1'

/** 本地日期（YYYY-MM-DD），用于每日额度重置 */
function todayKey() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/**
 * 全局业务状态
 * 注意：分享奖励每账号终身仅 1 次（shareClaimed 一旦为 true 不再重置）
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    photoPath: '',
    photoUrl: '',
    gender: 'female',
    mode: 'hair',
    selectedStyleIds: [],
    selectedColorId: '',
    taskId: '',
    result: null,
    quota: { freeLeft: 1, shareClaimed: false, adUsed: false, quotaDate: '' },
    unlocks: [],
    history: [],
    lastOrder: null
  }),
  getters: {
    totalTried: (s) => s.history.length,
    unlockedCount: (s) => s.unlocks.length
  },
  actions: {
    restore() {
      try {
        const raw = uni.getStorageSync(KEY)
        if (raw) {
          const data = typeof raw === 'string' ? JSON.parse(raw) : raw
          if (data.quota) this.quota = { ...this.quota, ...data.quota }
          // 解锁按「任务:款式」存储；没有任务前缀的历史数据直接丢弃，避免旧解锁让新一批出图免费
          if (Array.isArray(data.unlocks)) {
            this.unlocks = data.unlocks.filter((key) => typeof key === 'string' && key.indexOf(':') > -1)
          }
          if (Array.isArray(data.history)) this.history = data.history
        }
        this.resetDailyQuota()
      } catch (e) {
        // 本地缓存损坏时静默重置
        uni.removeStorageSync(KEY)
        this.resetDailyQuota()
      }
    },
    /** 每天重置 1 次免费额度与广告奖励标记（分享奖励终身 1 次，不重置） */
    resetDailyQuota() {
      const today = todayKey()
      if (this.quota.quotaDate === today) return
      this.quota = { ...this.quota, freeLeft: 1, adUsed: false, quotaDate: today }
      this.persist()
    },
    persist() {
      uni.setStorageSync(
        KEY,
        JSON.stringify({
          quota: this.quota,
          unlocks: this.unlocks,
          history: this.history
        })
      )
    },
    setPhoto(path, url) {
      this.photoPath = path
      this.photoUrl = url || ''
    },
    setSelection({ gender, mode, styleIds, colorId }) {
      if (gender) this.gender = gender
      if (mode) this.mode = mode
      if (styleIds) this.selectedStyleIds = styleIds
      if (colorId !== undefined) this.selectedColorId = colorId
    },
    /** 服务端额度为唯一权威：整包覆盖本地展示值 */
    setQuota(quota) {
      if (!quota) return
      const next = { ...this.quota, ...quota }
      // 服务端返回的 freeLeft 是当日权威值，同步标记本地日期，
      // 避免下次启动 resetDailyQuota 又把它重置成默认的 1 次
      if (quota.freeLeft !== undefined) next.quotaDate = todayKey()
      this.quota = next
      this.persist()
    },
    addUnlocks(ids) {
      const set = new Set(this.unlocks.concat(ids))
      this.unlocks = Array.from(set)
      this.persist()
    },
    /** 以服务端返回的解锁列表为准（taskId:styleId） */
    setUnlocks(keys) {
      this.unlocks = Array.from(new Set((keys || []).filter(Boolean)))
      this.persist()
    },
    addHistory(item) {
      this.history = [item, ...this.history].slice(0, 30)
      this.persist()
    },
    setResult(result) {
      this.result = result
      this.taskId = result && result.taskId ? result.taskId : ''
    },
    resetAll() {
      this.quota = { freeLeft: 1, shareClaimed: false, adUsed: false, quotaDate: '' }
      this.unlocks = []
      this.history = []
      this.photoPath = ''
      this.photoUrl = ''
      this.result = null
      this.taskId = ''
      uni.removeStorageSync(KEY)
      uni.removeStorageSync('fajing_pending_order')
      uni.removeStorageSync('fajing_pending_result')
      uni.removeStorageSync('fajing_picked_styles')
    }
  }
})
