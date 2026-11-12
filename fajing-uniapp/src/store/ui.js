import { defineStore } from 'pinia'

/** 全局 UI 状态：toast + 底部弹层 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    toastText: '',
    toastVisible: false,
    sheet: '',
    commentPost: null,
    payPlan: 'all',
    payStyleId: '',
    /** 发讨论模式：ask=还没剪（只传一张现在的发型），real=交作业（AI 图 + 剪后实拍） */
    composeMode: 'real',
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
