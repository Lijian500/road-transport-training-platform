import { nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type {
  CourseProgress,
  FaceCheckTask,
  LearningEventResult,
  LearningSession,
} from '@/api/learning'

import StudyView from './StudyView.vue'

const realtimeMock = vi.hoisted(() => ({
  options: undefined as
    | {
        onStateSync?: (session: LearningSession) => void
        onProgressConfirmed?: (result: LearningEventResult) => void
        onFaceCheckRequired?: (faceCheck: FaceCheckTask) => void
        onFaceCheckResult?: (faceCheck: FaceCheckTask) => void
        onDisconnected?: () => void
        onReplaced?: () => void
      }
    | undefined,
  ready: { __v_isRef: true, value: true },
  connectionState: { __v_isRef: true, value: 'connected' },
  bind: vi.fn(),
  sendEvent: vi.fn(),
  close: vi.fn(),
}))

const apiMock = vi.hoisted(() => ({
  attendanceFaceRequired: vi.fn(),
  verifyAttendanceFace: vi.fn(),
  getActiveLearningSession: vi.fn(),
  getLearningCourse: vi.fn(),
  getLearningPlaybackUrl: vi.fn(),
  getLearningSession: vi.fn(),
  openLearningSession: vi.fn(),
  terminateLearningSession: vi.fn(),
}))

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('@/composables/useLearningRealtime', () => ({
  LearningRealtimeError: class LearningRealtimeError extends Error {},
  useLearningRealtime: (options: typeof realtimeMock.options) => {
    realtimeMock.options = options
    return realtimeMock
  },
}))

vi.mock('@/api/learning', () => apiMock)

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { planId: '100', planCourseId: '101' } }),
  useRouter: () => routerMock,
}))

let wrapper: VueWrapper | undefined
let pausedDescriptor: PropertyDescriptor | undefined

describe('StudyView实时连接保护', () => {
  beforeEach(() => {
    realtimeMock.options = undefined
    realtimeMock.ready.value = true
    realtimeMock.connectionState.value = 'connected'
    vi.clearAllMocks()
    realtimeMock.sendEvent.mockReset()
    apiMock.getLearningCourse.mockResolvedValue(course())
    apiMock.openLearningSession.mockResolvedValue(session('PAUSED'))
    realtimeMock.bind.mockResolvedValue(session('PAUSED'))
    apiMock.getLearningPlaybackUrl.mockResolvedValue({
      url: 'https://example.com/video.mp4',
      method: 'GET',
      headers: {},
      expiresAt: '2026-08-30T09:00:00.000Z',
    })
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined)
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined)
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    pausedDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'paused')
    Object.defineProperty(HTMLMediaElement.prototype, 'paused', {
      configurable: true,
      get: () => false,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
    vi.useRealTimers()
    if (pausedDescriptor) {
      Object.defineProperty(HTMLMediaElement.prototype, 'paused', pausedDescriptor)
    } else {
      delete (HTMLMediaElement.prototype as { paused?: boolean }).paused
    }
  })

  it('当前视频结束并经服务端确认完成后自动播放下一视频', async () => {
    vi.useFakeTimers()
    const value = course()
    value.coursewares.push({ ...value.coursewares[0]!, coursewareSnapshotId: '302', title: '第二课', sortOrder: 2, confirmedPositionMillis: 0, status: 'NOT_STARTED' })
    apiMock.getLearningCourse.mockResolvedValue(value)
    realtimeMock.sendEvent.mockImplementation(async () => {
      const result = { ...eventResult('PAUSED'), confirmedPositionMillis: 60000, coursewareCompleted: true }
      realtimeMock.options?.onProgressConfirmed?.(result)
      return result
    })
    wrapper = mountStudyView()
    await flushPromises()
    await wrapper.get('video').trigger('ended')
    await flushPromises()
    expect(apiMock.getLearningPlaybackUrl).toHaveBeenLastCalledWith('900', '302', expect.any(String))
    await wrapper.get('video').trigger('loadedmetadata')
    await vi.advanceTimersByTimeAsync(0)
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalled()
  })

  it('要求人脸验证的签到先拍照，通过后才发送签到事件', async () => {
    apiMock.openLearningSession.mockResolvedValue(session('CREATED'))
    realtimeMock.bind.mockResolvedValue(session('CREATED'))
    apiMock.attendanceFaceRequired.mockResolvedValue(true)
    apiMock.verifyAttendanceFace.mockResolvedValue(undefined)
    realtimeMock.sendEvent.mockResolvedValue(eventResult('SIGNED_IN'))
    wrapper = mountStudyView()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '学习签到')!.trigger('click')
    await flushPromises()
    expect(realtimeMock.sendEvent).not.toHaveBeenCalled()
    wrapper.findComponent({ name: 'CameraCapture' }).vm.$emit('capture', new File(['photo'], 'face.jpg', { type: 'image/jpeg' }))
    await flushPromises()
    expect(apiMock.verifyAttendanceFace).toHaveBeenCalledWith('900', 'SIGN_IN', expect.any(String), expect.any(File))
    expect(realtimeMock.sendEvent).toHaveBeenCalled()
  })

  it('人脸验证失败不会发送签到事件', async () => {
    apiMock.openLearningSession.mockResolvedValue(session('CREATED'))
    realtimeMock.bind.mockResolvedValue(session('CREATED'))
    apiMock.attendanceFaceRequired.mockResolvedValue(true)
    apiMock.verifyAttendanceFace.mockRejectedValue(new Error('人脸不匹配'))
    wrapper = mountStudyView()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '学习签到')!.trigger('click')
    await flushPromises()
    wrapper.findComponent({ name: 'CameraCapture' }).vm.$emit('capture', new File(['photo'], 'face.jpg'))
    await flushPromises()
    expect(realtimeMock.sendEvent).not.toHaveBeenCalled()
    expect(wrapper.find('.attendance-camera').exists()).toBe(true)
  })

  it('切换期间不挂载空地址播放器，加载新课件后恢复其确认位置', async () => {
    const value = course()
    value.coursewares[0]!.status = 'COMPLETED'
    value.coursewares.push({
      ...value.coursewares[0]!,
      coursewareSnapshotId: '302',
      title: '第二课',
      sortOrder: 2,
      confirmedPositionMillis: 5000,
      status: 'IN_PROGRESS',
    })
    apiMock.getLearningCourse.mockResolvedValue(value)
    wrapper = mountStudyView()
    await flushPromises()
    let resolveUrl!: (value: { url: string }) => void
    apiMock.getLearningPlaybackUrl.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveUrl = resolve
        }),
    )
    await wrapper.findAll('.courseware-item')[1]!.trigger('click')
    expect(wrapper.find('video').exists()).toBe(false)
    resolveUrl({ url: 'https://example.com/second.mp4' })
    await flushPromises()
    const player = wrapper.get('video')
    expect(player.attributes('src')).toBe('https://example.com/second.mp4')
    await player.trigger('loadedmetadata')
    expect((player.element as HTMLVideoElement).currentTime).toBe(5)
    await player.trigger('seeking')
    expect((player.element as HTMLVideoElement).currentTime).toBe(5)
  })

  it('超时结果确认后点击知道了返回对应计划', async () => {
    wrapper = mountStudyView()
    await flushPromises()
    apiMock.getLearningSession.mockResolvedValue(session('TERMINATED'))
    realtimeMock.options?.onFaceCheckResult?.({ ...faceCheck(), status: 'TIMED_OUT' })
    await flushPromises()
    wrapper.findComponent({ name: 'FaceCheckDialog' }).vm.$emit('acknowledged')
    await flushPromises()
    expect(routerMock.replace).toHaveBeenCalledWith('/student/plans/100')
  })

  it('服务端超时扫描稍晚时继续同步，不让弹窗停留在零秒', async () => {
    vi.useFakeTimers()
    wrapper = mountStudyView()
    await flushPromises()
    const pending = { ...faceCheck(), deadlineAt: new Date(Date.now() - 1000).toISOString() }
    realtimeMock.options?.onFaceCheckRequired?.(pending)
    apiMock.getLearningSession
      .mockResolvedValueOnce({ ...session('FACE_PENDING'), currentFaceCheck: pending })
      .mockResolvedValueOnce({ ...session('TERMINATED'), currentFaceCheck: { ...pending, status: 'TIMED_OUT' } })
    wrapper.findComponent({ name: 'FaceCheckDialog' }).vm.$emit('expired')
    await flushPromises()
    expect(wrapper.find('.face-check-dialog').text()).toContain('PENDING')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('.face-check-dialog').text()).toContain('TIMED_OUT')
  })

  it('抽验失败通知丢失后按服务端终态恢复，不因已过截止时间误报超时', async () => {
    wrapper = mountStudyView()
    await flushPromises()
    const pending = { ...faceCheck(), deadlineAt: new Date(Date.now() - 1000).toISOString() }
    realtimeMock.options?.onFaceCheckRequired?.(pending)
    apiMock.getLearningSession.mockResolvedValue({
      ...session('TERMINATED'), currentFaceCheck: { ...pending, status: 'FAILED' },
    })
    wrapper.findComponent({ name: 'FaceCheckDialog' }).vm.$emit('expired')
    await flushPromises()
    expect(wrapper.find('.face-check-dialog').text()).toBe('FAILED')
    wrapper.findComponent({ name: 'FaceCheckDialog' }).vm.$emit('acknowledged')
    expect(routerMock.replace).not.toHaveBeenCalled()
  })

  it('连接断开时立即暂停播放器和计时入口', async () => {
    wrapper = mountStudyView()
    await flushPromises()
    await nextTick()

    realtimeMock.options?.onProgressConfirmed?.(eventResult('STUDYING'))
    realtimeMock.options?.onDisconnected?.()

    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled()
  })

  it('状态同步到STUDYING也不会自动播放', async () => {
    wrapper = mountStudyView()
    await flushPromises()
    await nextTick()

    realtimeMock.options?.onStateSync?.(session('STUDYING'))

    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled()
  })

  it('抽验触发后立即暂停播放器并展示可恢复任务', async () => {
    wrapper = mountStudyView()
    await flushPromises()
    await nextTick()

    realtimeMock.options?.onFaceCheckRequired?.(faceCheck())
    await nextTick()

    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled()
    expect(wrapper.find('.face-check-dialog').text()).toContain('PENDING')
    expect(wrapper.text()).toContain('等待人脸抽验')
  })
})

/** 使用必要的Element Plus桩挂载学习页。 */
function mountStudyView() {
  return mount(StudyView, {
    global: {
      directives: { loading: () => undefined },
      stubs: {
        ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
        CameraCapture: { name: 'CameraCapture', template: '<div class="attendance-camera" />' },
        ElAlert: { template: '<div><slot /></div>' },
        ElButton: { template: '<button><slot /></button>' },
        ElCard: { template: '<div><slot name="header" /><slot /></div>' },
        FaceCheckDialog: {
          name: 'FaceCheckDialog',
          props: ['faceCheck'],
          template: '<div class="face-check-dialog">{{ faceCheck?.status }}</div>',
        },
        ElProgress: true,
        ElTag: { template: '<span><slot /></span>' },
      },
    },
  })
}

/** 创建学习页使用的待处理抽验任务。 */
function faceCheck(): FaceCheckTask {
  return {
    taskId: '700',
    sessionId: '900',
    status: 'PENDING',
    triggeredAt: '2026-08-30T08:00:00.000Z',
    deadlineAt: '2099-08-30T08:01:00.000Z',
    attemptCount: 0,
    maxAttempts: 3,
    remainingAttempts: 3,
    result: null,
    failureReason: null,
    similarity: null,
    completedAt: null,
  }
}

/** 创建学习页课程数据。 */
function course(): CourseProgress {
  return {
    planCourseId: '101',
    courseName: '安全驾驶',
    sortOrder: 1,
    requiredDurationMillis: 60_000,
    effectiveDurationMillis: 10_000,
    allowSeek: false,
    progressReportIntervalSeconds: 20,
    studyToleranceSeconds: 5,
    status: 'IN_PROGRESS',
    coursewares: [
      {
        coursewareSnapshotId: '301',
        title: '第一课',
        sortOrder: 1,
        durationMillis: 60_000,
        confirmedPositionMillis: 10_000,
        maxConfirmedPositionMillis: 10_000,
        status: 'IN_PROGRESS',
      },
    ],
  }
}

/** 创建学习会话数据。 */
function session(status: LearningSession['status']): LearningSession {
  return {
    id: '900',
    taskId: '500',
    planId: '100',
    planCourseId: '101',
    courseName: '安全驾驶',
    status,
    currentCoursewareSnapshotId: '301',
    lastSequence: status === 'STUDYING' ? 2 : 1,
    confirmedPositionMillis: 10_000,
    effectiveDurationMillis: 10_000,
    requiredDurationMillis: 60_000,
    lastEventAt: '2026-08-30T08:00:00.000Z',
    createdAt: '2026-08-30T07:00:00.000Z',
  }
}

/** 创建学习事件确认数据。 */
function eventResult(status: LearningSession['status']): LearningEventResult {
  return {
    sessionId: '900',
    requestId: 'event-1',
    acceptedSequence: 2,
    status,
    currentCoursewareSnapshotId: '301',
    confirmedPositionMillis: 10_000,
    creditedDurationMillis: 0,
    effectiveDurationMillis: 10_000,
    requiredDurationMillis: 60_000,
    coursewareCompleted: false,
    courseCompleted: false,
    serverTime: '2026-08-30T08:00:00.000Z',
  }
}
