import { http, type PageResult } from '@/api/http'

export interface Vehicle {
  id: string
  plateNumber: string
  vehicleType: string
  orgId?: string
  orgName?: string
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  createdAt: string
}
export interface VehicleInput {
  plateNumber: string
  vehicleType: string
  orgId?: string
  remark?: string
}

/** 分页查询本企业车辆。 */
export function getVehicles(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: string
  orgId?: string
}) {
  return http.get<PageResult<Vehicle>>('/admin/vehicles', { params })
}
/** 获取车辆管理的部门选项。 */
export function getVehicleDepartments() {
  return http.get<Array<{ id: string; name: string }>>('/admin/vehicles/departments')
}
/** 新增车辆。 */
export function createVehicle(data: VehicleInput) {
  return http.post<Vehicle>('/admin/vehicles', data)
}
/** 编辑车辆。 */
export function updateVehicle(id: string, data: VehicleInput) {
  return http.put<Vehicle>(`/admin/vehicles/${id}`, data)
}
/** 启停车辆并保留记录。 */
export function changeVehicleStatus(id: string, status: Vehicle['status']) {
  return http.put(`/admin/vehicles/${id}/status`, { status })
}
