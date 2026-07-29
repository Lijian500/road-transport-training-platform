import { storeToRefs } from 'pinia'

import { useAuthStore } from '@/stores/auth'

export function useAuth() {
  const authStore = useAuthStore()
  const { currentUser, authenticated } = storeToRefs(authStore)

  return {
    currentUser,
    authenticated,
    clearSession: authStore.clearSession,
  }
}
