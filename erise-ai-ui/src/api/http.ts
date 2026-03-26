import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/models'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('erise-access-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response): any => {
    const payload = response.data as ApiResponse<unknown>
    if (payload.code !== 0) {
      ElMessage.error(payload.message)
      return Promise.reject(new Error(payload.message))
    }
    return payload.data
  },
  (error) => {
    const message = error.response?.data?.message ?? error.message ?? 'Request failed'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default http
