import axios from 'axios'
import { apiBaseUrl } from '../utils/serverConnection'

const http = axios.create({ timeout: 15000 })

http.interceptors.request.use((config) => {
  config.baseURL = apiBaseUrl()
  const token = localStorage.getItem('memospace_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(undefined, (error) => {
  const responseMessage = String(error.response?.data?.message || '')
  if (error.response?.status === 403 && responseMessage.includes('账号已被封禁')) {
    localStorage.removeItem('memospace_token')
    localStorage.removeItem('memospace_user')
    if (!location.pathname.includes('/login')) location.href = '/login?reason=banned'
    return Promise.reject(error)
  }
  if (error.response?.status === 401 && !location.pathname.includes('/login')) {
    localStorage.removeItem('memospace_token')
    localStorage.removeItem('memospace_user')
    location.href = '/login'
  }
  return Promise.reject(error)
})

export const errorMessage = (error: any) => {
  const serverMessage = error?.response?.data?.message
  if (serverMessage) return serverMessage
  if (error?.code === 'ECONNABORTED') return '服务器响应超时，请稍后重试。'
  if (error?.code === 'ERR_NETWORK' || error?.message === 'Network Error') {
    return '暂时无法连接服务器，请检查网络后刷新页面重试。'
  }
  return error?.message || '请稍后再试'
}
export default http
