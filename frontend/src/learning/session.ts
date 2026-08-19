import type { CoursewareProgress, LearningSession } from '@/api/learning'
import { ApiError } from '@/api/http'

const CLIENT_INSTANCE_KEY = 'road-training:learning-client-instance'

/** 获取当前浏览器持久化实例ID，不存在时安全生成。 */
export function getOrCreateClientInstanceId() {
  const existing = localStorage.getItem(CLIENT_INSTANCE_KEY)
  if (existing) return existing
  const value = createRequestId()
  localStorage.setItem(CLIENT_INSTANCE_KEY, value)
  return value
}

/** 为学习事件生成全局唯一请求ID。 */
export function createRequestId() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 判断严格顺序下指定课件是否已经解锁。 */
export function isCoursewareUnlocked(
  coursewares: CoursewareProgress[],
  target: CoursewareProgress,
) {
  return coursewares
    .filter((value) => value.sortOrder < target.sortOrder)
    .every((value) => value.status === 'COMPLETED')
}

/** 合并事件响应状态，已完成课件在回看过程中不得降级。 */
export function resolveCoursewareStatusAfterEvent(
  currentStatus: CoursewareProgress['status'],
  coursewareCompleted: boolean,
  sessionStatus: LearningSession['status'],
): CoursewareProgress['status'] {
  if (currentStatus === 'COMPLETED' || coursewareCompleted) return 'COMPLETED'
  return sessionStatus === 'STUDYING' ? 'IN_PROGRESS' : currentStatus
}

/** 判断学习事件失败是否属于可使用原请求ID重试的网络或服务端错误。 */
export function shouldRetryLearningEvent(error: unknown) {
  return error instanceof ApiError && (error.status === undefined || error.status >= 500)
}
