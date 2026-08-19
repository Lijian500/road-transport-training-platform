/**
 * 课程、培训计划与考试REST接口按领域拆分到本目录。
 */
import { http, type PageResult } from '@/api/http'

export type CourseStatus = 'DRAFT' | 'ENABLED' | 'DISABLED'

export interface Courseware {
  id: string
  storageObjectId: string
  title: string
  originalFilename: string
  contentType: string
  fileSizeBytes: number
  durationSeconds: number
  sortOrder: number
  createdAt: string
}

export interface Course {
  id: string
  name: string
  description?: string
  coverObjectId?: string
  coverFilename?: string
  coverSizeBytes?: number
  coverContentType?: string
  requiredDurationSeconds: number
  allowSeek: boolean
  progressReportIntervalSeconds: number
  studyToleranceSeconds: number
  status: CourseStatus
  everEnabled: boolean
  coursewareCount: number
  totalVideoDurationSeconds: number
  coursewares: Courseware[]
  createdAt: string
  updatedAt: string
}

export interface CoursePayload {
  name: string
  description?: string
  requiredDurationSeconds: number
  allowSeek: boolean
  progressReportIntervalSeconds: number
  studyToleranceSeconds: number
}

export type PlanStatus = 'DRAFT' | 'PUBLISHED' | 'IN_PROGRESS' | 'FINISHED' | 'CANCELLED'

export interface PlanCoursewareSnapshot {
  id: string
  sourceCoursewareId: string
  storageObjectId: string
  title: string
  durationSeconds: number
  sortOrder: number
}

export interface PlanCourse {
  id: string
  courseId: string
  courseName: string
  requiredDurationSeconds: number
  allowSeek: boolean
  progressReportIntervalSeconds: number
  studyToleranceSeconds: number
  sortOrder: number
  coursewares: PlanCoursewareSnapshot[]
}

export interface PlanUser {
  id: string
  userId: string
  orgId?: string
  orgName?: string
  username: string
  displayName: string
  assignmentStatus: string
  studyStatus: string
  examStatus: string
  completionStatus: string
  completedAt?: string
}

export interface Plan {
  id: string
  name: string
  description?: string
  startAt: string
  endAt: string
  status: PlanStatus
  examRequired: boolean
  examPassScore?: number
  courses: PlanCourse[]
  users: PlanUser[]
  publishedAt?: string
  cancelledAt?: string
  createdAt: string
  updatedAt: string
}

export interface PlanCourseOption {
  courseId: string
  name: string
  requiredDurationSeconds: number
  coursewareCount: number
  totalVideoDurationSeconds: number
}

export interface PlanParticipantOption {
  userId: string
  orgId?: string
  orgName?: string
  username: string
  displayName: string
}

export interface PlanPayload {
  name: string
  description?: string
  startAt: string
  endAt: string
  examRequired: boolean
  courseIds?: string[]
  userIds?: string[]
}

export interface StudentPlan {
  taskId: string
  planId: string
  name: string
  description?: string
  startAt: string
  endAt: string
  status: Exclude<PlanStatus, 'DRAFT'>
  assignmentStatus: string
  studyStatus: string
  examStatus: string
  completionStatus: string
  courses: StudentPlanCourse[]
  publishedAt: string
}

export interface StudentPlanCourseware {
  id: string
  title: string
  durationSeconds: number
  sortOrder: number
}

export interface StudentPlanCourse {
  id: string
  courseName: string
  requiredDurationSeconds: number
  allowSeek: boolean
  progressReportIntervalSeconds: number
  studyToleranceSeconds: number
  sortOrder: number
  coursewares: StudentPlanCourseware[]
}

export interface StorageCapability {
  enabled: boolean
  message: string
  provider: string
  partSizeBytes: number
  maxVideoBytes: number
  maxCoverBytes: number
  uploadUrlTtlSeconds: number
  previewUrlTtlSeconds: number
  videoContentTypes: string[]
  coverContentTypes: string[]
}

export interface SignedRequest {
  partNumber?: number
  url: string
  method: string
  headers: Record<string, string>
  expiresAt: string
}

export interface UploadSession {
  id: string
  courseId: string
  coursewareId?: string
  uploadType: 'COVER' | 'VIDEO'
  originalFilename: string
  fileSizeBytes: number
  clientLastModified?: number
  partSizeBytes: number
  partCount: number
  status: string
  expiresAt: string
  uploadRequest?: SignedRequest
}

export interface UploadedPart {
  partNumber: number
  sizeBytes: number
  etag: string
  lastModified?: string
}

export interface UploadComplete {
  sessionId: string
  courseId: string
  resourceId: string
  storageObjectId: string
  uploadType: 'COVER' | 'VIDEO'
  status: string
}

export interface FileDeclaration {
  originalFilename: string
  contentType: string
  fileSizeBytes: number
  clientLastModified: number
}

/** 分页查询当前组织课程。 */
export function getCourses(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: CourseStatus | ''
}) {
  return http.get<PageResult<Course>>('/training/courses', { params })
}

/** 创建草稿课程。 */
export function createCourse(data: CoursePayload) {
  return http.post<Course>('/training/courses', data)
}

/** 获取课程详情及有效课件。 */
export function getCourse(id: string) {
  return http.get<Course>(`/training/courses/${id}`)
}

/** 编辑草稿或已禁用课程规则。 */
export function updateCourse(id: string, data: CoursePayload) {
  return http.put<Course>(`/training/courses/${id}`, data)
}

/** 删除从未启用的草稿课程。 */
export function deleteCourse(id: string) {
  return http.delete<void>(`/training/courses/${id}`)
}

/** 启用或禁用课程。 */
export function changeCourseStatus(id: string, status: 'ENABLED' | 'DISABLED') {
  return http.patch<Course>(`/training/courses/${id}/status`, { status })
}

/** 编辑课件标题。 */
export function updateCourseware(courseId: string, id: string, title: string) {
  return http.put<Courseware>(`/training/courses/${courseId}/coursewares/${id}`, { title })
}

/** 删除有效课件。 */
export function deleteCourseware(courseId: string, id: string) {
  return http.delete<void>(`/training/courses/${courseId}/coursewares/${id}`)
}

/** 保存全部有效课件的显示顺序。 */
export function reorderCoursewares(courseId: string, coursewareIds: string[]) {
  return http.put<void>(`/training/courses/${courseId}/coursewares/order`, { coursewareIds })
}

/** 删除课程封面。 */
export function deleteCourseCover(courseId: string) {
  return http.delete<void>(`/training/courses/${courseId}/cover`)
}

/** 查询当前OSS上传能力及限制。 */
export function getStorageCapability() {
  return http.get<StorageCapability>('/training/storage/capability')
}

/** 创建封面单次直传会话。 */
export function createCoverUploadSession(courseId: string, data: FileDeclaration) {
  return http.post<UploadSession>(`/training/courses/${courseId}/cover/upload-sessions`, data)
}

/** 创建视频分片直传会话。 */
export function createCoursewareUploadSession(
  courseId: string,
  data: FileDeclaration & { title: string; durationSeconds: number },
) {
  return http.post<UploadSession>(`/training/courses/${courseId}/coursewares/upload-sessions`, data)
}

/** 批量获取指定分片的短期签名。 */
export function createPartUrls(sessionId: string, partNumbers: number[]) {
  return http.post<SignedRequest[]>(`/training/upload-sessions/${sessionId}/part-urls`, {
    partNumbers,
  })
}

/** 查询OSS中已存在的分片以支持断点续传。 */
export function getUploadedParts(sessionId: string) {
  return http.get<UploadedPart[]>(`/training/upload-sessions/${sessionId}/parts`)
}

/** 完成上传并绑定课程资源，接口可安全重试。 */
export function completeUploadSession(sessionId: string) {
  return http.post<UploadComplete>(`/training/upload-sessions/${sessionId}/complete`)
}

/** 主动取消上传会话。 */
export function cancelUploadSession(sessionId: string) {
  return http.delete<void>(`/training/upload-sessions/${sessionId}`)
}

/** 获取课程封面的短期预览地址。 */
export function getCoverPreviewUrl(courseId: string) {
  return http.get<SignedRequest>(`/training/courses/${courseId}/cover/preview-url`)
}

/** 获取视频课件的短期预览地址。 */
export function getCoursewarePreviewUrl(courseId: string, coursewareId: string) {
  return http.get<SignedRequest>(
    `/training/courses/${courseId}/coursewares/${coursewareId}/preview-url`,
  )
}

/** 分页查询当前组织培训计划。 */
export function getPlans(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: PlanStatus | ''
}) {
  return http.get<PageResult<Plan>>('/training/plans', { params })
}

/** 创建培训计划草稿。 */
export function createPlan(data: PlanPayload) {
  return http.post<Plan>('/training/plans', data)
}

/** 获取培训计划及当前快照详情。 */
export function getPlan(id: string) {
  return http.get<Plan>(`/training/plans/${id}`)
}

/** 编辑草稿计划并重建课程与学员快照。 */
export function updatePlan(id: string, data: PlanPayload) {
  return http.put<Plan>(`/training/plans/${id}`, data)
}

/** 删除培训计划草稿。 */
export function deletePlan(id: string) {
  return http.delete<void>(`/training/plans/${id}`)
}

/** 发布培训计划并冻结最终规则快照。 */
export function publishPlan(id: string) {
  return http.post<Plan>(`/training/plans/${id}/publish`)
}

/** 取消尚未开始的已发布计划。 */
export function cancelPlan(id: string) {
  return http.post<Plan>(`/training/plans/${id}/cancel`)
}

/** 查询培训计划可选择的已启用课程。 */
export function getPlanCourseCandidates(keyword?: string) {
  return http.get<PlanCourseOption[]>('/training/plans/course-candidates', {
    params: { keyword },
  })
}

/** 查询当前组织可参训的已启用学员。 */
export function getPlanParticipantCandidates(keyword?: string, orgId?: string) {
  return http.get<PlanParticipantOption[]>('/training/plans/participant-candidates', {
    params: { keyword, orgId },
  })
}

/** 分页查询当前登录学员的培训任务。 */
export function getStudentPlans(params: {
  pageNumber: number
  pageSize: number
  status?: Exclude<PlanStatus, 'DRAFT'> | ''
}) {
  return http.get<PageResult<StudentPlan>>('/training/student/plans', { params })
}

/** 获取当前登录学员被分配的计划详情。 */
export function getStudentPlan(id: string) {
  return http.get<StudentPlan>(`/training/student/plans/${id}`)
}
