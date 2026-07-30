import http, { ensureCsrfToken } from './http'

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthSession {
  userId: string
  enterpriseId: string | null
  username: string
  displayName: string
  enterpriseName: string | null
  platformAdmin: boolean
  mustChangePassword: boolean
  roles: string[]
  permissions: string[]
  workspaces: Array<'admin' | 'student'>
  defaultWorkspace: 'admin' | 'student'
}

export async function login(data: LoginRequest) {
  await ensureCsrfToken()
  return http.post<AuthSession>('/auth/login', data)
}

export async function currentSession() {
  await ensureCsrfToken()
  return http.get<AuthSession>('/auth/me')
}

export function changePassword(oldPassword: string, newPassword: string) {
  return http.post<AuthSession>('/auth/change-password', {
    oldPassword,
    newPassword,
  })
}

export function logout() {
  return http.post<void>('/auth/logout')
}
