import { describe, expect, it } from 'vitest'

import { decodeMessage, encodeMessage, type LearningEventMessage } from './protocol'

describe('实时学习协议', () => {
  it('按统一信封编码学习事件', () => {
    const message: LearningEventMessage = {
      type: 'PROGRESS',
      requestId: 'request-1',
      studySessionId: '9001',
      seq: 3,
      sentAt: '2026-08-30T08:00:00.000Z',
      payload: { coursewareSnapshotId: '8001', videoPositionMillis: 12_000 },
    }

    expect(JSON.parse(encodeMessage(message))).toEqual(message)
  })

  it('拒绝未知服务端消息类型', () => {
    expect(() =>
      decodeMessage(
        JSON.stringify({
          type: 'UNKNOWN',
          requestId: 'request-1',
          sentAt: '2026-08-30T08:00:00.000Z',
          payload: {},
        }),
      ),
    ).toThrow('服务端实时消息格式不正确')
  })

  it('拒绝缺少最终学时字段的确认消息', () => {
    expect(() =>
      decodeMessage(
        JSON.stringify({
          type: 'PROGRESS_CONFIRMED',
          requestId: 'request-1',
          studySessionId: '9001',
          seq: 3,
          sentAt: '2026-08-30T08:00:00.000Z',
          payload: {
            sessionId: '9001',
            requestId: 'request-1',
            acceptedSequence: 3,
            status: 'STUDYING',
          },
        }),
      ),
    ).toThrow('服务端实时消息格式不正确')
  })

  it('解析携带当前抽验任务的状态同步消息', () => {
    const currentFaceCheck = faceCheck('PENDING')
    const message = decodeMessage(
      JSON.stringify({
        type: 'STATE_SYNC',
        requestId: 'request-2',
        studySessionId: '9001',
        sentAt: '2026-08-30T08:00:00.000Z',
        payload: {
          id: '9001',
          taskId: '5001',
          planId: '1001',
          planCourseId: '2001',
          courseName: '安全驾驶',
          status: 'FACE_PENDING',
          currentCoursewareSnapshotId: '8001',
          lastSequence: 3,
          confirmedPositionMillis: 12_000,
          effectiveDurationMillis: 20_000,
          requiredDurationMillis: 60_000,
          currentFaceCheck,
          lastEventAt: '2026-08-30T08:00:00.000Z',
          createdAt: '2026-08-30T07:00:00.000Z',
        },
      }),
    )

    expect(message.type).toBe('STATE_SYNC')
    expect(message.payload).toMatchObject({ status: 'FACE_PENDING', currentFaceCheck })
  })

  it('解析抽验触发和结果消息的统一任务载荷', () => {
    for (const type of ['FACE_CHECK_REQUIRED', 'FACE_CHECK_RESULT']) {
      const message = decodeMessage(
        JSON.stringify({
          type,
          requestId: `request-${type}`,
          studySessionId: '9001',
          sentAt: '2026-08-30T08:00:00.000Z',
          payload: faceCheck(type === 'FACE_CHECK_REQUIRED' ? 'PENDING' : 'PASSED'),
        }),
      )
      expect(message.type).toBe(type)
    }
  })

  it('接受SFace合法的负余弦相似度并拒绝越界值', () => {
    const message = faceCheckMessage({ ...faceCheck('PASSED'), similarity: -0.25 })

    expect(decodeMessage(JSON.stringify(message)).payload).toMatchObject({ similarity: -0.25 })
    expect(() =>
      decodeMessage(
        JSON.stringify(faceCheckMessage({ ...faceCheck('PASSED'), similarity: -1.01 })),
      ),
    ).toThrow('服务端实时消息格式不正确')
  })
})

/** 构造抽验结果实时信封。 */
function faceCheckMessage(payload: ReturnType<typeof faceCheck>) {
  return {
    type: 'FACE_CHECK_RESULT',
    requestId: 'request-face-result',
    studySessionId: '9001',
    sentAt: '2026-08-30T08:00:00.000Z',
    payload,
  }
}

/** 构造实时协议使用的抽验任务载荷。 */
function faceCheck(status: 'PENDING' | 'PASSED') {
  return {
    taskId: '7001',
    sessionId: '9001',
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
