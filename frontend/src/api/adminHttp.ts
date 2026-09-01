import axios from 'axios'
import { apiBaseUrl } from '../utils/serverConnection'

const adminHttp = axios.create({ timeout: 15000 })

adminHttp.interceptors.request.use((config) => {
  config.baseURL = apiBaseUrl()
  const token = localStorage.getItem('memospace_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

adminHttp.interceptors.response.use(undefined, (error) => {
  if ((error.response?.status === 401 || error.response?.status === 403) && location.pathname === '/admin') {
    localStorage.removeItem('memospace_admin_token')
    localStorage.removeItem('memospace_admin_user')
    location.href = '/admin/login'
  }
  return Promise.reject(error)
})

export default adminHttp
