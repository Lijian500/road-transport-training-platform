import http, { type PageResult } from '@/api/http'

export type Status = 'ENABLED' | 'DISABLED'
export type OrganizationNature = 'ENTERPRISE' | 'REGULATOR'

export interface AddressPathNode {
  id: string
  level: 1 | 2 | 3
  areaCode: string
  name: string
}

export interface Enterprise {
  id: string
  code: string
  name: string
  organizationNature: OrganizationNature
  areaId: string | null
  areaName?: string
  areaPath: AddressPathNode[]
  contactName?: string
  contactPhone?: string
  address?: string
  status: Status
  createdAt: string
}

export interface EnterprisePayload {
  code: string
  name: string
  organizationNature: OrganizationNature
  areaId: string
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

export interface AddressNode {
  id: string
  level: 1 | 2 | 3
  parentCode: string
  areaCode: string
  zipCode: string
  cityCode: string
  name: string
  shortName: string
  mergerName: string
  pinyin: string
  lng: number
  lat: number
  hasChildren: boolean
}

export interface AddressPayload {
  parentCode: string
  areaCode: string
  zipCode: string
  cityCode: string
  name: string
  shortName: string
  mergerName: string
  pinyin: string
  lng: number
  lat: number
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

/** 分页查询企业和行管组织。 */
export function getEnterprises(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: Status | ''
  organizationNature?: OrganizationNature | ''
}) {
  return http.get<PageResult<Enterprise>>('/admin/enterprises', { params })
}

/** 创建企业或行管组织并初始化管理员。 */
export function createEnterprise(data: EnterprisePayload) {
  return http.post<Enterprise>('/admin/enterprises', data)
}

/** 编辑组织资料和行政辖区，组织类型不可修改。 */
export function updateEnterprise(
  id: string,
  data: Omit<
    EnterprisePayload,
    | 'code'
    | 'organizationNature'
    | 'adminUsername'
    | 'adminDisplayName'
    | 'adminPhone'
    | 'temporaryPassword'
  >,
) {
  return http.put<Enterprise>(`/admin/enterprises/${id}`, data)
}

/** 启用或禁用指定组织。 */
export function changeEnterpriseStatus(id: string, status: Status) {
  return http.patch<void>(`/admin/enterprises/${id}/status`, { status })
}

/** 查询指定组织的管理员账号。 */
export function getEnterpriseAdministrators(id: string) {
  return http.get<EnterpriseAdministrator[]>(`/admin/enterprises/${id}/administrators`)
}

/** 重置指定组织管理员的临时密码。 */
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

/** 按父级行政代码查询直属下级地址。 */
export function getAddressChildren(parentCode = '0') {
  return http.get<AddressNode[]>('/admin/addresses/children', { params: { parentCode } })
}

/** 新增省级地址或指定地址的下级。 */
export function createAddress(data: AddressPayload) {
  return http.post<AddressNode>('/admin/addresses', data)
}

/** 编辑行政地址基本信息。 */
export function updateAddress(id: string, data: AddressPayload) {
  return http.put<AddressNode>(`/admin/addresses/${id}`, data)
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
