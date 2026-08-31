import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { HeartbeatMessage } from './protocol'
import { RealtimeClient } from './client'

class FakeWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3

  readyState = FakeWebSocket.CONNECTING
  readonly sent: string[] = []
  deferClose = false
  private pendingClose?: CloseEvent
  private readonly listeners = new Map<string, Array<(event: Event) => void>>()

  /** 登记客户端监听器。 */
  addEventListener(type: string, listener: EventListener) {
    const handlers = this.listeners.get(type) ?? []
    handlers.push(listener)
    this.listeners.set(type, handlers)
  }

  /** 模拟服务端完成握手。 */
  open() {
    this.readyState = FakeWebSocket.OPEN
    this.dispatch('open', new Event('open'))
  }

  /** 记录客户端发送的文本。 */
  send(data: string) {
    this.sent.push(data)
  }

  /** 模拟本地或服务端关闭连接。 */
  close(code = 1000, reason = '') {
    this.readyState = FakeWebSocket.CLOSED
    this.pendingClose = new CloseEvent('close', { code, reason })
    if (!this.deferClose) this.flushClose()
  }

  /** 触发被延迟的浏览器关闭事件。 */
  flushClose(code?: number) {
    if (!this.pendingClose) return
    const event =
      code === undefined
        ? this.pendingClose
        : new CloseEvent('close', { code, reason: this.pendingClose.reason })
    this.pendingClose = undefined
    this.dispatch('close', event)
  }

  /** 同步触发指定类型监听器。 */
  private dispatch(type: string, event: Event) {
    this.listeners.get(type)?.forEach((listener) => listener(event))
  }
}

describe('RealtimeClient', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('断线后按固定退避创建新连接', async () => {
    const sockets: FakeWebSocket[] = []
    const client = new RealtimeClient({
      url: 'ws://localhost/ws/learning',
      createSocket: () => {
        const socket = new FakeWebSocket()
        sockets.push(socket)
        return socket as unknown as WebSocket
      },
    })

    client.connect()
    sockets[0]!.open()
    sockets[0]!.close(1006)
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(999)
    expect(sockets).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(sockets).toHaveLength(2)
  })

  it('连接被接管后停止重连', async () => {
    const sockets: FakeWebSocket[] = []
    const client = new RealtimeClient({
      url: 'ws://localhost/ws/learning',
      createSocket: () => {
        const socket = new FakeWebSocket()
        sockets.push(socket)
        return socket as unknown as WebSocket
      },
    })

    client.connect()
    sockets[0]!.open()
    sockets[0]!.close(4409)
    await vi.advanceTimersByTimeAsync(20_000)

    expect(sockets).toHaveLength(1)
  })

  it('主动以4409关闭时异步close事件不会覆盖被接管终态', () => {
    const states: string[] = []
    const socket = new FakeWebSocket()
    socket.deferClose = true
    const client = new RealtimeClient({
      url: 'ws://localhost/ws/learning',
      createSocket: () => socket as unknown as WebSocket,
      onStateChange: (state) => states.push(state),
    })

    client.connect()
    socket.open()
    client.close(4409, '连接已被接管')
    expect(states.at(-1)).toBe('replaced')

    socket.flushClose(1000)
    expect(states.at(-1)).toBe('replaced')
  })

  it('仅在OPEN状态发送类型化消息', () => {
    const socket = new FakeWebSocket()
    const client = new RealtimeClient({
      url: 'ws://localhost/ws/learning',
      createSocket: () => socket as unknown as WebSocket,
    })
    const heartbeat: HeartbeatMessage = {
      type: 'HEARTBEAT',
      requestId: 'heartbeat-1',
      sentAt: '2026-08-30T08:00:00.000Z',
      payload: {},
    }

    client.connect()
    expect(client.send(heartbeat)).toBe(false)
    socket.open()
    expect(client.send(heartbeat)).toBe(true)
    expect(socket.sent).toHaveLength(1)
  })
})
