import { getToken } from '@/utils/auth'

const BASE_URL = (import.meta.env && import.meta.env.VITE_API_BASE) || ''
const queue = []
let timer = null

/** 当前渠道标识，用于后台「渠道与传播」统计 */
function platformChannel() {
  let channel = 'unknown'
  // #ifdef MP-WEIXIN
  channel = 'mp-weixin'
  // #endif
  // #ifdef H5
  channel = 'h5'
  // #endif
  return channel
}

/**
 * 关键行为埋点：批量上报，失败静默，绝不阻断业务。
 * 事件：upload_success / generate_start / generate_success / result_view /
 *       save_image / pay_click / pay_success / post_create
 * channel 固定为运行平台，业务字段请用其他 key（如 payMethod / shareChannel），避免覆盖渠道归因。
 */
export function track(event, payload = {}) {
  if (!BASE_URL || !event) return
  queue.push({ event, payload: { ...payload, channel: platformChannel() } })
  if (queue.length >= 20) {
    flush()
    return
  }
  if (!timer) {
    timer = setTimeout(() => {
      timer = null
      flush()
    }, 1000)
  }
}

function flush() {
  if (!queue.length) return
  const events = queue.splice(0, 20)
  const token = getToken()
  uni.request({
    url: BASE_URL + '/api/event',
    method: 'POST',
    data: { events },
    header: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    timeout: 8000,
    fail: () => {}
  })
}
