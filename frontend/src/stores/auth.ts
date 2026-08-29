import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '../api/http'

export interface User {
  id: number
  publicId?: string
  public_id?: string
  username: string
  nickname: string
  avatar?: string
  bio?: string
  location?: string
  gender?: string
  birthday?: string
}

const normalizeUser = (value: User): User => ({ ...value, publicId: value.publicId || value.public_id })

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('memospace_token') || '')
  const cached = localStorage.getItem('memospace_user')
  const user = ref<User | null>(cached ? normalizeUser(JSON.parse(cached)) : null)

  const persist = (data: { token: string; user: User }) => {
    token.value = data.token
    user.value = normalizeUser(data.user)
    localStorage.setItem('memospace_token', data.token)
    localStorage.setItem('memospace_user', JSON.stringify(user.value))
  }

  const login = async (username: string, password: string) => {
    const { data } = await http.post('/auth/login', { username, password })
    persist(data)
  }

  const register = async (username: string, password: string, nickname: string) => {
    const { data } = await http.post('/auth/register', { username, password, nickname })
    persist(data)
  }

  const loadMe = async () => {
    if (!token.value) return
    const { data } = await http.get('/users/me')
    user.value = normalizeUser(data)
    localStorage.setItem('memospace_user', JSON.stringify(user.value))
  }

  const logout = () => {
    token.value = ''
    user.value = null
    localStorage.removeItem('memospace_token')
    localStorage.removeItem('memospace_user')
  }

  return { token, user, login, register, loadMe, logout }
})
