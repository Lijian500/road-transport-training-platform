import { http } from '@/api/http'
import type { SignedRequest } from '@/api/training'

export type LearningSessionStatus =
  | 'CREATED'
  | 'SIGNED_IN'
  | 'STUDYING'
  | 'PAUSED'
  | 'FACE_PENDING'
  | 'COMPLETED'
  | 'SIGNED_OUT'
  | 'TERMINATED'

export type LearningEventType = 'SIGN_IN' | 'PLAY' | 'PROGRESS' | 'PAUSE' | 'SIGN_OUT'

export interface CoursewareProgress {
  coursewareSnapshotId: string
  title: string
  sortOrder: number
  durationMillis: number
  confirmedPositionMillis: number
  maxConfirmedPositionMillis: number
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
  completedAt?: string
}

export interface CourseProgress {
  planCourseId: string
  courseName: string
  sortOrder: number
  requiredDurationMillis: number
  effectiveDurationMillis: number
  allowSeek: boolean
  progressReportIntervalSeconds: number
  studyToleranceSeconds: number
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
  coursewares: CoursewareProgress[]
  completedAt?: string
}

export interface PlanLearningProgress {
  taskId: string
  planId: string
  taskStudyStatus: string
  taskCompletionStatus: string
  synchronizationPending: boolean
  courses: CourseProgress[]
}

export interface LearningSession {
  id: string
  taskId: string
  planId: string
  planCourseId: string
  courseName: string
  status: LearningSessionStatus
  currentCoursewareSnapshotId?: string
  lastSequence: number
  confirmedPositionMillis: number
  effectiveDurationMillis: number
  requiredDurationMillis: number
  currentFaceCheck?: FaceCheckTask | null
  lastEventAt?: string
  createdAt: string
}

export type FaceCheckStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'TIMED_OUT'

export interface FaceCheckTask {
  taskId: string
  sessionId: string
  status: FaceCheckStatus
  triggeredAt: string
  deadlineAt: string
  attemptCount: number
  maxAttempts: number
  remainingAttempts: number
  result?: string | null
  failureReason?: string | null
  similarity?: number | null
  completedAt?: string | null
}

export interface LearningEventResult {
  sessionId: string
  requestId: string
  acceptedSequence: number
  status: LearningSessionStatus
  currentCoursewareSnapshotId?: string
  confirmedPositionMillis: number
  creditedDurationMillis: number
  effectiveDurationMillis: number
  requiredDurationMillis: number
  coursewareCompleted: boolean
  courseCompleted: boolean
  serverTime: string
}

export interface LearningEventPayload {
  clientInstanceId: string
  requestId: string
  sequence: number
  eventType: LearningEventType
  coursewareSnapshotId?: string
  videoPositionMillis: number
}

/** 查询当前学员在培训计划中的全部课程学习进度。 */
export function getPlanLearningProgress(planId: string) {
  return http.get<PlanLearningProgress>(`/learning/plans/${planId}/progress`)
}

/** 查询当前学员的一门计划课程及逐课件进度。 */
export function getLearningCourse(planId: string, planCourseId: string) {
  return http.get<CourseProgress>(`/learning/plans/${planId}/courses/${planCourseId}`)
}

/** 创建新学习会话或恢复同课程同浏览器会话。 */
export function openLearningSession(data: {
  planId: string
  planCourseId: string
  clientInstanceId: string
}) {
  return http.post<LearningSession>('/learning/sessions', data)
}

/** 查询当前账号唯一的活动学习会话。 */
export function getActiveLearningSession() {
  return http.get<LearningSession | null>('/learning/sessions/active')
}

/** 查询指定学习会话的服务端确认状态。 */
export function getLearningSession(id: string) {
  return http.get<LearningSession>(`/learning/sessions/${id}`)
}

/** 查询学习会话当前待处理的人脸抽验任务。 */
export function getCurrentFaceCheck(sessionId: string) {
  return http.get<FaceCheckTask | null>(`/learning/sessions/${sessionId}/face-check`)
}

/** 以multipart提交一次抽验照片，请求ID用于服务端幂等。 */
export function submitFaceCheck(taskId: string, photo: Blob, requestId: string) {
  const data = new FormData()
  data.append('requestId', requestId)
  data.append('photo', photo, 'face-check.jpg')
  return http.post<FaceCheckTask>(`/learning/face-checks/${taskId}/submissions`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 按严格序号向服务端提交一个学习状态事件。 */
export function submitLearningEvent(sessionId: string, data: LearningEventPayload) {
  return http.post<LearningEventResult>(`/learning/sessions/${sessionId}/events`, data)
}

/** 显式终止当前账号遗留的活动会话。 */
export function terminateLearningSession(sessionId: string) {
  return http.post<void>(`/learning/sessions/${sessionId}/terminate`)
}

/** 获取活动会话内指定课件的短期私有OSS播放地址。 */
export function getLearningPlaybackUrl(
  sessionId: string,
  coursewareSnapshotId: string,
  clientInstanceId: string,
) {
  return http.get<SignedRequest>(
    `/learning/sessions/${sessionId}/coursewares/${coursewareSnapshotId}/play-url`,
    { params: { clientInstanceId } },
  )
}
