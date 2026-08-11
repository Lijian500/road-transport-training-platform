import http, { type PageResult } from '@/api/http'

export type Status = 'ENABLED' | 'DISABLED'

export interface Enterprise {
  id: string
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  address?: string
  status: Status
  createdAt: string
}

export interface EnterprisePayload {
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  address?: string
  adminUsername: string
  adminDisplayName: string
  adminPhone?: string
  temporaryPassword: string
}

export interface EnterpriseAdministrator {
  id: string
  username: string
  displayName: string
  phone?: string
  status: Status
  mustChangePassword: boolean
  createdAt: string
}

export interface OrgNode {
  id: string
  parentId: string | null
  name: string
  code: string
  type: 'ENTERPRISE' | 'DEPARTMENT'
  status: Status
  sortOrder: number
  children: OrgNode[]
}

export interface OrgPayload {
  parentId: string | null
  name: string
  code: string
  sortOrder: number
}

export interface User {
  id: string
  enterpriseId: string
  orgId: string
  orgName: string
  username: string
  displayName: string
  phone?: string
  status: Status
  mustChangePassword: boolean
  roleIds: string[]
  roleNames: string[]
  createdAt: string
}

export interface UserPayload {
  username: string
  displayName: string
  phone?: string
  orgId: string
  temporaryPassword: string
  roleIds: string[]
}

export interface Role {
  id: string
  code: string
  name: string
  description?: string
  status: Status
  builtIn: boolean
  permissionIds: string[]
  createdAt: string
}

export interface PermissionNode {
  id: string
  parentId: string | null
  code: string
  name: string
  type: 'MENU' | 'ACTION'
  scope: 'PLATFORM' | 'ENTERPRISE' | 'COMMON'
  sortOrder: number
  children: PermissionNode[]
}

export function getEnterprises(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: Status | ''
}) {
  return http.get<PageResult<Enterprise>>('/admin/enterprises', { params })
}

export function createEnterprise(data: EnterprisePayload) {
  return http.post<Enterprise>('/admin/enterprises', data)
}

export function updateEnterprise(
  id: string,
  data: Omit<
    EnterprisePayload,
    'code' | 'adminUsername' | 'adminDisplayName' | 'adminPhone' | 'temporaryPassword'
  >,
) {
  return http.put<Enterprise>(`/admin/enterprises/${id}`, data)
}

export function changeEnterpriseStatus(id: string, status: Status) {
  return http.patch<void>(`/admin/enterprises/${id}/status`, { status })
}

/** 查询指定企业的管理员账号。 */
export function getEnterpriseAdministrators(id: string) {
  return http.get<EnterpriseAdministrator[]>(`/admin/enterprises/${id}/administrators`)
}

/** 重置指定企业管理员的临时密码。 */
export function resetEnterpriseAdministratorPassword(
  enterpriseId: string,
  userId: string,
  temporaryPassword: string,
) {
  return http.put<void>(`/admin/enterprises/${enterpriseId}/administrators/${userId}/password`, {
    temporaryPassword,
  })
}

export function getOrgTree() {
  return http.get<OrgNode[]>('/admin/orgs/tree')
}

export function createOrg(data: OrgPayload) {
  return http.post<OrgNode>('/admin/orgs', data)
}

export function updateOrg(id: string, data: OrgPayload) {
  return http.put<OrgNode>(`/admin/orgs/${id}`, data)
}

export function deleteOrg(id: string) {
  return http.delete<void>(`/admin/orgs/${id}`)
}

export function getUsers(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  orgId?: string
  status?: Status | ''
}) {
  return http.get<PageResult<User>>('/admin/users', { params })
}

export function createUser(data: UserPayload) {
  return http.post<User>('/admin/users', data)
}

export function updateUser(id: string, data: Pick<UserPayload, 'displayName' | 'phone' | 'orgId'>) {
  return http.put<User>(`/admin/users/${id}`, data)
}

export function changeUserStatus(id: string, status: Status) {
  return http.patch<void>(`/admin/users/${id}/status`, { status })
}

export function resetUserPassword(id: string, temporaryPassword: string) {
  return http.put<void>(`/admin/users/${id}/password`, { temporaryPassword })
}

export function assignUserRoles(id: string, roleIds: string[]) {
  return http.put<void>(`/admin/users/${id}/roles`, { roleIds })
}

export function getRoles(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: Status | ''
}) {
  return http.get<PageResult<Role>>('/admin/roles', { params })
}

export function getRoleOptions() {
  return http.get<Role[]>('/admin/roles/options')
}

export function createRole(data: { code: string; name: string; description?: string }) {
  return http.post<Role>('/admin/roles', data)
}

export function updateRole(id: string, data: { name: string; description?: string }) {
  return http.put<Role>(`/admin/roles/${id}`, data)
}

export function changeRoleStatus(id: string, status: Status) {
  return http.patch<void>(`/admin/roles/${id}/status`, { status })
}

export function deleteRole(id: string) {
  return http.delete<void>(`/admin/roles/${id}`)
}

export function getPermissionTree() {
  return http.get<PermissionNode[]>('/admin/permissions/tree')
}

export function assignRolePermissions(id: string, permissionIds: string[]) {
  return http.put<void>(`/admin/roles/${id}/permissions`, { permissionIds })
}
