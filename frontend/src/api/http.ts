import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  timeout: 120000
})

// 请求拦截：附加 JWT
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mydbd-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包 { code, message, data }
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (!(response.config as any)?.skipErrorMessage) {
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    // 预留能力接口（如终端指令/逆地理）后端尚未实现时，调用方设置 skipErrorMessage
    // 自行决定降级提示，不走全局错误弹窗
    const silent = (error.config as any)?.skipErrorMessage
    if (status === 401) {
      ElMessage.error('登录已失效，请重新登录')
      localStorage.removeItem('mydbd-token')
      window.location.href = '/login'
    } else if (!silent) {
      // message：Java 平台统一响应体；detail：FastAPI（processing）错误结构
      ElMessage.error(error.response?.data?.message || error.response?.data?.detail || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default http
