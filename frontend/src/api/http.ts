import axios from 'axios'

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
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
