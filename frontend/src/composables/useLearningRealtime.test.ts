import { defineComponent, h } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getLearningSession, type LearningEventResult, type LearningSession } from '@/api/learning'
import { refreshAccessSession } from '@/api/http'

import { useLearningRealtime } from './useLearningRealtime'

vi.mock('@/api/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/http')>()
  return { ...actual, refreshAccessSession: vi.fn(() => Promise.resolve()) }
})

vi.mock('@/api/learning', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/learning')>()
  return { ...actual, getLearningSession: vi.fn() }
})

class FakeWebSocket extends EventTarget {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  static readonly instances: FakeWebSocket[] = []

  readyState = FakeWebSocket.CONNECTING
  readonly sent: Array<Record<string, unknown>> = []

  constructor(readonly url: string) {
    super()
    FakeWebSocket.instances.push(this)
  }

  /** 模拟服务端完成握手。 */
  open() {
    this.readyState = FakeWebSocket.OPEN
    this.dispatchEvent(new Event('open'))
  }

  /** 记录客户端协议消息。 */
  send(data: string) {
    this.sent.push(JSON.parse(data) as Record<string, unknown>)
  }

  /** 模拟连接关闭。 */
  close(code = 1000, reason = '') {
    this.readyState = FakeWebSocket.CLOSED
    this.dispatchEvent(new CloseEvent('close', { code, reason }))
  }

  /** 模拟服务端下发协议消息。 */
  receive(message: Record<string, unknown>) {
    this.dispatchEvent(new MessageEvent('message', { data: JSON.stringify(message) }))
  }
}

let wrapper: VueWrapper | undefined

describe('useLearningRealtime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeWebSocket.instances.splice(0)
    vi.stubGlobal('WebSocket', FakeWebSocket)
    vi.mocked(refreshAccessSession).mockResolvedValue(undefined)
    vi.mocked(getLearningSession).mockResolvedValue(session('SIGNED_OUT', 1))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('绑定后严格按BIND、SYNC顺序就绪', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const socket = FakeWebSocket.instances[0]!

    socket.open()
    await respondToStateRequest(socket, 'BIND_SESSION', session('PAUSED', 2))
    await respondToStateRequest(socket, 'SYNC_STATE', session('PAUSED', 2))

    await expect(binding).resolves.toMatchObject({ status: 'PAUSED', lastSequence: 2 })
    expect(realtime.ready.value).toBe(true)
  })

  it('连接建立后启动心跳并在断开时停止', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const socket = FakeWebSocket.instances[0]!
    socket.open()
    await respondToStateRequest(socket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(socket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    expect(socket.sent.filter((message) => message.type === 'HEARTBEAT')).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(20_000)
    expect(socket.sent.filter((message) => message.type === 'HEARTBEAT')).toHaveLength(2)
    socket.close(1006, '网络中断')
    await vi.advanceTimersByTimeAsync(20_000)
    expect(socket.sent.filter((message) => message.type === 'HEARTBEAT')).toHaveLength(2)
  })

  it('同步到STUDYING时先确认安全暂停再就绪', async () => {
    const onProgress = vi.fn()
    const { realtime } = mountComposable({ onProgressConfirmed: onProgress })
    const binding = realtime.bind('900')
    const socket = FakeWebSocket.instances[0]!

    socket.open()
    await respondToStateRequest(socket, 'BIND_SESSION', session('STUDYING', 4))
    await respondToStateRequest(socket, 'SYNC_STATE', session('STUDYING', 4))
    const pause = lastSent(socket, 'PAUSE')

    expect(pause.seq).toBe(5)
    expect(realtime.ready.value).toBe(false)
    socket.receive(progressConfirmed(pause.requestId as string, 5, 'PAUSED'))
    await flushPromises()

    await expect(binding).resolves.toMatchObject({ status: 'PAUSED' })
    expect(realtime.ready.value).toBe(true)
    expect(onProgress).toHaveBeenCalledOnce()
  })

  it('抽验触发和结果只分发给当前绑定会话', async () => {
    const onRequired = vi.fn()
    const onResult = vi.fn()
    const { realtime } = mountComposable({
      onFaceCheckRequired: onRequired,
      onFaceCheckResult: onResult,
    })
    const binding = realtime.bind('900')
    const socket = FakeWebSocket.instances[0]!
    socket.open()
    await respondToStateRequest(socket, 'BIND_SESSION', session('PAUSED', 2))
    await respondToStateRequest(socket, 'SYNC_STATE', session('PAUSED', 2))
    await binding

    socket.receive(faceCheckMessage('FACE_CHECK_REQUIRED', 'PENDING'))
    socket.receive(faceCheckMessage('FACE_CHECK_RESULT', 'PASSED'))
    socket.receive({
      ...faceCheckMessage('FACE_CHECK_REQUIRED', 'PENDING'),
      requestId: 'other-session-event',
      payload: { ...faceCheck('PENDING'), sessionId: '901' },
    })

    expect(onRequired).toHaveBeenCalledOnce()
    expect(onRequired).toHaveBeenCalledWith(expect.objectContaining({ taskId: '700' }))
    expect(onResult).toHaveBeenCalledOnce()
    expect(realtime.latestSession.value).toMatchObject({
      status: 'FACE_PENDING',
      currentFaceCheck: { status: 'PASSED' },
    })
  })

  it('待确认PAUSE在重连后复用原requestId和seq', async () => {
    const onDisconnected = vi.fn()
    const { realtime } = mountComposable({ onDisconnected })
    const binding = realtime.bind('900')
    const firstSocket = FakeWebSocket.instances[0]!
    firstSocket.open()
    await respondToStateRequest(firstSocket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(firstSocket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('PAUSE', 1, '301', 10_000)
    const original = lastSent(firstSocket, 'PAUSE')
    firstSocket.close(1006, '网络中断')
    expect(onDisconnected).toHaveBeenCalledOnce()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1_000)

    const secondSocket = FakeWebSocket.instances[1]!
    secondSocket.open()
    await respondToStateRequest(secondSocket, 'BIND_SESSION', session('STUDYING', 0))
    await respondToStateRequest(secondSocket, 'SYNC_STATE', session('STUDYING', 0))
    const replay = lastSent(secondSocket, 'PAUSE')

    expect(replay.requestId).toBe(original.requestId)
    expect(replay.seq).toBe(original.seq)
    expect(realtime.ready.value).toBe(false)
    secondSocket.receive(progressConfirmed(replay.requestId as string, 1, 'PAUSED'))
    await expect(eventPromise).resolves.toMatchObject({ acceptedSequence: 1 })
    expect(realtime.ready.value).toBe(true)
  })

  it('PAUSE已处理但响应丢失时重放原请求并保留课件完成结果', async () => {
    const onProgress = vi.fn()
    const { realtime } = mountComposable({ onProgressConfirmed: onProgress })
    const binding = realtime.bind('900')
    const firstSocket = FakeWebSocket.instances[0]!
    firstSocket.open()
    await respondToStateRequest(firstSocket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(firstSocket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('PAUSE', 1, '301', 60_000)
    const original = lastSent(firstSocket, 'PAUSE')
    firstSocket.close(1006, '确认响应丢失')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1_000)

    const secondSocket = FakeWebSocket.instances[1]!
    secondSocket.open()
    await respondToStateRequest(secondSocket, 'BIND_SESSION', session('PAUSED', 1))
    await respondToStateRequest(secondSocket, 'SYNC_STATE', session('PAUSED', 1))
    const replay = lastSent(secondSocket, 'PAUSE')

    expect(replay.requestId).toBe(original.requestId)
    expect(replay.seq).toBe(original.seq)
    secondSocket.receive(
      progressConfirmed(replay.requestId as string, 1, 'PAUSED', {
        coursewareCompleted: true,
      }),
    )

    await expect(eventPromise).resolves.toMatchObject({ coursewareCompleted: true })
    expect(onProgress).toHaveBeenLastCalledWith(
      expect.objectContaining({ coursewareCompleted: true }),
    )
    expect(realtime.ready.value).toBe(true)
  })

  it('待确认事件ACK超时后重连并原样重发', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const firstSocket = FakeWebSocket.instances[0]!
    firstSocket.open()
    await respondToStateRequest(firstSocket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(firstSocket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('PAUSE', 1, '301', 10_000)
    const original = lastSent(firstSocket, 'PAUSE')
    await vi.advanceTimersByTimeAsync(10_000)
    await vi.advanceTimersByTimeAsync(1_000)

    const secondSocket = FakeWebSocket.instances[1]!
    secondSocket.open()
    await respondToStateRequest(secondSocket, 'BIND_SESSION', session('STUDYING', 0))
    await respondToStateRequest(secondSocket, 'SYNC_STATE', session('STUDYING', 0))
    const replay = lastSent(secondSocket, 'PAUSE')
    expect(replay.requestId).toBe(original.requestId)
    expect(replay.seq).toBe(original.seq)

    secondSocket.receive(progressConfirmed(replay.requestId as string, 1, 'PAUSED'))
    await expect(eventPromise).resolves.toMatchObject({ acceptedSequence: 1 })
  })

  it('收到ACK后重新计算最终确认超时', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const socket = FakeWebSocket.instances[0]!
    socket.open()
    await respondToStateRequest(socket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(socket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('PAUSE', 1, '301', 10_000)
    const pending = lastSent(socket, 'PAUSE')
    await vi.advanceTimersByTimeAsync(9_000)
    socket.receive({
      type: 'ACK',
      requestId: pending.requestId,
      studySessionId: '900',
      seq: 1,
      sentAt: new Date().toISOString(),
      payload: { acceptedSequence: 1, status: 'PAUSED' },
    })
    await vi.advanceTimersByTimeAsync(9_000)

    expect(FakeWebSocket.instances).toHaveLength(1)
    socket.receive(progressConfirmed(pending.requestId as string, 1, 'PAUSED'))
    await expect(eventPromise).resolves.toMatchObject({ acceptedSequence: 1 })
  })

  it('L3005同步后为仍需要的PAUSE创建最新事件', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const socket = FakeWebSocket.instances[0]!
    socket.open()
    await respondToStateRequest(socket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(socket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('PAUSE', 1, '301', 10_000)
    const original = lastSent(socket, 'PAUSE')
    socket.receive({
      type: 'ERROR',
      requestId: original.requestId,
      studySessionId: '900',
      seq: 1,
      sentAt: new Date().toISOString(),
      payload: {
        code: 'L3005',
        message: '学习事件序号无效',
        retryable: false,
        resyncRequired: true,
      },
    })
    await respondToStateRequest(socket, 'SYNC_STATE', session('STUDYING', 5))
    const recovered = lastSent(socket, 'PAUSE')

    expect(recovered.requestId).not.toBe(original.requestId)
    expect(recovered.seq).toBe(6)
    socket.receive(progressConfirmed(recovered.requestId as string, 6, 'PAUSED'))
    await expect(eventPromise).resolves.toMatchObject({ acceptedSequence: 6 })
  })

  it('签退响应丢失后通过REST只读状态确认终态', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const firstSocket = FakeWebSocket.instances[0]!
    firstSocket.open()
    await respondToStateRequest(firstSocket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(firstSocket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('SIGN_OUT', 1, '301', 10_000)
    firstSocket.close(1006, '签退响应丢失')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1_000)
    const secondSocket = FakeWebSocket.instances[1]!
    secondSocket.open()
    await flushPromises()
    const bindRequest = lastSent(secondSocket, 'BIND_SESSION')
    secondSocket.receive({
      type: 'ERROR',
      requestId: bindRequest.requestId,
      studySessionId: '900',
      sentAt: new Date().toISOString(),
      payload: {
        code: 'L3008',
        message: '学习会话已失效',
        retryable: false,
        resyncRequired: false,
      },
    })
    await flushPromises()

    await expect(eventPromise).resolves.toMatchObject({ status: 'SIGNED_OUT' })
    expect(getLearningSession).toHaveBeenCalledWith('900')
  })

  it('签退重绑失败且REST未确认签退时拒绝待确认事件', async () => {
    vi.mocked(getLearningSession).mockResolvedValueOnce(session('TERMINATED', 1))
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const firstSocket = FakeWebSocket.instances[0]!
    firstSocket.open()
    await respondToStateRequest(firstSocket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(firstSocket, 'SYNC_STATE', session('PAUSED', 0))
    await binding

    const eventPromise = realtime.sendEvent('SIGN_OUT', 1, '301', 10_000)
    const eventResult = expect(eventPromise).rejects.toMatchObject({ code: 'L3008' })
    firstSocket.close(1006, '签退响应丢失')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1_000)
    const secondSocket = FakeWebSocket.instances[1]!
    secondSocket.open()
    await flushPromises()
    const bindRequest = lastSent(secondSocket, 'BIND_SESSION')
    secondSocket.receive({
      type: 'ERROR',
      requestId: bindRequest.requestId,
      studySessionId: '900',
      sentAt: new Date().toISOString(),
      payload: {
        code: 'L3008',
        message: '学习会话已失效',
        retryable: false,
        resyncRequired: false,
      },
    })

    await eventResult
    expect(getLearningSession).toHaveBeenCalledWith('900')
  })

  it('4401关闭时先刷新HTTP会话再重连', async () => {
    const { realtime } = mountComposable()
    const binding = realtime.bind('900')
    const firstSocket = FakeWebSocket.instances[0]!
    firstSocket.open()
    firstSocket.close(4401, 'Token过期')
    await flushPromises()

    expect(refreshAccessSession).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(1_000)
    expect(FakeWebSocket.instances).toHaveLength(2)
    const secondSocket = FakeWebSocket.instances[1]!
    secondSocket.open()
    await respondToStateRequest(secondSocket, 'BIND_SESSION', session('PAUSED', 0))
    await respondToStateRequest(secondSocket, 'SYNC_STATE', session('PAUSED', 0))
    await expect(binding).resolves.toMatchObject({ status: 'PAUSED' })
  })

  it('4409关闭后通知接管且不再重连', async () => {
    const onReplaced = vi.fn()
    const { realtime } = mountComposable({ onReplaced })
    const binding = realtime.bind('900')
    const bindingResult = expect(binding).rejects.toMatchObject({ code: 'REALTIME_REPLACED' })
    const socket = FakeWebSocket.instances[0]!
    socket.open()
    socket.receive({
      type: 'SESSION_REPLACED',
      requestId: 'server-1',
      studySessionId: '900',
      sentAt: new Date().toISOString(),
      payload: { message: '已接管' },
    })
    socket.close(4409, '已接管')
    await vi.advanceTimersByTimeAsync(20_000)
    await bindingResult

    expect(onReplaced).toHaveBeenCalledOnce()
    expect(FakeWebSocket.instances).toHaveLength(1)
  })
})

/** 在Vue组件上下文中创建组合函数。 */
function mountComposable(options: Partial<Parameters<typeof useLearningRealtime>[0]> = {}) {
  let realtime!: ReturnType<typeof useLearningRealtime>
  wrapper = mount(
    defineComponent({
      /** 初始化被测组合函数。 */
      setup() {
        realtime = useLearningRealtime({ clientInstanceId: 'browser-one', ...options })
        return () => h('div')
      },
    }),
  )
  return { realtime }
}

/** 回复指定类型的BIND或SYNC状态请求。 */
async function respondToStateRequest(
  socket: FakeWebSocket,
  type: 'BIND_SESSION' | 'SYNC_STATE',
  value: LearningSession,
) {
  await flushPromises()
  const request = lastSent(socket, type)
  socket.receive({
    type: 'STATE_SYNC',
    requestId: request.requestId,
    studySessionId: value.id,
    seq: value.lastSequence,
    sentAt: new Date().toISOString(),
    payload: value,
  })
  await flushPromises()
}

/** 返回最近一条指定类型客户端消息。 */
function lastSent(socket: FakeWebSocket, type: string) {
  const message = [...socket.sent].reverse().find((value) => value.type === type)
  if (!message) throw new Error(`未找到${type}消息`)
  return message
}

/** 创建学习会话快照。 */
function session(status: LearningSession['status'], lastSequence: number): LearningSession {
  return {
    id: '900',
    taskId: '500',
    planId: '100',
    planCourseId: '101',
    courseName: '安全驾驶',
    status,
    currentCoursewareSnapshotId: '301',
    lastSequence,
    confirmedPositionMillis: 10_000,
    effectiveDurationMillis: 20_000,
    requiredDurationMillis: 60_000,
    lastEventAt: '2026-08-30T08:00:00.000Z',
    createdAt: '2026-08-30T07:00:00.000Z',
  }
}

/** 创建服务端事件确认消息。 */
function progressConfirmed(
  requestId: string,
  acceptedSequence: number,
  status: LearningSession['status'],
  overrides: Partial<LearningEventResult> = {},
) {
  const payload: LearningEventResult = {
    sessionId: '900',
    requestId,
    acceptedSequence,
    status,
    currentCoursewareSnapshotId: '301',
    confirmedPositionMillis: 10_000,
    creditedDurationMillis: 0,
    effectiveDurationMillis: 20_000,
    requiredDurationMillis: 60_000,
    coursewareCompleted: false,
    courseCompleted: false,
    serverTime: '2026-08-30T08:00:00.000Z',
    ...overrides,
  }
  return {
    type: 'PROGRESS_CONFIRMED',
    requestId,
    studySessionId: '900',
    seq: acceptedSequence,
    sentAt: new Date().toISOString(),
    payload,
  }
}

/** 构造指定状态的抽验任务载荷。 */
function faceCheck(status: 'PENDING' | 'PASSED') {
  return {
    taskId: '700',
    sessionId: '900',
    status,
    triggeredAt: '2026-08-30T08:00:00.000Z',
    deadlineAt: '2026-08-30T08:01:00.000Z',
    attemptCount: status === 'PENDING' ? 0 : 1,
    maxAttempts: 3,
    remainingAttempts: status === 'PENDING' ? 3 : 2,
    result: status === 'PASSED' ? 'MATCHED' : null,
    failureReason: null,
    similarity: status === 'PASSED' ? 0.91 : null,
    completedAt: status === 'PASSED' ? '2026-08-30T08:00:10.000Z' : null,
  }
}

/** 构造服务端抽验实时消息。 */
function faceCheckMessage(
  type: 'FACE_CHECK_REQUIRED' | 'FACE_CHECK_RESULT',
  status: 'PENDING' | 'PASSED',
) {
  return {
    type,
    requestId: `face-${type}`,
    studySessionId: '900',
    sentAt: '2026-08-30T08:00:00.000Z',
    payload: faceCheck(status),
  }
}
