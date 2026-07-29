import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export interface CurrentUser {
  userId: string
  displayName: string
}

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUser | null>(null)
  const permissions = ref<string[]>([])
  const authenticated = computed(() => currentUser.value !== null)

  function establishSession(user: CurrentUser, grantedPermissions: string[]) {
    currentUser.value = user
    permissions.value = grantedPermissions
  }

  function clearSession() {
    currentUser.value = null
    permissions.value = []
  }

  return {
    currentUser,
    permissions,
    authenticated,
    establishSession,
    clearSession,
  }
})
