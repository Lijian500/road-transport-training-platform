import { decodeMessage, encodeMessage, type RealtimeEnvelope } from './protocol'
import { reconnectDelay } from './reconnect'

export interface RealtimeClientOptions {
  url: string
  onMessage?: (message: RealtimeEnvelope) => void
  onStateChange?: (state: 'connecting' | 'connected' | 'disconnected') => void
}

export class RealtimeClient {
  private socket?: WebSocket
  private reconnectAttempt = 0
  private reconnectTimer?: ReturnType<typeof setTimeout>
  private manuallyClosed = false

  constructor(private readonly options: RealtimeClientOptions) {}

  connect() {
    this.manuallyClosed = false
    this.options.onStateChange?.('connecting')
    this.socket = new WebSocket(this.options.url)

    this.socket.addEventListener('open', () => {
      this.reconnectAttempt = 0
      this.options.onStateChange?.('connected')
    })
    this.socket.addEventListener('message', (event) => {
      if (typeof event.data === 'string') {
        this.options.onMessage?.(decodeMessage(event.data))
      }
    })
    this.socket.addEventListener('close', () => {
      this.options.onStateChange?.('disconnected')
      if (!this.manuallyClosed) {
        this.scheduleReconnect()
      }
    })
  }

  send<T>(message: RealtimeEnvelope<T>) {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      return false
    }
    this.socket.send(encodeMessage(message))
    return true
  }

  close() {
    this.manuallyClosed = true
    clearTimeout(this.reconnectTimer)
    this.socket?.close()
  }

  private scheduleReconnect() {
    clearTimeout(this.reconnectTimer)
    const delay = reconnectDelay(this.reconnectAttempt++)
    this.reconnectTimer = setTimeout(() => this.connect(), delay)
  }
}
