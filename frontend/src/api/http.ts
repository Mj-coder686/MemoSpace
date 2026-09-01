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
  if (error.response?.status === 401 && !location.pathname.includes('/login')) {
    localStorage.removeItem('memospace_token')
    localStorage.removeItem('memospace_user')
    location.href = '/login'
  }
  return Promise.reject(error)
})

export const errorMessage = (error: any) => error?.response?.data?.message || error?.message || '请稍后再试'
export default http
