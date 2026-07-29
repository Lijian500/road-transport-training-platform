import type { RealtimeEnvelope } from './protocol'

export type RealtimeMessageHandler = (message: RealtimeEnvelope) => void

export class RealtimeMessageHandlers {
  private readonly handlers = new Map<string, Set<RealtimeMessageHandler>>()

  register(type: string, handler: RealtimeMessageHandler) {
    const typeHandlers = this.handlers.get(type) ?? new Set()
    typeHandlers.add(handler)
    this.handlers.set(type, typeHandlers)
    return () => typeHandlers.delete(handler)
  }

  dispatch(message: RealtimeEnvelope) {
    this.handlers.get(message.type)?.forEach((handler) => handler(message))
  }
}
