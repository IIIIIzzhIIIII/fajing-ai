import { defineStore } from 'pinia'

const KEY = 'fajing_state_v1'

/**
 * 全局业务状态
 * 额度为账号终身：首次免费 1 次 + 分享奖励 1 次，不再每日重置
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
    quota: { freeLeft: 1, shareClaimed: false },
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
      } catch (e) {
        // 本地缓存损坏时静默重置
        uni.removeStorageSync(KEY)
      }
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
      this.quota = { ...this.quota, ...quota }
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
      this.quota = { freeLeft: 1, shareClaimed: false }
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
