import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

// 统一 axios 实例：baseURL=/api 前缀由网关按域路由
const request: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 15000,
})

// 请求拦截：携带 Access Token
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ---- 401 刷新队列（并发请求只刷新一次）----
let refreshing = false
let pendingQueue: Array<(token: string) => void> = []

async function refreshToken(): Promise<string> {
  // TODO(W2)：POST /api/auth/refresh（Refresh 7d，family 重放检测）
  throw new Error('refresh not implemented')
}

request.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const { response, config } = error
    if (response?.status === 401 && !config.__retry) {
      config.__retry = true
      if (refreshing) {
        return new Promise((resolve) => {
          pendingQueue.push((token) => {
            config.headers.Authorization = `Bearer ${token}`
            resolve(request(config))
          })
        })
      }
      refreshing = true
      try {
        const newToken = await refreshToken()
        localStorage.setItem('access_token', newToken)
        pendingQueue.forEach((cb) => cb(newToken))
        pendingQueue = []
        config.headers.Authorization = `Bearer ${newToken}`
        return request(config)
      } catch {
        // 刷新失败 → 跳登录
        localStorage.removeItem('access_token')
        window.location.href = '/login'
        return Promise.reject(error)
      } finally {
        refreshing = false
      }
    }
    return Promise.reject(error)
  },
)

export default request
