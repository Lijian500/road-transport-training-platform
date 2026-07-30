import { createPinia, setActivePinia } from 'pinia'

import type { AuthSession } from '@/api/auth'

import { useAuthStore } from './auth'
import { matchesPermission, usePermissionStore } from './permission'

describe('permission store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('支持精确权限及星号通配符', () => {
    expect(matchesPermission('admin:user:view', 'admin:user:view')).toBe(true)
    expect(matchesPermission('admin:user:*', 'admin:user:update')).toBe(true)
    expect(matchesPermission('admin:role:view', 'admin:user:view')).toBe(false)
  })

  it('使用当前会话权限判断操作可见性', () => {
    const authStore = useAuthStore()
    const permissionStore = usePermissionStore()
    authStore.session = {
      userId: '1',
      enterpriseId: '10',
      username: 'admin',
      displayName: '管理员',
      enterpriseName: '示例企业',
      platformAdmin: false,
      mustChangePassword: false,
      roles: ['ENTERPRISE_ADMIN'],
      permissions: ['admin:user:*'],
      workspaces: ['admin'],
      defaultWorkspace: 'admin',
    } satisfies AuthSession

    expect(permissionStore.has('admin:user:create')).toBe(true)
    expect(permissionStore.has('admin:role:create')).toBe(false)
  })
})
