import { defineStore } from 'pinia'

/** 全局 UI 状态：toast + 底部弹层 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    toastText: '',
    toastVisible: false,
    sheet: '',
    quotaReason: ''
  }),
  actions: {
    toast(text) {
      this.toastText = text
      this.toastVisible = true
      clearTimeout(this._timer)
      this._timer = setTimeout(() => {
        this.toastVisible = false
      }, 2000)
    },
    openSheet(name) {
      this.sheet = name
    },
    close() {
      this.sheet = ''
    }
  }
})
