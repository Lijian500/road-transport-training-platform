import { http, type PageResult } from '@/api/http'

export type RecordAudience = 'student' | 'admin'

export interface TrainingRecord {
  taskId: string
  planId: string
  userId: string
  displayName: string
  planName: string
  planStatus: string
  startAt: string
  endAt: string
  studyStatus: string
  examStatus: string
  completionStatus: string
  examScore?: number
  examPassed?: boolean
  completedAt?: string
  requiredDurationMillis: number
}

export interface RecordItem {
  training: TrainingRecord
  effectiveDurationMillis: number
}

export interface CourseRecord {
  planCourseId: string
  courseName: string
  requiredDurationMillis: number
  effectiveDurationMillis: number
  status: string
  completedAt?: string
}

export interface RecordDetail {
  training: TrainingRecord
  courses: CourseRecord[]
}

export interface StudentOverview {
  totalCount: number
  toStudyCount: number
  toExamCount: number
  completedCount: number
  recentTasks: TrainingRecord[]
}

export interface RecordQuery {
  pageNumber: number
  pageSize: number
  keyword?: string
  completionStatus?: string
  fromDate?: string
  toDate?: string
  activity?: string
}

export interface SessionRecord {
  id: string
  taskId: string
  planId: string
  planCourseId: string
  courseName: string
  status: string
  createdAt: string
  signedInAt?: string
  startedAt?: string
  signedOutAt?: string
  terminatedAt?: string
  terminationReason?: string
  signInPhotoUrl?: string
  signOutPhotoUrl?: string
}

export interface EventRecord {
  id: string
  requestId: string
  sequence: number
  eventType: string
  fromStatus: string
  toStatus: string
  reportedPositionMillis: number
  confirmedPositionMillis: number
  creditedDurationMillis: number
  resultCode: string
  serverTime: string
}

export interface FaceRecord {
  id: string
  status: string
  triggeredAt: string
  deadlineAt: string
  completedAt?: string
  failureReason?: string
  attemptCount: number
  attempts: Array<{
    photoUrl?: string
    attemptNo: number
    result: string
    failureReason?: string
    similarity?: number
    elapsedMillis: number
    createdAt: string
  }>
}

/** 查询本人培训档案，包含已结束计划。 */
export function getMyRecords(params: RecordQuery) {
  return http.get<PageResult<RecordItem>>('/training/student/records', { params })
}

/** 查询当前学员首页，不启动学习或考试。 */
export function getStudentOverview() {
  return http.get<StudentOverview>('/training/student/overview')
}

/** 获取经过本人或企业范围校验的档案详情。 */
export function getRecordDetail(taskId: string, audience: RecordAudience) {
  const path =
    audience === 'student'
      ? `/training/student/records/${taskId}`
      : `/training/statistics/participants/${taskId}/record`
  return http.get<RecordDetail>(path)
}

/** 按任务分页查询学习会话。 */
export function getRecordSessions(
  audience: RecordAudience,
  params: {
    taskId: string
    pageNumber: number
    pageSize: number
    status?: string
    fromTime?: string
    toTime?: string
  },
) {
  return http.get<PageResult<SessionRecord>>(`/learning/records/${audience}/sessions`, { params })
}

/** 分页查询已受理学习事件。 */
export function getRecordEvents(
  audience: RecordAudience,
  id: string,
  pageNumber: number,
  pageSize = 10,
) {
  return http.get<PageResult<EventRecord>>(`/learning/records/${audience}/sessions/${id}/events`, {
    params: { pageNumber, pageSize },
  })
}

/** 分页查询抽验任务及提交明细，包含零次提交的超时任务。 */
export function getRecordFaceChecks(
  audience: RecordAudience,
  id: string,
  pageNumber: number,
  pageSize = 10,
) {
  return http.get<PageResult<FaceRecord>>(
    `/learning/records/${audience}/sessions/${id}/face-checks`,
    {
      params: { pageNumber, pageSize },
    },
  )
}
