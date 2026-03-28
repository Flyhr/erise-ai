import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/models'

const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
const normalizedApiBase = apiBase.endsWith('/') ? apiBase.slice(0, -1) : apiBase

const http = axios.create({
  baseURL: normalizedApiBase,
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
      const message = payload.message ?? payload.msg ?? 'Request failed'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return payload.data
  },
  (error) => {
    const message = error.response?.data?.message ?? error.response?.data?.msg ?? error.message ?? 'Request failed'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export const resolveApiUrl = (path: string) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedApiBase}${normalizedPath}`
}

export default http
