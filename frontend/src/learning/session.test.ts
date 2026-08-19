import type { CoursewareProgress } from '@/api/learning'
import { ApiError } from '@/api/http'

import {
  getOrCreateClientInstanceId,
  isCoursewareUnlocked,
  resolveCoursewareStatusAfterEvent,
  shouldRetryLearningEvent,
} from './session'

/** 创建课件进度测试数据。 */
function courseware(sortOrder: number, status: CoursewareProgress['status']) {
  return {
    coursewareSnapshotId: String(sortOrder),
    title: `课件${sortOrder}`,
    sortOrder,
    durationMillis: 60_000,
    confirmedPositionMillis: 0,
    maxConfirmedPositionMillis: 0,
    status,
  } satisfies CoursewareProgress
}

describe('学习会话前端规则', () => {
  beforeEach(() => localStorage.clear())

  it('浏览器实例ID生成后保持稳定', () => {
    const first = getOrCreateClientInstanceId()
    const second = getOrCreateClientInstanceId()

    expect(first).toBeTruthy()
    expect(second).toBe(first)
  })

  it('只有前置课件全部完成才解锁后续课件', () => {
    const first = courseware(1, 'COMPLETED')
    const second = courseware(2, 'IN_PROGRESS')
    const third = courseware(3, 'NOT_STARTED')

    expect(isCoursewareUnlocked([first, second, third], second)).toBe(true)
    expect(isCoursewareUnlocked([first, second, third], third)).toBe(false)
    second.status = 'COMPLETED'
    expect(isCoursewareUnlocked([first, second, third], third)).toBe(true)
  })

  it('回看已完成课件时保持完成状态', () => {
    expect(resolveCoursewareStatusAfterEvent('COMPLETED', false, 'STUDYING')).toBe('COMPLETED')
    expect(resolveCoursewareStatusAfterEvent('NOT_STARTED', false, 'STUDYING')).toBe('IN_PROGRESS')
    expect(resolveCoursewareStatusAfterEvent('IN_PROGRESS', true, 'PAUSED')).toBe('COMPLETED')
  })

  it('只重试网络错误和服务端错误', () => {
    expect(shouldRetryLearningEvent(new ApiError('网络错误'))).toBe(true)
    expect(shouldRetryLearningEvent(new ApiError('服务异常', 'S9999', 500))).toBe(true)
    expect(shouldRetryLearningEvent(new ApiError('状态冲突', 'L3005', 409))).toBe(false)
  })
})
