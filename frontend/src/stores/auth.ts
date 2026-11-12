import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, write, ApiError } from '../api'
import type { User } from '../types'
export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const loaded = ref(false)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  async function load() {
    try { user.value = await api<User>('/auth/me') } catch (error) { if (!(error instanceof ApiError && error.status === 401)) throw error; user.value = null }
    loaded.value = true
  }
  async function login(username: string, password: string) { user.value = await write<User>('/auth/login', 'POST', { username, password }); loaded.value = true }
  async function logout() { await write('/auth/logout', 'POST'); user.value = null; loaded.value = true }
  return { user, loaded, isAdmin, load, login, logout }
})
