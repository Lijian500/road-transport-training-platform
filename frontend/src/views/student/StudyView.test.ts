import { nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CourseProgress, LearningEventResult, LearningSession } from '@/api/learning'

import StudyView from './StudyView.vue'

const realtimeMock = vi.hoisted(() => ({
  options: undefined as
    | {
        onStateSync?: (session: LearningSession) => void
        onProgressConfirmed?: (result: LearningEventResult) => void
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
  getActiveLearningSession: vi.fn(),
  getLearningCourse: vi.fn(),
  getLearningPlaybackUrl: vi.fn(),
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
    if (pausedDescriptor) {
      Object.defineProperty(HTMLMediaElement.prototype, 'paused', pausedDescriptor)
    } else {
      delete (HTMLMediaElement.prototype as { paused?: boolean }).paused
    }
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
})

/** 使用必要的Element Plus桩挂载学习页。 */
function mountStudyView() {
  return mount(StudyView, {
    global: {
      directives: { loading: () => undefined },
      stubs: {
        ElAlert: { template: '<div><slot /></div>' },
        ElButton: { template: '<button><slot /></button>' },
        ElCard: { template: '<div><slot name="header" /><slot /></div>' },
        ElProgress: true,
        ElTag: { template: '<span><slot /></span>' },
      },
    },
  })
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
