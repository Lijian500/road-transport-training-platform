import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  changePassword as changePasswordRequest,
  currentSession,
  login as loginRequest,
  logout as logoutRequest,
  type AuthSession,
  type LoginRequest,
} from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(null)
  const initialized = ref(false)
  const loading = ref(false)

  const currentUser = computed(() => session.value)
  const permissions = computed(() => session.value?.permissions ?? [])
  const authenticated = computed(() => session.value !== null)
  const mustChangePassword = computed(() => session.value?.mustChangePassword ?? false)

  async function login(data: LoginRequest) {
    loading.value = true
    try {
      session.value = await loginRequest(data)
      initialized.value = true
      return session.value
    } finally {
      loading.value = false
    }
  }

  async function restoreSession() {
    if (initialized.value) {
      return session.value
    }
    try {
      session.value = await currentSession()
    } catch {
      session.value = null
    } finally {
      initialized.value = true
    }
    return session.value
  }

  async function logout() {
    try {
      await logoutRequest()
    } finally {
      clearSession()
    }
  }

  async function changePassword(oldPassword: string, newPassword: string) {
    session.value = await changePasswordRequest(oldPassword, newPassword)
    return session.value
  }

  function clearSession() {
    session.value = null
    initialized.value = true
  }

  window.addEventListener('auth:expired', clearSession)

  return {
    session,
    currentUser,
    permissions,
    initialized,
    loading,
    authenticated,
    mustChangePassword,
    login,
    restoreSession,
    logout,
    changePassword,
    clearSession,
  }
})
