import { usePermissionStore } from '@/stores/permission'

export function usePermission() {
  const permissionStore = usePermissionStore()
  return {
    hasPermission: permissionStore.has,
  }
}
