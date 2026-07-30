import { createPinia, setActivePinia } from 'pinia'
import { vi } from 'vitest'

import {
  currentSession,
  login as loginRequest,
  logout as logoutRequest,
  type AuthSession,
} from '@/api/auth'

import { useAuthStore } from './auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  currentSession: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
}))

const session: AuthSession = {
  userId: '1',
  enterpriseId: null,
  username: 'root',
  displayName: '平台管理员',
  enterpriseName: null,
  platformAdmin: true,
  mustChangePassword: false,
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
  workspaces: ['admin'],
  defaultWorkspace: 'admin',
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('登录成功后建立内存会话', async () => {
    vi.mocked(loginRequest).mockResolvedValue(session)
    const store = useAuthStore()

    await store.login({ username: 'root', password: 'Password1' })

    expect(store.authenticated).toBe(true)
    expect(store.session?.displayName).toBe('平台管理员')
  })

  it('恢复失败时保持未登录状态', async () => {
    vi.mocked(currentSession).mockRejectedValue(new Error('unauthorized'))
    const store = useAuthStore()

    await store.restoreSession()

    expect(store.initialized).toBe(true)
    expect(store.authenticated).toBe(false)
  })

  it('退出时总会清理本地会话', async () => {
    vi.mocked(logoutRequest).mockRejectedValue(new Error('network'))
    const store = useAuthStore()
    store.session = session

    await expect(store.logout()).rejects.toThrow('network')
    expect(store.session).toBeNull()
  })
})
