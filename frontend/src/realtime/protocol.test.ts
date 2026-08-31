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
})
