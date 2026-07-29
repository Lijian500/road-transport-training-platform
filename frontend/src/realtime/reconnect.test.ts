import { describe, expect, it } from 'vitest'

import { reconnectDelay } from './reconnect'

describe('reconnectDelay', () => {
  it('按约定返回递增退避时间并限制最大值', () => {
    expect(reconnectDelay(0)).toBe(1_000)
    expect(reconnectDelay(2)).toBe(5_000)
    expect(reconnectDelay(20)).toBe(10_000)
  })
})
