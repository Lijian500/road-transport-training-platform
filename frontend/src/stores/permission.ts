import { computed } from 'vue'
import { defineStore } from 'pinia'

import { useAuthStore } from './auth'

export const usePermissionStore = defineStore('permission', () => {
  const authStore = useAuthStore()
  const permissionSet = computed(() => new Set(authStore.permissions))

  function has(permission: string) {
    return permissionSet.value.has(permission)
  }

  return {
    has,
  }
})
