import { defineStore } from 'pinia'

import { useAuthStore } from './auth'

export function matchesPermission(granted: string, required: string) {
  if (granted === '*' || granted === required) {
    return true
  }
  const grantedParts = granted.split(':')
  const requiredParts = required.split(':')
  return (
    grantedParts.length === requiredParts.length &&
    grantedParts.every((part, index) => part === '*' || part === requiredParts[index])
  )
}

export const usePermissionStore = defineStore('permission', () => {
  const authStore = useAuthStore()

  function has(permission?: string) {
    if (!permission) {
      return true
    }
    return authStore.permissions.some((granted) => matchesPermission(granted, permission))
  }

  return {
    has,
  }
})
