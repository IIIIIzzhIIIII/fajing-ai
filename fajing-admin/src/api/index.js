import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000
})

service.interceptors.request.use((config) => {
  config.headers['X-Admin-Token'] = 'admin-token'
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

export function fetchPosts() {
  return service.get('/api/admin/posts')
}

export function deletePost(id) {
  return service.delete(`/api/admin/posts/${id}`)
}

export function fetchOrders() {
  return service.get('/api/admin/orders')
}

export default service
