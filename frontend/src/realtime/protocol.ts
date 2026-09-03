import type {
  FaceCheckTask,
  LearningEventResult,
  LearningEventType,
  LearningSession,
} from '@/api/learning'

interface ClientEnvelopeBase<TType extends string, TPayload> {
  type: TType
  requestId: string
  studySessionId?: string
  seq?: number
  sentAt: string
  payload: TPayload
}

export type BindSessionMessage = ClientEnvelopeBase<
  'BIND_SESSION',
  { clientInstanceId: string; reconnecting: boolean }
>
export type HeartbeatMessage = ClientEnvelopeBase<'HEARTBEAT', Record<string, never>>
export type SyncStateMessage = ClientEnvelopeBase<'SYNC_STATE', Record<string, never>>
export type LearningEventMessage = ClientEnvelopeBase<
  LearningEventType,
  { coursewareSnapshotId?: string; videoPositionMillis: number }
> & { studySessionId: string; seq: number }

export type RealtimeClientMessage =
  BindSessionMessage | HeartbeatMessage | SyncStateMessage | LearningEventMessage

interface ServerEnvelopeBase<TType extends string, TPayload> {
  type: TType
  requestId: string
  studySessionId?: string
  seq?: number
  sentAt: string
  payload: TPayload
}

export type RealtimeServerMessage =
  | ServerEnvelopeBase<'ACK', { acceptedSequence: number; status: LearningSession['status'] }>
  | ServerEnvelopeBase<'STATE_SYNC', LearningSession>
  | ServerEnvelopeBase<'PROGRESS_CONFIRMED', LearningEventResult>
  | ServerEnvelopeBase<'FACE_CHECK_REQUIRED', FaceCheckTask>
  | ServerEnvelopeBase<'FACE_CHECK_RESULT', FaceCheckTask>
  | ServerEnvelopeBase<'PONG', { serverTime: string }>
  | ServerEnvelopeBase<
      'ERROR',
      { code: string; message: string; retryable: boolean; resyncRequired: boolean }
    >
  | ServerEnvelopeBase<'SESSION_REPLACED', { message: string }>

const SERVER_MESSAGE_TYPES = new Set([
  'ACK',
  'STATE_SYNC',
  'PROGRESS_CONFIRMED',
  'FACE_CHECK_REQUIRED',
  'FACE_CHECK_RESULT',
  'PONG',
  'ERROR',
  'SESSION_REPLACED',
])
const LEARNING_SESSION_STATUSES = new Set([
  'CREATED',
  'SIGNED_IN',
  'STUDYING',
  'PAUSED',
  'FACE_PENDING',
  'COMPLETED',
  'SIGNED_OUT',
  'TERMINATED',
])

/** 将客户端实时消息编码为JSON文本。 */
export function encodeMessage(message: RealtimeClientMessage) {
  return JSON.stringify(message)
}

/** 解析并校验服务端实时消息的基础信封。 */
export function decodeMessage(data: string): RealtimeServerMessage {
  const value = JSON.parse(data) as unknown
  if (!isRealtimeServerMessage(value)) throw new Error('服务端实时消息格式不正确')
  return value
}

/** 创建统一的ISO时间戳。 */
export function realtimeSentAt() {
  return new Date().toISOString()
}

/** 判断未知值是否为可读取字段的普通对象。 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** 校验未知值是否为完整的服务端实时消息。 */
function isRealtimeServerMessage(value: unknown): value is RealtimeServerMessage {
  if (!isRecord(value)) return false
  return (
    typeof value.type === 'string' &&
    SERVER_MESSAGE_TYPES.has(value.type) &&
    typeof value.requestId === 'string' &&
    value.requestId.length > 0 &&
    isIsoTime(value.sentAt) &&
    (value.studySessionId === undefined || isNonEmptyString(value.studySessionId)) &&
    (value.seq === undefined || (Number.isInteger(value.seq) && Number(value.seq) >= 0)) &&
    isRecord(value.payload) &&
    hasValidServerPayload(value.type, value.payload)
  )
}

/** 按消息类型校验服务端载荷的关键字段。 */
function hasValidServerPayload(type: string, payload: Record<string, unknown>) {
  if (type === 'ACK') {
    return isPositiveInteger(payload.acceptedSequence) && isSessionStatus(payload.status)
  }
  if (type === 'STATE_SYNC') {
    return (
      isNonEmptyString(payload.id) &&
      isNonEmptyString(payload.taskId) &&
      isNonEmptyString(payload.planId) &&
      isNonEmptyString(payload.planCourseId) &&
      typeof payload.courseName === 'string' &&
      isSessionStatus(payload.status) &&
      isNonNegativeInteger(payload.lastSequence) &&
      isNonNegativeNumber(payload.confirmedPositionMillis) &&
      isNonNegativeNumber(payload.effectiveDurationMillis) &&
      isNonNegativeNumber(payload.requiredDurationMillis) &&
      isOptionalFaceCheck(payload.currentFaceCheck) &&
      isOptionalString(payload.currentCoursewareSnapshotId) &&
      isOptionalIsoTime(payload.lastEventAt) &&
      isIsoTime(payload.createdAt)
    )
  }
  if (type === 'PROGRESS_CONFIRMED') {
    return (
      isNonEmptyString(payload.sessionId) &&
      isNonEmptyString(payload.requestId) &&
      isPositiveInteger(payload.acceptedSequence) &&
      isSessionStatus(payload.status) &&
      isOptionalString(payload.currentCoursewareSnapshotId) &&
      isNonNegativeNumber(payload.confirmedPositionMillis) &&
      isNonNegativeNumber(payload.creditedDurationMillis) &&
      isNonNegativeNumber(payload.effectiveDurationMillis) &&
      isNonNegativeNumber(payload.requiredDurationMillis) &&
      typeof payload.coursewareCompleted === 'boolean' &&
      typeof payload.courseCompleted === 'boolean' &&
      isIsoTime(payload.serverTime)
    )
  }
  if (type === 'FACE_CHECK_REQUIRED') return isFaceCheckTask(payload)
  if (type === 'FACE_CHECK_RESULT') return isFaceCheckTask(payload)
  if (type === 'ERROR') {
    return (
      isNonEmptyString(payload.code) &&
      typeof payload.message === 'string' &&
      typeof payload.retryable === 'boolean' &&
      typeof payload.resyncRequired === 'boolean'
    )
  }
  if (type === 'PONG') return isIsoTime(payload.serverTime)
  return type === 'SESSION_REPLACED' && typeof payload.message === 'string'
}

/** 校验抽验任务载荷的必需字段和次数边界。 */
function isFaceCheckTask(value: Record<string, unknown>) {
  return (
    isNonEmptyString(value.taskId) &&
    isNonEmptyString(value.sessionId) &&
    isFaceCheckStatus(value.status) &&
    isIsoTime(value.triggeredAt) &&
    isIsoTime(value.deadlineAt) &&
    isNonNegativeInteger(value.attemptCount) &&
    isPositiveInteger(value.maxAttempts) &&
    isNonNegativeInteger(value.remainingAttempts) &&
    isNullableString(value.result) &&
    isNullableString(value.failureReason) &&
    isOptionalSimilarity(value.similarity) &&
    (value.completedAt === undefined || value.completedAt === null || isIsoTime(value.completedAt))
  )
}

/** 校验SFace余弦相似度原始值允许处于负一到一之间。 */
function isOptionalSimilarity(value: unknown) {
  return (
    value === undefined ||
    value === null ||
    (typeof value === 'number' && Number.isFinite(value) && value >= -1 && value <= 1)
  )
}

/** 判断值是否为缺省、空值或字符串。 */
function isNullableString(value: unknown) {
  return value === undefined || value === null || typeof value === 'string'
}

/** 校验STATE_SYNC中可空的当前抽验任务。 */
function isOptionalFaceCheck(value: unknown) {
  return value === undefined || value === null || (isRecord(value) && isFaceCheckTask(value))
}

/** 判断值是否为抽验任务状态。 */
function isFaceCheckStatus(value: unknown) {
  return typeof value === 'string' && ['PENDING', 'PASSED', 'FAILED', 'TIMED_OUT'].includes(value)
}

/** 判断值是否为非空字符串。 */
function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}

/** 判断值是否为可选字符串。 */
function isOptionalString(value: unknown) {
  return value === undefined || typeof value === 'string'
}

/** 判断值是否为ISO日期时间字符串。 */
function isIsoTime(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T/.test(value) &&
    !Number.isNaN(Date.parse(value))
  )
}

/** 判断值是否为可选ISO日期时间字符串。 */
function isOptionalIsoTime(value: unknown) {
  return value === undefined || value === null || isIsoTime(value)
}

/** 判断值是否为合法学习会话状态。 */
function isSessionStatus(value: unknown): value is LearningSession['status'] {
  return typeof value === 'string' && LEARNING_SESSION_STATUSES.has(value)
}

/** 判断值是否为非负有限数。 */
function isNonNegativeNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
}

/** 判断值是否为非负整数。 */
function isNonNegativeInteger(value: unknown): value is number {
  return isNonNegativeNumber(value) && Number.isInteger(value)
}

/** 判断值是否为正整数。 */
function isPositiveInteger(value: unknown): value is number {
  return isNonNegativeInteger(value) && value > 0
}
