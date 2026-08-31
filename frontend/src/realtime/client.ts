import {
  decodeMessage,
  encodeMessage,
  type RealtimeClientMessage,
  type RealtimeServerMessage,
} from './protocol'
import { reconnectDelay } from './reconnect'

export type RealtimeConnectionState =
  'idle' | 'connecting' | 'connected' | 'disconnected' | 'replaced'

export interface RealtimeClientOptions {
  url: string
  onMessage?: (message: RealtimeServerMessage) => void
  onOpen?: (reconnecting: boolean) => void
  onClose?: (event: CloseEvent) => void
  onStateChange?: (state: RealtimeConnectionState) => void
  beforeReconnect?: (event: CloseEvent) => Promise<boolean> | boolean
  createSocket?: (url: string) => WebSocket
}

/** 仅负责WebSocket连接、协议解码和重连退避。 */
export class RealtimeClient {
  private socket?: WebSocket
  private reconnectAttempt = 0
  private reconnectTimer?: ReturnType<typeof setTimeout>
  private manuallyClosed = false
  private manuallyClosedState: RealtimeConnectionState = 'idle'
  private everConnected = false

  constructor(private readonly options: RealtimeClientOptions) {}

  /** 建立连接；重复调用不会创建并行Socket。 */
  connect() {
    this.manuallyClosed = false
    this.manuallyClosedState = 'idle'
    this.reconnectAttempt = 0
    this.openSocket()
  }

  /** 发送一条已类型化的客户端消息。 */
  send(message: RealtimeClientMessage) {
    if (this.socket?.readyState !== WebSocket.OPEN) return false
    this.socket.send(encodeMessage(message))
    return true
  }

  /** 主动触发断开并按既定退避重新连接。 */
  reconnect() {
    if (this.manuallyClosed) return
    if (this.socket && this.socket.readyState < WebSocket.CLOSING) {
      this.socket.close(4000, '重新同步实时连接')
      return
    }
    void this.scheduleReconnect(this.syntheticCloseEvent())
  }

  /** 永久关闭连接并取消后续重连，可保留被接管终态。 */
  close(code = 1000, reason = '页面已关闭') {
    this.manuallyClosed = true
    this.manuallyClosedState = code === 4409 ? 'replaced' : 'idle'
    clearTimeout(this.reconnectTimer)
    this.socket?.close(code, reason)
    this.options.onStateChange?.(this.manuallyClosedState)
  }

  /** 创建Socket并绑定本轮连接事件。 */
  private openSocket() {
    if (this.socket && this.socket.readyState < WebSocket.CLOSING) return
    clearTimeout(this.reconnectTimer)
    this.options.onStateChange?.('connecting')
    const socket = this.options.createSocket?.(this.options.url) ?? new WebSocket(this.options.url)
    this.socket = socket

    socket.addEventListener('open', () => {
      if (socket !== this.socket) return
      const reconnecting = this.everConnected
      this.everConnected = true
      this.reconnectAttempt = 0
      this.options.onStateChange?.('connected')
      this.options.onOpen?.(reconnecting)
    })
    socket.addEventListener('message', (event) => {
      if (socket !== this.socket || typeof event.data !== 'string') return
      try {
        this.options.onMessage?.(decodeMessage(event.data))
      } catch {
        socket.close(1002, '服务端消息格式不正确')
      }
    })
    socket.addEventListener('close', (event) => {
      if (socket !== this.socket) return
      this.socket = undefined
      this.options.onClose?.(event)
      if (event.code === 4409) {
        this.manuallyClosed = true
        this.manuallyClosedState = 'replaced'
        this.options.onStateChange?.('replaced')
        return
      }
      if (this.manuallyClosed) {
        this.options.onStateChange?.(this.manuallyClosedState)
        return
      }
      this.options.onStateChange?.('disconnected')
      void this.scheduleReconnect(event)
    })
  }

  /** 执行重连前置动作并应用1、2、5、10秒退避。 */
  private async scheduleReconnect(event: CloseEvent) {
    clearTimeout(this.reconnectTimer)
    const allowed = (await this.options.beforeReconnect?.(event)) ?? true
    if (!allowed || this.manuallyClosed) return
    const delay = reconnectDelay(this.reconnectAttempt++)
    this.reconnectTimer = setTimeout(() => this.openSocket(), delay)
  }

  /** 构造无原生关闭事件时使用的重连上下文。 */
  private syntheticCloseEvent() {
    return new CloseEvent('close', { code: 4000, reason: '重新同步实时连接' })
  }
}
