import http from './http'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  userId: string
  displayName: string
  permissions: string[]
}

export function login(data: LoginRequest) {
  return http.post<LoginResponse>('/auth/login', data)
}

export function logout() {
  return http.post<void>('/auth/logout')
}
