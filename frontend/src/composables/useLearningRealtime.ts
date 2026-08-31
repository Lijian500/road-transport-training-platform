import { computed, onBeforeUnmount, shallowRef } from 'vue'

import {
  getLearningSession,
  type LearningEventResult,
  type LearningEventType,
  type LearningSession,
} from '@/api/learning'
import { refreshAccessSession } from '@/api/http'
import { createRequestId } from '@/learning/session'
import { RealtimeClient, type RealtimeConnectionState } from '@/realtime/client'
import {
  realtimeSentAt,
  type BindSessionMessage,
  type LearningEventMessage,
  type RealtimeServerMessage,
  type SyncStateMessage,
} from '@/realtime/protocol'

const HEARTBEAT_INTERVAL_MILLIS = 20_000
const ACK_TIMEOUT_MILLIS = 10_000

interface LearningRealtimeOptions {
  clientInstanceId: string
  onStateSync?: (session: LearningSession) => void
  onProgressConfirmed?: (result: LearningEventResult) => void
  onDisconnected?: () => void
  onReplaced?: () => void
}

interface StateRequest {
  timer: ReturnType<typeof setTimeout>
  resolve: (session: LearningSession) => void
  reject: (error: LearningRealtimeError) => void
}

interface PendingEvent {
  message: LearningEventMessage
  synchronizationBarrier: boolean
  acknowledged: boolean
  timer?: ReturnType<typeof setTimeout>
  resolve: (result: LearningEventResult) => void
  reject: (error: LearningRealtimeError) => void
}

/** 实时学习业务错误，保留服务端是否可重试及是否需同步的语义。 */
export class LearningRealtimeError extends Error {
  constructor(
    message: string,
    readonly code = 'S9999',
    readonly retryable = false,
    readonly resyncRequired = false,
  ) {
    super(message)
    this.name = 'LearningRealtimeError'
  }
}

/** 管理学习会话绑定、状态同步、唯一待确认事件和断线恢复。 */
export function useLearningRealtime(options: LearningRealtimeOptions) {
  const connectionState = shallowRef<RealtimeConnectionState>('idle')
  const synchronized = shallowRef(false)
  const latestSession = shallowRef<LearningSession>()
  const ready = computed(() => connectionState.value === 'connected' && synchronized.value)

  const stateRequests = new Map<string, StateRequest>()
  const bindWaiters: Array<{
    resolve: (session: LearningSession) => void
    reject: (error: LearningRealtimeError) => void
  }> = []
  let studySessionId: string | undefined
  let pendingEvent: PendingEvent | undefined
  let heartbeatTimer: ReturnType<typeof setInterval> | undefined
  let establishing = false
  let closing = false
  let replacementNotified = false

  const client = new RealtimeClient({
    url: resolveRealtimeUrl(),
    onMessage: handleMessage,
    onOpen: handleOpen,
    onClose: handleClose,
    onStateChange: (state) => {
      connectionState.value = state
    },
    beforeReconnect: prepareReconnect,
  })

  /** 绑定REST已创建的会话，并等待绑定、同步及必要的安全暂停完成。 */
  function bind(sessionId: string) {
    if (studySessionId && studySessionId !== sessionId) {
      throw new LearningRealtimeError('当前实时连接已绑定其他学习会话', 'L3008')
    }
    studySessionId = sessionId
    const promise = new Promise<LearningSession>((resolve, reject) => {
      bindWaiters.push({ resolve, reject })
    })
    if (ready.value && latestSession.value) {
      resolveBindWaiters(latestSession.value)
    } else if (connectionState.value === 'connected') {
      void establish(false)
    } else {
      client.connect()
    }
    return promise
  }

  /** 发送一个学习事件；同一时刻仅允许存在一个未确认事件。 */
  function sendEvent(
    eventType: LearningEventType,
    sequence: number,
    coursewareSnapshotId?: string,
    videoPositionMillis = 0,
  ) {
    if (!ready.value || !studySessionId) {
      return Promise.reject(
        new LearningRealtimeError('实时学习连接尚未完成同步', 'REALTIME_NOT_READY', true),
      )
    }
    if (pendingEvent) {
      return Promise.reject(
        new LearningRealtimeError('上一学习事件仍在等待确认', 'REALTIME_EVENT_PENDING', true),
      )
    }
    return queueEvent(
      createLearningMessage(eventType, sequence, coursewareSnapshotId, videoPositionMillis),
      false,
    )
  }

  /** 主动关闭当前页面的实时连接。 */
  function close() {
    closing = true
    stopHeartbeat()
    const error = new LearningRealtimeError('学习页面已关闭', 'REALTIME_CLOSED')
    rejectStateRequests(error)
    rejectPendingEvent(error)
    rejectBindWaiters(error)
    client.close()
  }

  /** 连接建立后依次执行绑定和显式状态同步。 */
  function handleOpen(reconnecting: boolean) {
    startHeartbeat()
    void establish(reconnecting)
  }

  /** 完成BIND_SESSION、SYNC_STATE和断线待确认事件恢复。 */
  async function establish(reconnecting: boolean) {
    if (!studySessionId || establishing) return
    establishing = true
    synchronized.value = false
    try {
      await requestState('BIND_SESSION', reconnecting)
      const session = await requestState('SYNC_STATE', reconnecting)
      recoverPendingAfterSync(session)
      if (pendingEvent) return
      if (session.status === 'STUDYING') {
        const safety = createLearningMessage(
          'PAUSE',
          session.lastSequence + 1,
          session.currentCoursewareSnapshotId,
          session.confirmedPositionMillis,
        )
        void queueEvent(safety, true).catch(() => undefined)
        return
      }
      finishSynchronization(session)
    } catch (error) {
      const realtimeError = toRealtimeError(error)
      if (await resolveConfirmedSignOut(realtimeError)) return
      if (realtimeError.retryable && connectionState.value === 'connected') {
        client.reconnect()
      } else if (!realtimeError.retryable) {
        rejectPendingEvent(realtimeError)
        rejectBindWaiters(realtimeError)
        client.close()
      }
    } finally {
      establishing = false
    }
  }

  /** 发送绑定或同步请求，并按requestId等待STATE_SYNC。 */
  function requestState(type: 'BIND_SESSION' | 'SYNC_STATE', reconnecting: boolean) {
    if (!studySessionId) {
      return Promise.reject(new LearningRealtimeError('学习会话尚未指定', 'L3002'))
    }
    const requestId = createRequestId()
    const message: BindSessionMessage | SyncStateMessage =
      type === 'BIND_SESSION'
        ? {
            type,
            requestId,
            studySessionId,
            sentAt: realtimeSentAt(),
            payload: { clientInstanceId: options.clientInstanceId, reconnecting },
          }
        : {
            type,
            requestId,
            studySessionId,
            sentAt: realtimeSentAt(),
            payload: {},
          }
    return new Promise<LearningSession>((resolve, reject) => {
      const timer = setTimeout(() => {
        stateRequests.delete(requestId)
        reject(new LearningRealtimeError('学习状态同步超时', 'REALTIME_ACK_TIMEOUT', true))
      }, ACK_TIMEOUT_MILLIS)
      stateRequests.set(requestId, { timer, resolve, reject })
      if (!client.send(message)) {
        clearTimeout(timer)
        stateRequests.delete(requestId)
        reject(new LearningRealtimeError('实时连接已断开', 'REALTIME_DISCONNECTED', true))
      }
    })
  }

  /** 根据服务端消息类型更新同步请求、待确认事件和连接状态。 */
  function handleMessage(message: RealtimeServerMessage) {
    if (message.type === 'STATE_SYNC') {
      latestSession.value = message.payload
      options.onStateSync?.(message.payload)
      const request = stateRequests.get(message.requestId)
      if (request) {
        clearTimeout(request.timer)
        stateRequests.delete(message.requestId)
        request.resolve(message.payload)
      }
      return
    }
    if (message.type === 'PROGRESS_CONFIRMED') {
      confirmPendingEvent(message.requestId, message.payload)
      return
    }
    if (message.type === 'ACK') {
      acknowledgePendingEvent(message.requestId, message.payload.acceptedSequence)
      return
    }
    if (message.type === 'ERROR') {
      handleServerError(message)
      return
    }
    if (message.type === 'SESSION_REPLACED') handleReplacementMessage()
  }

  /** 收到ACK后重新启动最终确认超时，避免旧ACK计时器误触发。 */
  function acknowledgePendingEvent(requestId: string, acceptedSequence: number) {
    const current = pendingEvent
    if (
      current?.message.requestId !== requestId ||
      current.message.seq !== acceptedSequence ||
      current.acknowledged
    ) {
      return
    }
    current.acknowledged = true
    restartPendingTimeout(current)
  }

  /** 使用服务端确认结果完成事件Promise并刷新本地会话。 */
  function confirmPendingEvent(requestId: string, result: LearningEventResult) {
    const current = pendingEvent
    if (
      current?.message.requestId !== requestId ||
      result.requestId !== requestId ||
      current.message.seq !== result.acceptedSequence ||
      result.sessionId !== studySessionId
    ) {
      return
    }
    clearTimeout(current.timer)
    pendingEvent = undefined
    if (latestSession.value) {
      latestSession.value = eventResultToSession(latestSession.value, result)
    }
    if (current.synchronizationBarrier && latestSession.value) {
      finishSynchronization(latestSession.value)
    }
    current.resolve(result)
    options.onProgressConfirmed?.(result)
  }

  /** 将ERROR分发给同步请求或唯一待确认学习事件。 */
  function handleServerError(message: Extract<RealtimeServerMessage, { type: 'ERROR' }>) {
    const error = new LearningRealtimeError(
      message.payload.message,
      message.payload.code,
      message.payload.retryable,
      message.payload.resyncRequired,
    )
    const stateRequest = stateRequests.get(message.requestId)
    if (stateRequest) {
      clearTimeout(stateRequest.timer)
      stateRequests.delete(message.requestId)
      stateRequest.reject(error)
      return
    }
    if (pendingEvent?.message.requestId !== message.requestId) return
    if (error.resyncRequired) {
      void recoverSequence(error)
    } else if (error.retryable) {
      clearTimeout(pendingEvent.timer)
      client.reconnect()
    } else {
      rejectPendingEvent(error)
    }
  }

  /** L3005后重新同步，并只为仍必要的暂停或签退创建最新序号事件。 */
  async function recoverSequence(error: LearningRealtimeError) {
    const current = pendingEvent
    if (!current) return
    clearTimeout(current.timer)
    try {
      const session = await requestState('SYNC_STATE', true)
      if (pendingEvent !== current) return
      if (current.message.type === 'PROGRESS' || current.message.type === 'PLAY') {
        rejectPendingEvent(error)
        return
      }
      if (current.message.type === 'SIGN_IN') {
        if (session.status !== 'CREATED') resolvePendingFromSession(current, session)
        else rejectPendingEvent(error)
        return
      }
      if (current.message.type === 'PAUSE' && session.status !== 'STUDYING') {
        resolvePendingFromSession(current, session)
        return
      }
      current.message = createLearningMessage(
        current.message.type,
        session.lastSequence + 1,
        current.message.payload.coursewareSnapshotId,
        current.message.payload.videoPositionMillis,
      )
      sendPendingEvent(current)
    } catch (syncError) {
      const realtimeError = toRealtimeError(syncError)
      if (realtimeError.retryable) {
        client.reconnect()
      } else {
        synchronized.value = false
        rejectPendingEvent(realtimeError)
        rejectBindWaiters(realtimeError)
        closing = true
        client.close()
      }
    }
  }

  /** 重连同步后按事件语义决定丢弃、保留或重发。 */
  function recoverPendingAfterSync(session: LearningSession) {
    const current = pendingEvent
    if (!current) return
    clearTimeout(current.timer)
    if (current.message.type === 'PROGRESS' || current.message.type === 'PLAY') {
      rejectPendingEvent(
        new LearningRealtimeError('断线后已丢弃过期学习操作', 'REALTIME_EVENT_STALE'),
      )
      return
    }
    if (current.message.type === 'SIGN_IN') {
      if (session.status !== 'CREATED') resolvePendingFromSession(current, session)
      else {
        rejectPendingEvent(
          new LearningRealtimeError('断线后签到未确认，请重新操作', 'REALTIME_EVENT_STALE'),
        )
      }
      return
    }
    // PAUSE可能已经成功落库但确认响应丢失，必须先重放原请求以取回完整幂等结果。
    // 若状态由服务端超时任务转为PAUSED，重放会返回L3005，再按最新状态安全收敛。
    current.synchronizationBarrier = true
    sendPendingEvent(current)
  }

  /** 创建并登记唯一待确认学习事件。 */
  function queueEvent(message: LearningEventMessage, synchronizationBarrier: boolean) {
    return new Promise<LearningEventResult>((resolve, reject) => {
      const pending: PendingEvent = {
        message,
        synchronizationBarrier,
        acknowledged: false,
        resolve,
        reject,
      }
      pendingEvent = pending
      sendPendingEvent(pending)
    })
  }

  /** 发送或重发同一requestId和seq，并启动确认超时。 */
  function sendPendingEvent(pending: PendingEvent) {
    if (pendingEvent !== pending) return
    clearTimeout(pending.timer)
    pending.acknowledged = false
    if (!client.send(pending.message)) {
      client.reconnect()
      return
    }
    restartPendingTimeout(pending)
  }

  /** 重置ACK或最终确认等待时间，超时后通过重连恢复同一事件。 */
  function restartPendingTimeout(pending: PendingEvent) {
    clearTimeout(pending.timer)
    pending.timer = setTimeout(() => {
      if (pendingEvent !== pending) return
      client.reconnect()
    }, ACK_TIMEOUT_MILLIS)
  }

  /** 使用最新同步状态完成已达到目标的PAUSE事件。 */
  function resolvePendingFromSession(pending: PendingEvent, session: LearningSession) {
    clearTimeout(pending.timer)
    pendingEvent = undefined
    const result = sessionToEventResult(pending.message.requestId, session)
    pending.resolve(result)
    options.onProgressConfirmed?.(result)
    if (pending.synchronizationBarrier) finishSynchronization(session)
  }

  /** 拒绝并清理当前唯一待确认事件。 */
  function rejectPendingEvent(error: LearningRealtimeError) {
    const current = pendingEvent
    if (!current) return
    clearTimeout(current.timer)
    pendingEvent = undefined
    current.reject(error)
    if (current.synchronizationBarrier) rejectBindWaiters(error)
  }

  /** 连接关闭时停止心跳、暂停页面并保留可安全重放的待确认事件。 */
  function handleClose(event: CloseEvent) {
    stopHeartbeat()
    synchronized.value = false
    rejectStateRequests(new LearningRealtimeError('实时连接已断开', 'REALTIME_DISCONNECTED', true))
    clearTimeout(pendingEvent?.timer)
    if (event.code === 4409) {
      const error = new LearningRealtimeError('学习连接已被其他页面接管', 'REALTIME_REPLACED')
      rejectPendingEvent(error)
      rejectBindWaiters(error)
      notifyReplacement()
    }
    if (!closing) options.onDisconnected?.()
  }

  /** 4401关闭前先通过HTTP刷新会话，其他关闭直接进入重连退避。 */
  async function prepareReconnect(event: CloseEvent) {
    if (event.code === 4403) {
      const error = new LearningRealtimeError('学习权限已失效', 'A0006')
      rejectPendingEvent(error)
      rejectBindWaiters(error)
      return false
    }
    if (event.code !== 4401) return true
    try {
      await refreshAccessSession()
      return true
    } catch {
      window.dispatchEvent(new CustomEvent('auth:expired'))
      const error = new LearningRealtimeError('登录状态已过期', 'A0003')
      rejectPendingEvent(error)
      rejectBindWaiters(error)
      return false
    }
  }

  /** 签退响应丢失且终态拒绝重绑时，通过REST只读查询确认签退已完成。 */
  async function resolveConfirmedSignOut(error: LearningRealtimeError) {
    const current = pendingEvent
    if (error.code !== 'L3008' || current?.message.type !== 'SIGN_OUT' || !studySessionId) {
      return false
    }
    try {
      const state = await getLearningSession(studySessionId)
      if (state.status !== 'SIGNED_OUT' || pendingEvent !== current) return false
      latestSession.value = state
      options.onStateSync?.(state)
      resolvePendingFromSession(current, state)
      closing = true
      client.close()
      return true
    } catch {
      return false
    }
  }

  /** 启动连接级心跳，心跳不参与学习计时。 */
  function startHeartbeat() {
    stopHeartbeat()
    sendHeartbeat()
    heartbeatTimer = setInterval(sendHeartbeat, HEARTBEAT_INTERVAL_MILLIS)
  }

  /** 发送不需要业务确认的HEARTBEAT消息。 */
  function sendHeartbeat() {
    client.send({
      type: 'HEARTBEAT',
      requestId: createRequestId(),
      studySessionId,
      sentAt: realtimeSentAt(),
      payload: {},
    })
  }

  /** 停止当前连接的心跳定时器。 */
  function stopHeartbeat() {
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = undefined
  }

  /** 标记同步完成并释放所有等待绑定的调用方。 */
  function finishSynchronization(session: LearningSession) {
    latestSession.value = session
    synchronized.value = true
    resolveBindWaiters(session)
  }

  /** 完成所有等待实时会话就绪的Promise。 */
  function resolveBindWaiters(session: LearningSession) {
    bindWaiters.splice(0).forEach((waiter) => waiter.resolve(session))
  }

  /** 拒绝所有等待实时会话就绪的Promise。 */
  function rejectBindWaiters(error: LearningRealtimeError) {
    bindWaiters.splice(0).forEach((waiter) => waiter.reject(error))
  }

  /** 拒绝本轮尚未完成的绑定或同步请求。 */
  function rejectStateRequests(error: LearningRealtimeError) {
    stateRequests.forEach((request) => {
      clearTimeout(request.timer)
      request.reject(error)
    })
    stateRequests.clear()
  }

  /** 只通知一次连接被其他页面接管。 */
  function notifyReplacement() {
    if (replacementNotified) return
    replacementNotified = true
    options.onReplaced?.()
  }

  /** 收到接管通知后立即冻结业务操作并主动关闭旧连接。 */
  function handleReplacementMessage() {
    const error = new LearningRealtimeError('学习连接已被其他页面接管', 'REALTIME_REPLACED')
    synchronized.value = false
    stopHeartbeat()
    rejectPendingEvent(error)
    rejectBindWaiters(error)
    notifyReplacement()
    closing = true
    client.close(4409, '学习连接已被其他页面接管')
  }

  /** 将事件参数封装为可原样重发的协议消息。 */
  function createLearningMessage(
    type: LearningEventType,
    sequence: number,
    coursewareSnapshotId?: string,
    videoPositionMillis = 0,
  ): LearningEventMessage {
    if (!studySessionId) throw new LearningRealtimeError('学习会话尚未指定', 'L3002')
    return {
      type,
      requestId: createRequestId(),
      studySessionId,
      seq: sequence,
      sentAt: realtimeSentAt(),
      payload: {
        coursewareSnapshotId,
        videoPositionMillis: Math.max(0, Math.floor(videoPositionMillis)),
      },
    }
  }

  onBeforeUnmount(close)

  return {
    connectionState,
    ready,
    latestSession,
    bind,
    sendEvent,
    close,
  }
}

/** 将同源HTTP地址转换为ws或wss学习端点。 */
function resolveRealtimeUrl() {
  const url = new URL(import.meta.env.VITE_WS_PATH || '/ws/learning', window.location.href)
  if (url.protocol === 'https:') url.protocol = 'wss:'
  else if (url.protocol === 'http:') url.protocol = 'ws:'
  return url.toString()
}

/** 将服务端事件确认合并进最近一次会话快照。 */
function eventResultToSession(session: LearningSession, result: LearningEventResult) {
  return {
    ...session,
    status: result.status,
    lastSequence: result.acceptedSequence,
    currentCoursewareSnapshotId: result.currentCoursewareSnapshotId,
    confirmedPositionMillis: result.confirmedPositionMillis,
    effectiveDurationMillis: result.effectiveDurationMillis,
    requiredDurationMillis: result.requiredDurationMillis,
    lastEventAt: result.serverTime,
  }
}

/** 根据STATE_SYNC构造不计新增学时的本地确认结果。 */
function sessionToEventResult(requestId: string, session: LearningSession): LearningEventResult {
  return {
    sessionId: session.id,
    requestId,
    acceptedSequence: session.lastSequence,
    status: session.status,
    currentCoursewareSnapshotId: session.currentCoursewareSnapshotId,
    confirmedPositionMillis: session.confirmedPositionMillis,
    creditedDurationMillis: 0,
    effectiveDurationMillis: session.effectiveDurationMillis,
    requiredDurationMillis: session.requiredDurationMillis,
    coursewareCompleted: false,
    courseCompleted: session.status === 'COMPLETED',
    serverTime: session.lastEventAt || new Date().toISOString(),
  }
}

/** 将未知异常统一转换为实时学习错误。 */
function toRealtimeError(error: unknown) {
  return error instanceof LearningRealtimeError
    ? error
    : new LearningRealtimeError('实时学习连接异常', 'S9999', true)
}
